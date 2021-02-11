/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.apama.e2a;

import java.util.List;
import java.util.LinkedList;

/**
 * Class for Translating Esper patterns
 * See http://esper.espertech.com/release-8.6.0/reference-esper/html/event_patterns.html
 */
public class TranslatePattern extends EsperBaseVisitor<EPLOutput>  {

	private Scope scope;

	public TranslatePattern(Scope scope){
		this.scope = scope;
	}

	@Override 
	/**
	 * Method to translate the highest-level pattern in the parse-tree.
	 */
	public EPLOutput visitPattern(EsperParser.PatternContext ctx){
		if(ctx.every == null) {
			return EPLOutput.cannotTranslate(ctx, "Patterns without a top-level 'every'").add(translatePattern(ctx));
		} else {
			return new EPLOutput("on all ").add(translatePattern(ctx.pattern(0)));
		}
	}

	/**
	 * Translates any parsed patterns that are on lower levels of the parse tree
	 * (i.e. any patterns that are not the highest level pattern in the parse tree )
	 * @See visitPattern
	 */
	public EPLOutput translatePattern(EsperParser.PatternContext ctx){
		if(ctx.every != null) {
			return EPLOutput.cannotTranslate(ctx, "Patterns with a nested 'every'");
		} else if(ctx.operator != null) {
			// Translates patterns that consist of binary operators ('->', 'and', ...)
			return translatePattern(ctx.pattern(0)).add(" ").add(ctx.operator).add(" ").add(translatePattern(ctx.pattern(1)));
		} else if(ctx.not != null) {
			return EPLOutput.cannotTranslate(ctx, "Patterns using a 'not' operator").add(translatePattern(ctx.pattern(0)));
		} else if (ctx.enclosed != null) {
			return new EPLOutput().add("(").add(translatePattern(ctx.pattern(0))).add(")");
		} else if(ctx.eventFilter != null) {
			// Add relevant using statements and channel subscriptions and add the variable to scope, 
			// as this will improve the translation in other places
			Type inputType = Type.getByEsperName(ctx.eventFilter);
			scope.getFile().addUsing(inputType);
			scope.getFile().addChannelSubscription(inputType);
			if(ctx.coassignee != null){
				scope.addVariableToLocalScope(ctx.coassignee.getText(), inputType);
			}

			return EPLOutput.cannotTranslate(ctx, "Patterns with events");
		} else if(ctx.timerAtArgs != null) {
			// TODO PAB-2036
			return EPLOutput.cannotTranslate(ctx, "Patterns containing timer:at");
		} else if(ctx.timerIntervalArgs != null) {
			return translateTimerIntervalPattern(ctx.timePeriod());
		} else {
			return EPLOutput.cannotTranslate(ctx);
		}
	}

	/**
	 * Translate an Esper pattern that matches 
	 * timer:interval(x1 years x2 months ... x7 seconds x8 milliseconds).
	 * 
	 * For time units of years/months, an  E2A:TODO comment is added. Otherwise, the unit is converted to seconds,
	 * and an on "wait(x)" EPLOutput object is returned.
	 */
	private EPLOutput translateTimerIntervalPattern(EsperParser.TimePeriodContext ctx){
		EPLOutput waitInSeconds = new EPLOutput();
		for(int i = 0; i < ctx.units.size(); ++i){
			String unit = ctx.units.get(i).getText();
			if(unit.contains("week")) {
				waitInSeconds.add("7.0*24.0*60.0*60.0*");
			} else if(unit.contains("day")) {
				waitInSeconds.add("24.0*60.0*60.0*");
			} else if(unit.contains("hour")){
				waitInSeconds.add("60.0*60.0*");
			} else if(unit.contains("min")){
				waitInSeconds.add("60.0*");
			} else if(unit.contains("milli") || unit.equals("msec")){
				waitInSeconds.add("0.001*");
			} 
			if(unit.contains("year") || unit.contains("month")){
				waitInSeconds.add(EPLOutput.cannotTranslate(ctx.amounts.get(i).getText()+" "+unit));
			} else {
				EPLOutput amount = new TranslateExpr(scope).visit(ctx.amounts.get(i));
				waitInSeconds.add(Misc.tryCastToFloat(amount));
			}
			if(i < ctx.units.size() - 1){
				waitInSeconds.add(" + ");
			}
		}
		if(ctx.amount != null){ // if unit not specified, default is seconds
			EPLOutput amount = new TranslateExpr(scope).visit(ctx.amount);
			waitInSeconds.add(Misc.tryCastToFloat(amount));
		}
		return new EPLOutput("wait(").add(waitInSeconds).add(")");
	}
}

