/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
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
