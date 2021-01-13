/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.HashMap;
import java.util.Map;

/**
 * Interface for types that are exclusively C8Y input types.
 * That is, they are exclusively Esper input streams, and require a monitor subscription to a channel in EPL.
 *
 * Anything that is an InputType is also a Type, but enforcing that in a language without multiple inheritance is a pain :-).
 */
public interface InputType {

	/** This method returns a utility action to filter the input stream events
	 *  based on whether the input event is a create event or an update event
	 *  returns null if the situation does not apply to any C8Y Event Type
	 *  */
	static UtilityAction getFilterActionForInputStreamType(Type inputType) {
		if(!(inputType instanceof InputType)) {
			return null;
		}
		// Measurements are special case - they cannot be updated (only created).
		if(inputType instanceof Type.Measurement) {
			return null;
		}

		String[] inputStreamTypes = new String[]{"Update", "Create"};
		for(String type: inputStreamTypes){
			// If we are creating or updating...
			if(inputType.getClassName().contains(type)){
				String name = "was" + type + "d";
				final Map<String, String> params = new HashMap<String, String>();
				params.put("e", "any");

				// The way we determine whether event was result of create/update is different for C8Y versions after 10.6.6
				EPLOutput version1066 = new EPLOutput()
						.add("// For Cumulocity version 10.6.6")
						.addLine("dictionary<any,any> payloadAttrs := <dictionary<any,any> > params[\"payload.attrs\"];")
						.addLine("string type := <string> payloadAttrs.getOrDefault(\"_type\");")
						.addLine("boolean "+name+" := (type.find(\""+type.toUpperCase()+"\") != -1);")
						.addLine("return "+name+";");
				EPLOutput post1066Versions = new EPLOutput()
						.add("// For versions of Cumulocity greater than 10.6.6")
						.addLine("string type := <string> params[\".apama_notificationType\"];")
						.addLine("boolean "+name+" := (type.find(\""+type.toUpperCase()+"\") != -1);")
						.addLine("return "+name+";");

				EPLOutput actionBody = new EPLOutput()
						.add("dictionary<string, any> params := <dictionary<string, any> > (e.getField(\"params\"));")
						.addLine("if (params.hasKey(\".apama_notificationType\")) ")
						.addBlock(post1066Versions)
						.add(" else").addBlock(version1066);

				// Add new utility action to monitor.
				return new UtilityAction(name, actionBody, params, "boolean", "Returns true if e indicates a Cumulocity object being " + type.toLowerCase() + "d");
			}
		}
		return null;
	}
}
