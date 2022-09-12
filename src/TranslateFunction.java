/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.apama.e2a;


import org.antlr.v4.runtime.ParserRuleContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map;

public class TranslateFunction extends EsperBaseVisitor<EPLOutput> {
	private Scope scope;

	private static final Set<String> ESPER_FRAGMENT_FUNCTION_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
		"getString",	
		"getNumber"
	)));

	/* ESPER_TO_EPL_INTEGER - A set of Esper integer types, used by esperCastToEplType.
	 * Esper integral types - all are converted to EPL integer type. The end user should call .floor() if the
	 * expression returns non float or decimal type. In such case EPL compiler alerts the user for manual edit.
	 * */
	private static final Set<String> ESPER_TO_EPL_INTEGER = Collections.unmodifiableSet(new HashSet<String>() {{
		add("long");
		add("Long");
		add("Int");
		add("Integer");
		add("int");
	}});

	/* ESPER_TO_EPL_FLOAT - A set of Esper multi-precision types, used by esperCastToEplType.
	 * Esper multi-precision types - EPL has only float type. It is safer to call .toFloat() on an stringified float.
	 * Rest of the expressions are translated as they are.
	 * */
	private static final Set<String> ESPER_TO_EPL_FLOAT = Collections.unmodifiableSet(new HashSet<String>() {{
		add("float");
		add("Float");
		add("double");
		add("Double");
		add("BigDecimal");
		add("java.math.BigDecimal");
		add("Number");
	}});

	/* ESPER_TO_EPL_STRING - A set of Esper string types, used by esperCastToEplType.
	 * Esper string types - The set is created to avoid calling .toString() on string types.
	 * Rest of the expressions are translated with ".toString()" as it is safer to call .toStirng() on most of the EPL types.
	 * */
	private static final Set<String> ESPER_TO_EPL_STRING = Collections.unmodifiableSet(new HashSet<String>() {{
		add("string");
		add("String");
		add("java.lang.String");
	}});

	TranslateFunction(Scope scope) {
		this.scope = scope;
	}

	private static Map<String, String> initMethodTranslations() {
		final Map<String, String> result = new HashMap<String, String>();
		result.put("Test", "EPLTranslated");
		return Collections.unmodifiableMap(result);
	}

	private static final Map<String, String> ESPER_TO_EPL_METHOD_TRANSLATIONS = initMethodTranslations();

	@Override
	public EPLOutput visitFunctionCall(EsperParser.FunctionCallContext ctx) {
		String functionName = ctx.memberLookup().getText();
		if(scope.getSelect()== null && functionName.startsWith("find")) {
			return EPLOutput.cannotTranslate(ctx, "find... calls outside of a select statement");
		}

		if (ESPER_FRAGMENT_FUNCTION_NAMES.contains(functionName)) {
			return getFragment(ctx);
		} else if(functionName.equals("findManagedObjectById")) {
			return findManagedObjectById(ctx);
		} else if ("findFirstAlarmBySourceAndStatusAndType".equals(functionName))  {
			return findFirstAlarmBySourceAndStatusAndType(ctx);
		} else if(functionName.equals("findFirstManagedObjectByType")) {
			return findFirstManagedObjectByType(ctx);
		} else if(functionName.equals("findOneManagedObjectByType")) {
			return findOneManagedObjectByType(ctx);
		} else if(functionName.equals("cast")) {
			return esperCastToEplType(ctx);
		} else if(ctx.getText().equals("current_timestamp()")){
			return new EPLOutput().add("currentTime"); // This is a special case 
		} else if(ESPER_TO_EPL_METHOD_TRANSLATIONS.containsKey(functionName)) {
			// Generic case
			EPLOutput generateOutput = new EPLOutput();
			generateOutput.add(ESPER_TO_EPL_METHOD_TRANSLATIONS.get(functionName)).add("(");
			for(int i = 0; ctx.arguments().expr(i) != null; i++){
				generateOutput.add(new TranslateExpr(this.scope).visit(ctx.arguments().expr(i))); 
				// If method takes multiple arguments, separate them with commas 
				if(ctx.arguments().expr(i + 1) != null){
					generateOutput.add(", ");
				} 
			}
			return generateOutput.add(")");
		}
		return EPLOutput.cannotTranslate(ctx);

		// Esper model accepts Date type for 'time' field for Alarms/Measurements etc.
		// Apama uses timestamp for these fields of type float. 
		/*if(ctx.getText().equals("current_timestamp().toDate()") && outputType.getEPLName().equals("Alarm")){
			return new EPLOutput().add("currentTime");
		} else if (ctx.getText().equals("current_timestamp().toDate()")){
			return new EPLOutput().cannotTranslate("Unsure of how to translate "+ctx.getText()+" in this context. EPL uses the float type for storing timestamps (see 'currentTime' global variable). If a date format is required, see the 'TimeFormat' event library.");
		} 
		// Generic case. Translate each function indivdually 
		EPLOutput eplOut = new EPLOutput();
		for(int i = 0; ctx.function(i) != null; i++) {
			eplOut.add(new TranslateFunction(coassignee).visit(ctx.function(i)));
			if(ctx.function(i+1) != null){
				eplOut.add(".");
			}
		}
		return eplOut;*/


	}

	/**
	 * Translates a call to the Esper utility functions used to access fragments
	 * For now, this translation method supports 'getString' and 'getNumber' - this is a weird one.
	 * If the path argument is a string literal of a known field, just get that out via ordinary EPL - x.y.z .
	 * If it's an unknown string literal on a measurement, then we extract the measurement's value/units/extra params
	 * If it's an unknown string literal on anything else, then we use the AnyExtractor on the extra params of the object
	 * If it's on a custom schema, we just don't bother supporting that, as it doesn't make much sense in reality.
	 */
	private EPLOutput getFragment(EsperParser.FunctionCallContext ctx) {
		String functionName = ctx.memberLookup().getText();
		boolean isGetString = functionName.equals("getString");
		boolean isGetNumber = functionName.equals("getNumber");
		EPLOutput object = new TranslateExpr(this.scope).visit(ctx.arguments().expr(0));
		if(scope.getVar(ctx.arguments().expr(0).getText()) != null){
			object.setExprType(scope.getVar(ctx.arguments().expr(0).getText()));
		}
		String pathArgumentStringLiteral = stripStringLiteral(ctx.arguments().expr(1));
		if(pathArgumentStringLiteral == null) {
			return EPLOutput.cannotTranslate(ctx, "Fragment paths that are not string literals");
		}
		if(object.getExprType() instanceof Type.CustomSchema) {
			return EPLOutput.cannotTranslate(ctx, "Extracting fragments from custom schemas");
		}
		EPLOutput knownField = Misc.commonPathMapping(object, pathArgumentStringLiteral, true);
		if (knownField != null) {
			return knownField;
		} else {
			String[] path = stripStringLiteral(ctx.arguments().expr(1)).split("\\.");

			String warning = "This " + functionName + " implementation throws if this fragment is not present";
			if(object.isExprType("Measurement")) {
				if(path.length == 3) {
					EPLOutput ret = object.add(".measurements").add("[\"").add(path[0]).add("\"][\"").add(path[1]).add("\"]");
					if(path[2].equals("value")) {
						ret.add(".value");
						if (isGetString) {
							ret.add(".toString()");
						}
						return ret.addWarning(warning);
					}
					if(path[2].equals("unit")) {
						return ret.add(".unit").addWarning(warning);
					}
					ret.add(".params[\"").add(path[2]).add("\"]");
					if (isGetString) {
						ret.add(".valueToString()");
					}
					return ret.addWarning(warning);
				}
				return EPLOutput.cannotTranslate(ctx);
			} else {
				// Anything other than a Measurement
				scope.getFile().addUsing("com.apama.util.AnyExtractor");
				EPLOutput ret = new EPLOutput("AnyExtractor(").add(object);
				ret.add(".params[\"").add(path[0]);
				if(isGetNumber) {
					ret.add("\"]).getFloat(\"");
				} else {
					ret.add("\"]).getAny(\"");
				}
				for(int i = 1; i < path.length; i++) {
					ret.add(path[i]);
					if(i < path.length - 1) ret.add(".");
				}
				ret = ret.add("\")");
				if (isGetString) {
					ret.add(".valueToString()");
				}
				ret.addWarning(warning);
				return ret;
			}
		}
	}

	/** Translates a call to findManagedObjectById. Uses TranslateUnwindowedSelectClause.asyncCall to generate a use of the FindManagedObject event protocol. */
	private EPLOutput findManagedObjectById(EsperParser.FunctionCallContext ctx) {
		addUsingAndChannelSubscription("ManagedObject");

		String reqName = scope.getFile().uniqueVarName("fmo");
		EPLOutput asyncBit = new EPLOutput();
		asyncBit.
			addLine("integer " + reqName + "_req := Util.generateReqId();").
			addLine("send FindManagedObject(" + reqName + "_req, ").
				add(new TranslateExpr(scope).visit(ctx.arguments().expr(0))).
				add(", new dictionary<string, string>) to FindManagedObject.SEND_CHANNEL;").
				addLine();

		EventExpression receive = new EventExpression("FindManagedObjectResponse", reqName);
		receive.addConstraint("reqId", "=", reqName + "_req");
		EventExpression terminate = new EventExpression("FindManagedObjectResponseAck");
		terminate.addConstraint("reqId", "=", reqName + "_req");
		asyncBit.
			addLine("on ").add(receive.toEPLOutput()).
			addLine("   and not ").add(terminate.toEPLOutput());

		scope.getSelect().asyncCall(asyncBit);
		return new EPLOutput(reqName).add(".managedObject").setExprType(Type.getByEsperName("ManagedObjectCreated"));
	}

	/** Translates a call to findFirstAlarmBySourceAndStatusAndType. Uses TranslateUnwindowedSelectClause.asyncCall to generate a use of the FindAlarm event protocol. */
	private EPLOutput findFirstAlarmBySourceAndStatusAndType(EsperParser.FunctionCallContext ctx) {
		addUsingAndChannelSubscription("Alarm");

		String reqName = scope.getFile().uniqueVarName("findAlarm");
		EPLOutput asyncBit = new EPLOutput();
		TranslateExpr translateExpr = new TranslateExpr(this.scope);
		asyncBit.
				addLine("integer " + reqName + "_req := Util.generateReqId();").
				addLine("send FindAlarm(" + reqName + "_req, ").
				add("{\"source\":").add(translateExpr.visit(ctx.arguments().expr(0))).
				add(", \"status\":").add(translateExpr.visit(ctx.arguments().expr(1))).
				add(", \"type\":").add(translateExpr.visit(ctx.arguments().expr(2))).
				add("}) to FindAlarm.SEND_CHANNEL;").
				addLine();

		EventExpression receive = new EventExpression("FindAlarmResponse", reqName);
		receive.addConstraint("reqId", "=", reqName + "_req");
		EventExpression terminate = new EventExpression("FindAlarmResponseAck");
		terminate.addConstraint("reqId", "=", reqName + "_req");
		asyncBit.
			addLine("on ").add(receive.toEPLOutput()).
			addLine("   and not ").add(terminate.toEPLOutput());

		scope.getSelect().asyncCall(asyncBit);
		return new EPLOutput(reqName).add(".alarm").setExprType(Type.getByEsperName("AlarmCreated"));
	}

	/** Translates a call to findFirstManagedObjectByType. Uses TranslateUnwindowedSelectClause.asyncCall to generate a use of the FindManagedObject event protocol. */
	private EPLOutput findFirstManagedObjectByType(EsperParser.FunctionCallContext ctx) {
		addUsingAndChannelSubscription("ManagedObject");

		String reqName = scope.getFile().uniqueVarName("fmo");
		String fmo = scope.getFile().uniqueVarName("fmo");
		EPLOutput asyncBit = new EPLOutput();
		asyncBit.
			addLine("integer " + reqName + "_req := Util.generateReqId();").
			addLine("FindManagedObject " + fmo + " := new FindManagedObject;").
			addLine(fmo + ".reqId := " + reqName +"_req;").
			addLine(fmo + ".params[\"type\"] := " + (new TranslateExpr(scope).visit(ctx.arguments().expr(0))).formatOutput() +";").
			addLine("send " + fmo + " to FindManagedObject.SEND_CHANNEL;").
			addLine();

		EventExpression receive = new EventExpression("FindManagedObjectResponse", reqName);
		receive.addConstraint("reqId", "=", reqName + "_req");
		EventExpression terminate = new EventExpression("FindManagedObjectResponseAck");
		terminate.addConstraint("reqId", "=", reqName + "_req");
		asyncBit.
			addLine("on ").add(receive.toEPLOutput()).
			addLine("   and not ").add(terminate.toEPLOutput());

		scope.getSelect().asyncCall(asyncBit);
		return new EPLOutput(reqName).add(".managedObject").setExprType(Type.getByEsperName("ManagedObjectCreated"));
	}

	/** Translates a call to findOneManagedObjectByType. Uses TranslateUnwindowedSelectClause.asyncCall to generate a use of the FindManagedObject event protocol. */
	private EPLOutput findOneManagedObjectByType(EsperParser.FunctionCallContext ctx) {
		addUsingAndChannelSubscription("ManagedObject");

		String reqName = scope.getFile().uniqueVarName("fmo");
		String fmo = scope.getFile().uniqueVarName("fmo");
		EPLOutput asyncBit = new EPLOutput();
		asyncBit.
			addLine("integer " + reqName + "_req := Util.generateReqId();").
			addLine("FindManagedObject " + fmo + " := new FindManagedObject;").
			addLine(fmo + ".reqId := " + reqName +"_req;").
			addLine(fmo + ".params[\"type\"] := " + (new TranslateExpr(scope).visit(ctx.arguments().expr(0))).formatOutput() +";").
			addLine("send " + fmo + " to FindManagedObject.SEND_CHANNEL;").
			addLine();

		EventExpression receive = new EventExpression("FindManagedObjectResponse", reqName);
		receive.addConstraint("reqId", "=", reqName + "_req");
		EventExpression terminate = new EventExpression("FindManagedObjectResponseAck");
		terminate.addConstraint("reqId", "=", reqName + "_req");
		asyncBit.
			addLine("on ((").add(receive.toEPLOutput()).add(" and not ").add(terminate.toEPLOutput()).add(") -> ").
			addLine("   (").add(terminate.toEPLOutput()).add(" and not ").add(receive.toEPLOutput()).add("))");

		scope.getSelect().asyncCall(asyncBit);
		return new EPLOutput(reqName).add(".managedObject").setExprType(Type.getByEsperName("ManagedObjectCreated"));
	}

	/** Add required headers and subscribe to channel depending on predefined type for which find request is made*/
	private void addUsingAndChannelSubscription(String type) {
		scope.getFile().addUsing("com.apama.cumulocity.Util");
		switch(type) {
			case "Alarm":
				scope.getFile().addUsing("com.apama.cumulocity.FindAlarm");
				scope.getFile().addUsing("com.apama.cumulocity.FindAlarmResponse");
				scope.getFile().addUsing("com.apama.cumulocity.FindAlarmResponseAck");
				scope.getFile().addChannelSubscription("FindAlarmResponse.SUBSCRIBE_CHANNEL");
				break;
			case "ManagedObject":
				scope.getFile().addUsing("com.apama.cumulocity.FindManagedObject");
				scope.getFile().addUsing("com.apama.cumulocity.FindManagedObjectResponse");
				scope.getFile().addUsing("com.apama.cumulocity.FindManagedObjectResponseAck");
				scope.getFile().addChannelSubscription("FindManagedObjectResponse.SUBSCRIBE_CHANNEL");
				break;
		}
	}

	/** Convert Esper cast() function to corresponding EPL.
	 * getNumber - returns float, and EPL won't allow direct coversion to integer, i.e. toInteger() is not available.
	 *					EPL has floor() that returns integral part of float. Or round() that returns nearest integer to the float.
	 * getString - returns string, and it is safe to place .to<type>() call on the return type.
	 *
	 * In all other cases, we simply return the first expression as it is.
	 * Calling <exprType>.to<ExprType> on a type 'exprType' is forbidden in EPL. For e.g. float.toFloat() is not allowd.
	 *
	 * Conversion to EPL types Measurement, Operation expected to return same type.
	 * */
	private EPLOutput esperCastToEplType(EsperParser.FunctionCallContext ctx) {
		String expression = ctx.arguments().expr(0).getText();
		String esperType  = ctx.arguments().expr(1).getText();

		boolean isGetNumber = expression.startsWith("getNumber");
		boolean isGetString = expression.startsWith("getString");

		// Get known expression type of Cast() first expression.
		EPLOutput res = new EPLOutput().add(new TranslateExpr(this.scope).visit(ctx.arguments().expr(0)));
		if (ESPER_TO_EPL_INTEGER.contains(esperType)) {
			if (isGetNumber) {
				return res.add(".floor()");
			} else if (isGetString) {
				return res.add(".toInteger()");
			}
		}

		if (ESPER_TO_EPL_FLOAT.contains(esperType)) {
			if (isGetString) {
				return res.add(".toFloat()");
			}
		}

		if (ESPER_TO_EPL_STRING.contains(esperType)) {
			if (!isGetString) {
				// Avoid string to string
				return res.add(".toString()");
			}
		}

		/* A default scenario, may need to be updateed by user. */
		return res;
	}

	/** Some tokens will be Esper string literals - this gets out the content, minus the quoting */
	static private String stripStringLiteral(ParserRuleContext ctx) {
		String ret = ctx.getText();
		if(ctx.start == ctx.stop && (
			(ret.startsWith("\"") && ret.endsWith("\"")) ||
			(ret.startsWith("'") && ret.endsWith("'")))) {
			return ret.substring(1, ret.length() - 1);
		}
		return null;
	}
}

