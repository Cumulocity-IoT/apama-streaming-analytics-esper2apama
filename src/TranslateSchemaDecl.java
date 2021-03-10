/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.apama.e2a;

import java.util.HashMap;
import java.util.Map;

/** Handles Esper 'create schema' declarations */
public class TranslateSchemaDecl extends EsperBaseVisitor<EPLOutput> {
	public TranslateSchemaDecl(Scope scope) {
		this.scope = scope;
	}

	private Scope scope;

	/** Esper schema declaration turns into a very simple equivalent EPL 'event' declaration, except for the typedef-style "create schema Foo as Bar" which we don't translate */
	public EPLOutput visitSchemaDecl(EsperParser.SchemaDeclContext ctx) {
		if(ctx.typedef != null) {
			return EPLOutput.cannotTranslate(ctx, "Schemas defined as aliases to other types");
		}

		EPLOutput members = new EPLOutput();
		// Map of member field name to the member type.
		Map<String, Type> membersMap = new HashMap<String, Type>();
		for(int i = 0; i < ctx.fieldNames.size(); i++) {
			Type t = Type.getByEsperName(ctx.fieldTypes.get(i));
			scope.getFile().addUsing(t);
			members.addLine(t.getEPLName()).add(" ").add(ctx.fieldNames.get(i)).add(";");
			String fieldName = ctx.fieldNames.get(i).getText();
			membersMap.put(fieldName, t);
			// Add warning for fields which are not exact mapping. Eg : BigDecimal to float
			if(Misc.APPROXIMATE_TYPE_MATCHING.containsKey(t.getNameInEsper())) {
				members.addWarning(Misc.APPROXIMATE_TYPE_MATCHING.get(t.getNameInEsper()));
			}
		}
		Type.declType(new Type.CustomSchema(ctx.schemaName, membersMap));

		return new EPLOutput().add("event ").add(ctx.schemaName).addBlock(members);
	}
}
