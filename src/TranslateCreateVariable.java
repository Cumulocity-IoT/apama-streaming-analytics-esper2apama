/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import com.apama.e2a.EPLOutput;

/** Handles Esper 'create variable' declarations */
public class TranslateCreateVariable extends EsperBaseVisitor<EPLOutput> {
	public TranslateCreateVariable(Scope scope) {
		this.scope = scope;
	}

	private Scope scope;

	/** Esper global variables/constants declaration turns into monitor global variables/constants */
	public EPLOutput visitCreateVariable(EsperParser.CreateVariableContext ctx) {
		EPLOutput globalVariable = new EPLOutput();
		String varName = ctx.name.getText();

		Type t = Type.getByEsperName(ctx.type);
		if (ctx.constant != null) {
			globalVariable.add("constant ");
		}
		globalVariable.add(t.getEPLName());
		globalVariable.add(" ").add(varName);
		if (ctx.expr() != null) {
			globalVariable.add(" := ").add(new TranslateExpr(this.scope).visit(ctx.expr()));
		}
		globalVariable.add(";");
		scope.addVariableToGlobalScope(varName, t);
		if(Misc.APPROXIMATE_TYPE_MATCHING.containsKey(t.getNameInEsper())) {
			globalVariable.addWarning(Misc.APPROXIMATE_TYPE_MATCHING.get(t.getNameInEsper()));
		}
		return new EPLOutput().addLine(globalVariable);
	}
}
