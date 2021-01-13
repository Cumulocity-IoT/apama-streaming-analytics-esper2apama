/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
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
