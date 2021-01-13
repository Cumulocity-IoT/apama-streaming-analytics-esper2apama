/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.ArrayList;
import java.util.List;


public class TranslatePattern extends EsperBaseVisitor<EPLOutput>  {

	private Scope scope;

	public TranslatePattern(Scope scope){
		this.scope = scope;
	}

	@Override 
	public EPLOutput visitPattern(EsperParser.PatternContext ctx){
		// TODO THIS HAS NOT BEEN TESTED
		EPLOutput eplOut = new EPLOutput("on ");
		if (ctx.every != null) {
			eplOut.add("all ");
		}

		EsperParser.PatternContext patternCtx = ctx;
		List<EPLOutput> eventExpression = new ArrayList<EPLOutput>(2);
		while (patternCtx.operator != null) {
			eventExpression.add(0, translateSinglePattern(patternCtx.pattern(1)));
			eventExpression.add(0, new EPLOutput(patternCtx.operator.getText()));
			patternCtx = patternCtx.pattern(0);
		}
		eventExpression.add(0, translateSinglePattern(patternCtx));
		
		for(EPLOutput component : eventExpression){
			eplOut.add(component).add(" ");
		}
		return eplOut;
	}

	public EPLOutput translateSinglePattern(EsperParser.PatternContext ctx){
		EPLOutput eplOut = new EPLOutput();

		while(ctx.pattern (1) != null){
			// TODO 
		}
	
		if(ctx.not != null){
			eplOut.add(" not ");
			eplOut.add(translateSinglePattern(ctx.pattern(0)));
		}
		if (ctx.getText().startsWith("(") && ctx.getText().endsWith(")") 
			 && ctx.pattern(1) == null) {
			eplOut.add("(").add(translateSinglePattern(ctx.pattern(0))).add(")");
		} 
		if(ctx.filterExpressionArgs != null){
			eplOut = EPLOutput.cannotTranslate(ctx, "Cannot translate filter expressions in pattern");
		} 
		if(ctx.customObserverArgs != null){
			eplOut = EPLOutput.cannotTranslate(ctx, "Cannot translate custom plug-in observers in pattern");
		}
		if(ctx.timerAtArgs != null){
			// TODO: PAB-2036
			// Hint : Use Event Expression class
		} 
		if(ctx.timerIntervalArgs != null){
			// TODO: PAB-2037
			// Hint : Use Event Expression class
		}
		return eplOut;
	}
}
