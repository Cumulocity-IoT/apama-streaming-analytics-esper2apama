/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;


import org.antlr.v4.runtime.ParserRuleContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TranslateFunction extends EsperBaseVisitor<EPLOutput> {
	private Scope scope;

	private static final Set<String> ESPER_FRAGMENT_FUNCTION_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
		"getString",	
		"getNumber"
	)));

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
		if (ESPER_FRAGMENT_FUNCTION_NAMES.contains(functionName)) {
			return getFragment(ctx);
		} else if(functionName.equals("findManagedObjectById")) {
			return findManagedObjectById(ctx);
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
		scope.getFile().addUsing("com.apama.cumulocity.Util");
		scope.getFile().addUsing("com.apama.cumulocity.FindManagedObject");
		scope.getFile().addUsing("com.apama.cumulocity.FindManagedObjectResponse");
		scope.getFile().addUsing("com.apama.cumulocity.FindManagedObjectResponseAck");
		scope.getFile().addChannelSubscription("FindManagedObjectResponse.SUBSCRIBE_CHANNEL");

		String reqName = scope.getFile().uniqueVarName("fmo");
		EPLOutput asyncBit = new EPLOutput();
		asyncBit.
			addLine("integer " + reqName + "_req := Util.generateReqId();").
			addLine("send FindManagedObject(" + reqName + "_req, ").
				add(new TranslateExpr(scope).visit(ctx.arguments().expr(0))).
				add(", new dictionary<string, string>) to FindManagedObject.SEND_CHANNEL;");
		EventExpression receive = new EventExpression("FindManagedObjectResponse", reqName);
		receive.addConstraint("reqId", "=", reqName + "_req");
		EventExpression terminate = new EventExpression("FindManagedObjectResponseAck");
		terminate.addConstraint("reqId", "=", reqName + "_req");
		scope.getSelect().asyncCall(asyncBit, receive, terminate);
		return new EPLOutput(reqName).add(".managedObject").setExprType(Type.getByEsperName("ManagedObjectCreated"));
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
