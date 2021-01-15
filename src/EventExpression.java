/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.apama.e2a;

import java.util.TreeMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.SortedMap;

/**
 * Class for constructing EPL Event expressions.
 */
public class EventExpression {

	/** The coassignee of the event when the listener is activated. */
	private String coassignee = null;
	/** The name of the event type */
	private final String type;
	/** key = field name; value = constraint on field */
	private SortedMap<String, List<Constraint>> eventFieldConstraints = new TreeMap<String, List<Constraint>>();

	public EventExpression(String type, String coassignee) {
		this.type = type;
		this.coassignee = coassignee;
	}

	/** Constructor for an event expression with no coassignee 
	 * (i.e. with no "... as x"). */
	public EventExpression(String type) {
		this.type = type;
	}

	/** Returns true if the event expression has no constraints on its fields */
	public boolean hasNoFieldConstraints() {
		return eventFieldConstraints.isEmpty();
	}

	/** Removes any field constraints on the current event expression */
	public void clearEventFieldConstraints() {
		this.eventFieldConstraints.clear();
	}

	/** Converts an EventExpression object to an EPLOutput object */
	public EPLOutput toEPLOutput() {
		EPLOutput eplOut = new EPLOutput(type).add("(");
		for (String eventField : eventFieldConstraints.keySet()) {
			eplOut.add(eventField);
			if (eventFieldConstraints.get(eventField).size() == 1) {
				Constraint c = eventFieldConstraints.get(eventField).get(0);
				eplOut.add(" ").add(c.relationalComparison.operatorEPL).add(" ").add(c.comparator);
			} else { 
				// Field contains a range expression
				eplOut.add(" in ");
				Constraint lowerBound = eventFieldConstraints.get(eventField).get(0);
				if(lowerBound.relationalComparison.equals(RelationalComparison.GREATER_THAN)){
					eplOut.add("(");
				} else /* GREATER_OR_EQUAL */ { 
					eplOut.add("[");
				}
				Constraint upperBound = eventFieldConstraints.get(eventField).get(1);
				eplOut.add(lowerBound.comparator).add(" : ").add(upperBound.comparator);
				if(upperBound.relationalComparison.equals(RelationalComparison.LESS_THAN)){
					eplOut.add(")");
				} else /* LESS_OR_EQUAL */ {
					eplOut.add("]");
				}
			}
			if (!eventField.equals(eventFieldConstraints.lastKey())) {
				eplOut.add(", ");
			}
		}
		eplOut.add(")");
		if(coassignee != null){
			eplOut.add(" as ").add(coassignee);
		}
		return eplOut;
	}

	/**
	 * Checks to see if a given expr is in a supported format to be assimilated to
	 * an Event Expression.
	 * 
	 * If it is in a supported format (i.e. a simple comparison of an event field
	 * with a literal) then that constraint is added to the event expression and
	 * true is returned.
	 * 
	 * If the constraint cannot be added to the event expression as it is not in a
	 * supported format, then false is returned.
	 */
	public boolean addConstraint(final EsperParser.ExprContext exprCtx, final Scope scope) {
		if(!isSimpleComparison(exprCtx)){
			return false;
		}
		Scope scopeCopy = scope.variablesCopy();
		// Check if comparison operator is supported in event expression
		String eventFieldConstraint = new TranslateExpr(scopeCopy).visit(exprCtx).formatOutput();
		List<String> split = Arrays.asList(eventFieldConstraint.split("\\s+"));
		RelationalComparison comparison = RelationalComparison.fromString(split.get(1)); 
		if (RelationalComparison.UNSUPPORTED.equals(comparison)) {
			return false;
		}
		// Check if we are comparing an event field with a literal
		boolean eventFieldOnLHS = isMemberLookupAndLiteral(exprCtx.expr(0), exprCtx.expr(1), scopeCopy);
		boolean eventFieldOnRHS = isMemberLookupAndLiteral(exprCtx.expr(1), exprCtx.expr(0), scopeCopy);
		if (!(eventFieldOnLHS || eventFieldOnRHS)) {
			return false;
		}
		String fieldName;
		String secondOperand;
		int indexOfComparison = eventFieldConstraint.indexOf(comparison.operatorEPL);
		int indexOfFieldName = eventFieldConstraint.indexOf(coassignee + ".") + coassignee.length() + 1;
		if (eventFieldOnLHS) {
			fieldName = eventFieldConstraint.substring(indexOfFieldName, indexOfComparison).trim();
			secondOperand = eventFieldConstraint.substring(indexOfComparison + comparison.operatorEPL.length()).trim();
		} else /* eventFieldOnRHS */ {
			comparison = comparison.invert();
			fieldName = eventFieldConstraint.substring(indexOfFieldName).trim();
			secondOperand = eventFieldConstraint.substring(0, indexOfComparison).trim();
		}
		// Check if we are adding a constraint to a field that already has a constraint
		// on it e.g. if specifying a range
		if (eventFieldConstraints.containsKey(fieldName)) {
			if (eventFieldConstraints.get(fieldName).size() >= 2) {
				return false; // We cannot have more than 2 conditions on a single event field
			} 
			return addSecondConstraint(fieldName, comparison, secondOperand, false);
		} else {
			addFirstConstraintToField(fieldName, comparison, secondOperand);
		}
		return true;
	}

	/** Returns true if side1 can be reduced to a member lookup on the event and
	 * side2 is a literal. */
	private boolean isMemberLookupAndLiteral(final EsperParser.ExprContext side1, final EsperParser.ExprContext side2, final Scope scopeCopy){
		String side1Translation = new TranslateExpr(scopeCopy).visit(side1).formatOutput().trim();
		boolean side1HasEventField = side1Translation.startsWith(coassignee + ".")
			&& !side1Translation.contains("[") 
			&& (side1Translation.length() - side1Translation.replace(".", "").length() == 1); 
		return side1HasEventField && side2.literal() != null;
	}

	/**
	 * Adds a constraint on a field on the event expression. If the constraint
	 * cannot be added to the field (e.g. because of pre-existing conditions on the
	 * field and overwrite param is false), then a runtime exception is thrown.
	 * 
	 * @param fieldName  The name of the field that has a constraint
	 * @param comparison The relational comparison operator. This can be one of
	 *                   "<=", "<", "=", ">=", or ">". If it is not one of these
	 *                   values, a RunTimeException is thrown.
	 * @param comparator The boundary of the constraint on the field
	 * @param overwrite  Specifies whether the new constraint on the event field
	 *                   should overwrite existing overlapping constraints (e.g. if
	 *                   "ev.x<10" is already a constraint, and we wish to add
	 *                   another such as "ev.x<=11").
	 * @see RelationalComparison
	 */
	public void addConstraint(final String fieldName, final String comparison, final String comparator, final boolean overwrite){
		RelationalComparison rc = RelationalComparison.fromString(comparison);
		final String errorPrefix = "EventExpression.addCondition(). ";
		if (RelationalComparison.UNSUPPORTED.equals(rc)) {
			throw new RuntimeException(errorPrefix + "Comparison operator must be one of \"<=\", \"<\", \"=\", \">=\", or \">\". Got \""+comparison+"\".");
		}
		if (eventFieldConstraints.containsKey(fieldName)) {
			boolean constraintSuccessfullyAdded = addSecondConstraint(fieldName, rc, comparator, overwrite);
			if(!overwrite && !constraintSuccessfullyAdded){
				throw new RuntimeException(errorPrefix + "Cannot add constraint " 
					+ fieldName + rc.toString() + comparator + " to " + type);
			}
		} else {
			addFirstConstraintToField(fieldName, rc, comparator);
		}
	}

	/** 
	 * Executes addConstraint with overwrite=true.
	 * @see addConstraint(final String fieldName, final String comparison, final String comparator, boolean overwrite) 
	 * */
	public void addConstraint(final String fieldName, final String comparison, final String comparator){
		addConstraint(fieldName, comparison, comparator, true);
	}

	/** Adds a constraint to a given field, when there are no pre-existing constraints on that field. */
	private void addFirstConstraintToField(final String fieldName, final RelationalComparison rc, final String comparator){
		List<Constraint> constraints = new ArrayList<Constraint>(1);
		constraints.add(new Constraint(rc, comparator));
		eventFieldConstraints.put(fieldName, constraints);
	}

	/**
	 * Adds a second constraint to a field on an event expression. Returns true if
	 * the constraint was successfully added (can return false if the overwrite
	 * option is false). 
	 
	 * TODO - if required later, we could add a 'replace' option that checks to see 
	 * if there is already an overlapping constraint on the field, and selects the 
	 * constraint that is more restrictive when replace=true. E.g. If we have exisiting 
	 * constraint, "field > 0", and try to add new condition "field > 5", then we can
	 * replace the first condition with the second (as the first becomes redundant). 
	 * 
	 * @param fieldName  the name of the event field.
	 * @param rc         the relational comparison in the condition.
	 * @param comparator a literal that defines the boundary of the condition.
	 * @param overwrite  specifies whether the new constraint on the event field
	 *                   should overwrite existing overlapping constraints (e.g. if
	 *                   "ev.x<10" is already a constraint, and we wish to add
	 *                   another such as "ev.x<=11").
	 * @return true if the constraint was successfully added to the event
	 *         expression, false if the condition could not be added to the event
	 *         expression.
	 */
	private boolean addSecondConstraint(final String fieldName, final RelationalComparison rc, final String comparator,
	final boolean overwrite){
		List<Constraint> constraintsOnField = eventFieldConstraints.get(fieldName);
		RelationalComparison prevRC = constraintsOnField.get(0).relationalComparison;
		switch(rc) {
			case EQUAL:
				if(overwrite){
					eventFieldConstraints.replace(fieldName, new ArrayList<Constraint>(1));
					eventFieldConstraints.get(fieldName).add(new Constraint(rc, comparator));
					return true;
				}
				return false; // this is not the best optimization, but as it (currently)
				// only covers specific cases when the input Esper is dodgy (but valid),
				// e.g. "where ev.field>10 and ev.field=13" or "where ev.field<7 and ev.field=10",
				// we do not want to over-complicate solution.
			case GREATER_THAN:
			case GREATER_OR_EQUAL:
				switch(prevRC) {
					case LESS_THAN:
					case LESS_OR_EQUAL:
						// Add to start so the lower bound always appears first in list of constraints - see toEPLOutput() method
						constraintsOnField.add(0, new Constraint(rc, comparator));
						return true;
					case GREATER_THAN:
					case GREATER_OR_EQUAL:
					case EQUAL:
					default:
						if (overwrite) {
							constraintsOnField.set(0, new Constraint(rc, comparator));
							return true;
						}
						return false;
				}
			case LESS_THAN:
			case LESS_OR_EQUAL:
				switch(prevRC) {
					case GREATER_THAN:
					case GREATER_OR_EQUAL:
						constraintsOnField.add(new Constraint(rc, comparator));
						return true;
					case LESS_THAN:
					case LESS_OR_EQUAL:
					case EQUAL:
					default:
						if (overwrite) {
							constraintsOnField.set(0, new Constraint(rc, comparator));
							return true;
						}
						return false;
				}
			default:
				return false;
		}
	}

	/**
	 * @return true if exprCtx is an expression that compares two values and can be
	 *         evaluated as a boolean. False otherwise.
	 */
	public static boolean isSimpleComparison(final EsperParser.ExprContext exprCtx){
		return exprCtx.expr(1) != null 
				&& exprCtx.comparisonOperator != null 
				&& exprCtx.booleanOperator == null
				&& exprCtx.expr(0).expr(0) == null // Cannot expand 1st expr to {expr op expr}
				&& exprCtx.expr(1).expr(0) == null; // Cannot expand 2nd expr to {expr op expr}
	}

	/**
	 * Represents a constraint on an event field. This is composed of a
	 * RelationalComparison (e.g. "<", "=", etc.), and a comparator (i.e. the
	 * constraint boundary).
	 */
	private class Constraint {
		private final RelationalComparison relationalComparison;
		private final String comparator;
		private Constraint(RelationalComparison relationalComparison, String comparator){
			this.relationalComparison = relationalComparison;
			this.comparator = comparator;
		}
	}

	/**
	 * Enum for accepted relational comparisons that can be used to define a
	 * condition in an event expression in EPL. 
	 * 
	 * This includes LESS_THAN("<"), LESS_OR_EQUAL("<="), EQUAL("="),
	 * GREATER_OR_EQUAL(">="), and GREATER_THAN(">").
	 */
	private enum RelationalComparison {
		LESS_THAN("<"), 
		LESS_OR_EQUAL("<="), 
		EQUAL("="), 
		GREATER_OR_EQUAL(">="), 
		GREATER_THAN(">"), 
		UNSUPPORTED("");

		private final String operatorEPL;

		private RelationalComparison(String operatorEPL){
			this.operatorEPL = operatorEPL;
		}

		/** Returns the relational comparison if the operands in the comparison were to
		 * be switched sides. (if the LHS and RHS of the comparison were switched) */
		private RelationalComparison invert(){
			switch(this){
				case LESS_THAN:
					return GREATER_THAN;
				case LESS_OR_EQUAL:
					return GREATER_OR_EQUAL;
				case GREATER_OR_EQUAL:
					return LESS_OR_EQUAL;
				case GREATER_THAN:
					return LESS_THAN;
				case EQUAL:
				default:
					return this;
			}
		}

		/** Returns the RelationalComparison enum that matches the given string.*/
		private static RelationalComparison fromString(final String operator){
			for(RelationalComparison relationalComparison: RelationalComparison.values()){
				if (operator.equals(relationalComparison.operatorEPL)) {
					return relationalComparison;
				}
			}
			return UNSUPPORTED;
		}
	}
}
