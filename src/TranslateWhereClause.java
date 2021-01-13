/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

public class TranslateWhereClause extends EsperBaseVisitor<EPLOutput> {

	private EsperParser.ExprContext whereCtx;
	private EventExpression eventExpression;
	private TranslateExpr translateExpr;
	private Scope scope;
	private EPLOutput nestedIfExpressionEPL = new EPLOutput();
	
	public TranslateWhereClause(Scope scope, EsperParser.ExprContext whereCtx, EventExpression eventExpression) {
		this.scope = scope;
		this.whereCtx = whereCtx;
		this.translateExpr = new TranslateExpr(scope);
		this.eventExpression = eventExpression;
	}

	/**
	 * Returns the event expression that assimilates as many constraints in the
	 * "where" clause as possible. This includes any comparison operations
	 * (excluding "!=") that directly compares a field on an event to a literal. If
	 * the constraint cannot be assimilated to the event expression, then we use a
	 * nested if statement inside the event listener instead.
	 * 
	 * If the boolean operator "or" appears in the where clause, then the whole
	 * where clasue is placed inside the nested if.
	 */
	public void updateEventExpression() {
		// The grammar will break down the "where" clause into a chain of conditions
		// ("expr comparisonOperator expr" evaluating to a boolean) that are joined by
		// boolean operators ("and"/"or").
		
		// Iterate through conditions from right to left
		EsperParser.ExprContext expressionCtx = this.whereCtx;
		boolean isComparison = false;
		while (expressionCtx.expr(1) != null) {
			// Only accept conditions joined with ANDs (if where clause contains an "OR",
			// then put the whole where clause in a nested if statement)
			boolean nextOperatorIsAnd = expressionCtx.booleanOperator == null
					|| expressionCtx.booleanOperator.getText().toLowerCase().equals("and");
			if (!nextOperatorIsAnd) {
				putWholeWhereClauseInNestedIf();
				return;
			}

			boolean isBoolean = expressionCtx.booleanOperator != null; 
			boolean isBooleanWithComparison = isBoolean
					&& EventExpression.isSimpleComparison(expressionCtx.expr(1));
			isComparison = EventExpression.isSimpleComparison(expressionCtx);
					
			if (isBooleanWithComparison) {
				boolean constraintSuccessfullyAdded = eventExpression.addConstraint(expressionCtx.expr(1), scope);
				if (!constraintSuccessfullyAdded){
					addToNestedIf(translateExpr.visit(expressionCtx.expr(1)));
				}
			} else if (isComparison) {
				boolean constraintSuccessfullyAdded = eventExpression.addConstraint(expressionCtx, scope);
				if (!constraintSuccessfullyAdded){
					addToNestedIf(translateExpr.visit(expressionCtx));
				}
			} else if (isBoolean) {
				// Cannot assimilate constraint to event expression.
				// But can be include in nested if instead.
				addToNestedIf(translateExpr.visit(expressionCtx.expr(1)));
			} else {
				 // Expression does not match supported pattern for creating event expression
				putWholeWhereClauseInNestedIf();
				return;
			}
			expressionCtx = expressionCtx.expr(0);
		} // End of while loop
		if (expressionCtx.expr(0) != null || !isComparison) {
			// If there are any conditions left over after the while loop, 
			// add to the nested if statement
			addToNestedIf(translateExpr.visit(expressionCtx));
		}
		stripNestedIfParentheses();
	}

	/**
	 * Add to the sequence of conditions included in the nested if (rather than
	 * event expression)
	 */
	private void addToNestedIf(final String condition) {
		if(nestedIfExpressionEPL.isEmpty()){
			nestedIfExpressionEPL.add(condition);
		} else {
			nestedIfExpressionEPL = new EPLOutput(condition).add(" and ").add(nestedIfExpressionEPL);
		}
		
	}

	/** @see addToNestedIf(String condition) */
	private void addToNestedIf(final EPLOutput condition) {
		addToNestedIf(condition.formatOutput());
	}

	/**
	 * Removes all of the constraints from the event expression, and places the whole 
	 * where clause inside the nestedIfExpressionEPL object.
	 */
	private void putWholeWhereClauseInNestedIf(){
		eventExpression.clearEventFieldConstraints();
		nestedIfExpressionEPL = new EPLOutput().add(translateExpr.visit(this.whereCtx));
		stripNestedIfParentheses();
	}

	/** If nestedIfExpressionEPL is enclosed by (redundant) parenteses, then remove them */
	private void stripNestedIfParentheses(){
		String ifString = nestedIfExpressionEPL.formatOutput();
		if (ifString.startsWith("(") && ifString.endsWith(")")) {
			nestedIfExpressionEPL = new EPLOutput(ifString.substring(1, ifString.length() - 1));
		}
	}

	public EPLOutput getNestedIf() {
		return nestedIfExpressionEPL;
	}

	/**
	 * Returns true if the EPL event expression does not cover all the conditions
	 * specified in a "where" clause (i.e. still require a nested if to cover
	 * some/all conditions)
	 */
	public boolean requiresNestedIf() {
		return !nestedIfExpressionEPL.isEmpty();
	}
}
