/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Convert annotations from @<Annotationname> [([attribute [, attribute]*? ]?)]
 * into EPL statements. 
 * 
 * Note that @Name and @Description are the only handled cases currently and 
 * they produce comments with the attributes.
 * 
 * An annotation is part of the statement text and precedes the EPL select or
 * pattern statement. Annotations are therefore part of the EPL grammar. The
 * syntax for annotations follows thehost language (Java, .NET) annotation
 * syntax.
 */
public class TranslateAnnotation extends EsperBaseVisitor<EPLOutput> {

	TranslateAnnotation() {
	}

	@Override
	public EPLOutput visitStatementAnnotation(EsperParser.StatementAnnotationContext ctx) {
		EPLOutput ret = new EPLOutput();
		String annotationName = ctx.identifier().getText();

		if (annotationName.equalsIgnoreCase("name") || annotationName.equalsIgnoreCase("description")) {
			List<EsperParser.StatementAnnotationAttributeContext> attributes = ctx.statementAnnotationAttribute();
			if (attributes != null) {
				ret.add("//"+annotationName+": ");
				boolean first = true;
				//belt and braces just in case there are multiple entries
				for (EsperParser.StatementAnnotationAttributeContext attribute : attributes) {
					if (!first) {
						ret.add(","); //comma separate if multiple
					}
					ret.add(visit(attribute));
					first = false;
				}
			}
			return ret;
		}
		return EPLOutput.cannotTranslate(ctx , "Many built-in annotations");
	}
  
	@Override
	public EPLOutput visitStatementAnnotationAttribute(EsperParser.StatementAnnotationAttributeContext ctx) {
		return new EPLOutput(ctx);
	}
}
