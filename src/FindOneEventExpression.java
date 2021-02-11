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
import java.util.Collections;

// for findOne..(..) requests. We need to ensure that there is exactly one response else listener won't be triggered at all.
public class FindOneEventExpression extends EventExpression{
	private final static Map<String, String> RESP_ACK_MAP;
	public FindOneEventExpression(String type, String coassignee) {
		super(type, coassignee);
	}

	static {
		final Map<String, String> respAckMap = new HashMap<String, String>();
		respAckMap.put("FindManagedObjectResponse", "FindManagedObjectResponseAck");
		RESP_ACK_MAP = Collections.unmodifiableMap(respAckMap);
	}
	/*
		on	((A1() and not B1()) -> (B1() and not A1())) and 
		((A2() and not B2()) -> (B2() and not A2())) {...}
	*/
	@Override
	public EPLOutput toEPLOutput() {
		EPLOutput eplOut = new EPLOutput("((" + type);
		eplOut.add(constraintsToEPLOutput(true));
		eplOut.add(" and not " + RESP_ACK_MAP.get(type));
		eplOut.add(constraintsToEPLOutput(false));
		eplOut.add(") ->").addLine().add("  (" + RESP_ACK_MAP.get(type));
		eplOut.add(constraintsToEPLOutput(false));
		eplOut.add(" and not " + type);
		eplOut.add(constraintsToEPLOutput(false));
		eplOut.add(")");

		eplOut.add(")");
		return eplOut;
	}
}
