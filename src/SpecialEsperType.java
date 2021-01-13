/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
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
