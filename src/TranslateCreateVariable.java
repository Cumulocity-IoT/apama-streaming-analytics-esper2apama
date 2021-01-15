/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
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
