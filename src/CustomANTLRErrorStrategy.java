/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.apama.e2a;

import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;

/** ANTLRErrorStrategy used for our parsing - just prints the first error and bails out, so we don't get any confusing codegen from an allegedly-successful parse */
public class CustomANTLRErrorStrategy extends DefaultErrorStrategy {
	@Override
	public Token recoverInline(Parser recognizer) {
		super.recoverInline(recognizer);
		System.err.println("Detected a syntax error in the Esper - this is more likely to be a bug in the tool than the original Esper");
		System.exit(1);
		return null;
	}

	@Override
	public void recover(Parser recognizer, RecognitionException e) {
		super.recover(recognizer, e);
		System.err.println("Detected a syntax error in the Esper - this is more likely to be a bug in the tool than the original Esper");
		System.exit(1);
	}

	@Override
	public void sync(Parser recognizer) { }
}
