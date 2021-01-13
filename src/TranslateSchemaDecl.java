/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.misc.Interval;

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
