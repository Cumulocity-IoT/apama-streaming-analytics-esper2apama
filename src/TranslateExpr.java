/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.List;
import java.util.ArrayList;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.ParserRuleContext;

/** Translating an arbitrary expression (the traditional language meaning of expression, not the Esper meaning) */
public class TranslateExpr extends EsperBaseVisitor<EPLOutput> {
	private Scope scope;

	public TranslateExpr(Scope scope) {
		this.scope = scope;
	}

	@Override
	/**
	 * Mostly passes through to other rules, but deals with binary operators - almost identical to EPL, except for '||' which appends strings, and "is" which is equality operator "=".
	 */
	public EPLOutput visitExpr(EsperParser.ExprContext ctx) {
		if (ctx.timeUnit() != null) {
			return EPLOutput.cannotTranslate(ctx, "Time literals");
		}
		if (ctx.objectForMemberCall != null) {
			return EPLOutput.cannotTranslate(ctx);
		}
		if (ctx.ordering != null) {
			return EPLOutput.cannotTranslate(ctx, "Asc/desc ordering");
		}
		if (ctx.exprUnsupported() != null) {
			return visit(ctx.exprUnsupported());
		}
		if (ctx.getText().startsWith("(") && ctx.getText().endsWith(")") ){
			if(ctx.operator == null && ctx.comparisonOperator == null && ctx.booleanOperator == null) {
				return new EPLOutput().addLine("(").add(visitExpr(ctx.expr(0))).add(")");
			} else {
				EPLOutput out = visit(ctx.expr(0));
				if (ctx.operator != null) {
					out.add(" ").add(ctx.operator).add(" ");
				} else if (ctx.comparisonOperator != null) {
					out.add(ctx.comparisonOperator);
				} else {
					out.add(ctx.booleanOperator);
				}
				return out.add( visit(ctx.expr(1))); 
			}
		} else if (ctx.operator == null && ctx.comparisonOperator == null && ctx.booleanOperator == null) {
			return TranslateExpr.super.visitExpr(ctx);
		} else {
			String opLower;
			if (ctx.operator != null) {
				opLower = ctx.operator.getText().toLowerCase();
				if (opLower.equals("||")) {
					return visit(ctx.expr(0)).add(" + ").add(visit(ctx.expr(1)));
				} else if(opLower.equals("not")) {
					return new EPLOutput().addLine("not ").add(visit(ctx.expr(0)));
				} else if(opLower.equals("<>")) {
					return EPLOutput.cannotTranslate(ctx, "SQL-style not-equals operator");
				}
			} else if (ctx.comparisonOperator != null) { 
				opLower = ctx.comparisonOperator.getText().toLowerCase();
				if (opLower.equals("is not")) {
					return visit(ctx.expr(0)).add(" != ").add(visit(ctx.expr(1)));
				} else if (opLower.equals("is")) {
					return visit(ctx.expr(0)).add(" = ").add(visit(ctx.expr(1)));
				}
			} else { // ctx.booleanOperator != null
				opLower = ctx.booleanOperator.getText().toLowerCase();
			}
			return visit(ctx.expr(0)).add(" ").add(opLower).add(" ").add(visit(ctx.expr(1)));
		}
	}

	@Override
	/**
	 * Our literals _almost_ translate 1:1 to EPL with exceptions
	 * - Esper 'float' literals (as opposed to 'double' literals) have a 'f' suffix that EPL doesn't want
	 * */
	public EPLOutput visitLiteral(EsperParser.LiteralContext ctx) {
		if(ctx.FLOAT() != null) {
			return new EPLOutput(ctx.getText().substring(0, ctx.getText().length() - 1));
		}
		if(ctx.getText().equals("null")) {
			return EPLOutput.cannotTranslate(ctx, "'null' values");
		}
		if(ctx.booleanLiteral() != null) {
			return new EPLOutput(ctx.getText().toLowerCase());
		}
		return new EPLOutput(ctx);
	}

	@Override
	/** Map in Esper is translated to a dictionary in EPL */
	public EPLOutput visitDictionary(EsperParser.DictionaryContext ctx){
		EPLOutput dictionaryOut = new EPLOutput().add("{");
		for(int i=0 ; i < ctx.keys.size(); i++){
			dictionaryOut.add(ctx.keys.get(i).getText()).add(": ");
			if(i == 0){
				dictionaryOut.add("<any> ");
			}
			dictionaryOut.add(new TranslateExpr(this.scope).visit(ctx.values.get(i)));
			if(i != ctx.keys.size()-1){
				dictionaryOut.add(", ");
			}
		}
		return dictionaryOut.add("}");
	}

	@Override
	/** Array in Esper is translated to a sequence in EPL */
	public EPLOutput visitArray(EsperParser.ArrayContext ctx){
		if(ctx.expr(0) == null){
			return new EPLOutput("new sequence<any>");
		}
		EPLOutput arrEPL = new EPLOutput().add("[");
		int i = 0;
		while(ctx.expr(i) != null){
			if(i == 0){
				arrEPL.add("<any> ");
			}
			arrEPL.add(new TranslateExpr(this.scope).visit(ctx.expr(i)));
			i++;
			if(ctx.expr(i) != null){
				arrEPL.add(", ");
			}
		}
		return arrEPL.add("]");
	}

	@Override
	/**
	 * This path expression might be some members on a variable in scope (named by the first element in the path), such as a 'from' input. If that doesn't apply, then it might be a global constant/enum.
	 * @see Misc.commonPathMapping for implementation details.
	 */
	public EPLOutput visitMemberLookup(EsperParser.MemberLookupContext ctx) {
		// check if the entire text is already in the scope as a variable before doing anything else
		// It avoids any mistranslation of known field names used as variable identifiers
		if (scope.getVar(ctx.getText()) != null) {
			return new EPLOutput().add(ctx.getText());
		}

		EPLOutput object = null;
		String identifer0 = ctx.identifier(0).getText();
		String pathTail = ctx.getText();
		if(scope.getVar(identifer0) != null) {
			object = new EPLOutput().add(identifer0).setExprType(scope.getVar(identifer0));
			// Don't add coassignee to pathTail
			pathTail = pathTail.replaceFirst(identifer0+".", "");
		}
		EPLOutput eplMemberLookup = Misc.commonPathMapping(object, pathTail, true);
		if(eplMemberLookup != null) {
			return eplMemberLookup;
		}
		if(object == null) {
			return EPLOutput.cannotTranslate(ctx);
		}
		return new EPLOutput(ctx.getText());
	}

	@Override
	public EPLOutput visitIdentifier(EsperParser.IdentifierContext ctx){
		return new EPLOutput(ctx.getText());
	}

	@Override
	public EPLOutput visitFunctionCall(EsperParser.FunctionCallContext ctx){
		return new TranslateFunction(this.scope).visit(ctx);
	}
	
	@Override
	public EPLOutput visitLambda(EsperParser.LambdaContext ctx) {
		return EPLOutput.cannotTranslate(ctx, "Lambda functions");
	}

	@Override
	/** Covers all kinds of expression we don't support yet without requiring specific coding for it*/
	public EPLOutput visitExprUnsupported(EsperParser.ExprUnsupportedContext ctx) {
		return EPLOutput.cannotTranslate(ctx);
	}
}
