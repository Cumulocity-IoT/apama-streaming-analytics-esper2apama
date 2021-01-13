/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

/** Handles an on ... set ... statement in esper */
public class TranslateOnSet extends EsperBaseVisitor<EPLOutput> {
	public TranslateOnSet(Scope scope) {
		this.scope = scope;
	}

	private Scope scope;

	/**
	 * The name of the identifier that the input event gets bound to e.g. "on FooType coassignee"
	 */
	private String coassignee = "unknown";
	private Type inputType = new Type.Unknown("???");

	/** Turns an 'on ... set VARIABLES' statement from esper to apama */
	@Override
	public EPLOutput visitOnSet(EsperParser.OnSetContext ctx) {

		EPLOutput onSetInput = visitOnSetInput(ctx.onSetInput());
		scope = scope.addVariableToLocalScope(coassignee, inputType);
		scope.getFile().addUsing(inputType);
		scope.getFile().addChannelSubscription(inputType);

		EPLOutput setOperations = new EPLOutput();
		for ( int i=0; i < ctx.identifier().size(); i++) {
			setOperations.addLine(new EPLOutput().add(ctx.identifier().get(i).getText()).add(" := ").add(new TranslateExpr(this.scope).visitExpr(ctx.expr().get(i))).add(";"));
		}

		EPLOutput ret = setOperations;

		UtilityAction filterAction = InputType.getFilterActionForInputStreamType(inputType);
		if (filterAction != null) {
			scope.getFile().addEPLUtilityAction(filterAction);
			ret = new EPLOutput().addLine("if (").add(filterAction.getName() + "(").add(coassignee).add(")").add(")").addBlock(ret);
		}

		ret = onSetInput.addBlock(ret);
		return ret;
	}

	/** Turns the INPUT from 'on INPUT set VARIABLES' to EPL listener and sets this.inputType and this.coassignee, where it's possible to do so */
	@Override
	public EPLOutput visitOnSetInput(EsperParser.OnSetInputContext ctx) {
		EPLOutput ret = new EPLOutput();
		if(ctx.typeName() != null) {
			this.inputType = Type.getByEsperName(ctx.typeName());
			if(ctx.coassignee == null){
				// If coassignee not assigned, then create one
				this.coassignee = this.inputType.getClassName().substring(0, 1).toLowerCase();
			} else {
				this.coassignee = ctx.coassignee.getText();
			}
			EPLOutput eventTemplate = ctx.filter == null ? new EPLOutput("()") : new EPLOutput(" ").add(EPLOutput.cannotTranslate(ctx.filter, "Not supporting filters yet")).add(" ");

			ret = ret.addLine("on all ").add(this.inputType.nameInEPL()).add(eventTemplate).add(" as ").add(coassignee);;
		} else {
			ret = EPLOutput.cannotTranslate(ctx, "Cannot translate patterns");
		}
		return ret;
	}
}
