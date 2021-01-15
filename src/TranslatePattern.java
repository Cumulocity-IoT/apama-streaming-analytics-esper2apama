/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
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
