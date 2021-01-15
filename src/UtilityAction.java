/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.apama.e2a;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/** Class for creating an EPLOutput object for an EPL action */
public class UtilityAction {

	private String name;
	private Map<String, String> params;  // key is name of param, value is type
	private String returnType; 
	private String description = ""; // EPL doc description 
	private EPLOutput body; 

	/** Returns the EPLOutput for an action */
	public EPLOutput addEPLAction(){
		EPLOutput actionOutput = new EPLOutput().addLine(" ");
		if (!description.trim().isEmpty()) {
			actionOutput.addLine("/**").addLine("* ").add(description).addLine("*/");
		}
		actionOutput.addLine("action ").add(name).add("(");
		// Add parameter list to action definition
		Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<String, String> param = it.next();
			// In params map, value is argument type; key is argument name 
			actionOutput.add(param.getValue().toString()).add(" "+param.getKey().toString());
			// separate params with commas
			if (it.hasNext()){
				actionOutput.add(", ");
			}
		}
		actionOutput.add(")");
		if(returnType != null && !returnType.trim().isEmpty()){
			actionOutput.add(" returns ").add(returnType);
		}
		return actionOutput.addBlock(body).addLine(" ");
	}

	public String getName(){
		return name;
	}

	/** action with no return type */
	UtilityAction(String name, EPLOutput body, Map<String, String> params){
		this.name = name;
		this.params = params;
		this.body = body;
	}

	/** action that takes no arguments */
	UtilityAction(String name, EPLOutput body, String returnType){
		this.name = name;
		this.params = new HashMap<String, String>();
		this.returnType = returnType;
		this.body = body;
	}

	/** action that takes no arguments */
	UtilityAction(String name, EPLOutput body, String returnType, String description){
		this.name = name;
		this.params = new HashMap<String, String>();
		this.returnType = returnType;
		this.description = description;
		this.body = body;
	}

	UtilityAction(String name, EPLOutput body, Map<String, String> params, String returnType){
		this.name = name;
		this.params = params;
		this.returnType = returnType;
		this.body = body;
	}

	UtilityAction(String name, EPLOutput body, Map<String, String> params, String returnType, String description){
		this.name = name;
		this.params = params;
		this.returnType = returnType;
		this.description = description;
		this.body = body;
	}

}