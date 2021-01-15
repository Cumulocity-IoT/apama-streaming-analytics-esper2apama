/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.apama.e2a;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Types which are special to Esper language. These doesn't have class name as
 * the Esper type name.
 */
class SpecialEsperType extends Type {
	private String nameInEsper;

	private SpecialEsperType(String nameInEPL, String nameInEsper) {
		super(nameInEPL);
		this.nameInEsper = nameInEsper;
	}

	public final static String UNSUPPORTED_FIELD = "unsupported field";

	private static Map<String, String> initCommonPathMappings() {
		final Map<String, String> result = new HashMap<String, String>();
		result.put("value", "");
		result.put("type", UNSUPPORTED_FIELD);
		result.put("name", UNSUPPORTED_FIELD);
		return Collections.unmodifiableMap(result);
	}

	private final static Map<String, String> COMMON_PATH_MAPPINGS = initCommonPathMappings();

	public String translateMemberName(String key) {
		return COMMON_PATH_MAPPINGS.getOrDefault(key, key);
	}

	@Override
	public String getNameInEsper() {
		return nameInEsper;
	}

	static class ModelId extends SpecialEsperType {
		ModelId() {
			super("string", "com.cumulocity.model.ID");
		}
	}

	static class BigDecimalFullPath extends SpecialEsperType {
		BigDecimalFullPath() {
			super("float", "java.math.BigDecimal");
		}
	}
}
