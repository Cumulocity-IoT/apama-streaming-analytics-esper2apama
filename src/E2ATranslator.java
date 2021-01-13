/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenFactory;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Esper to Apama Translator class
 */
public class E2ATranslator {

	/* the path to the Esper file to translate from */
	private final Path filePath;

	/* If true, esper comments will be ignored */
	private final boolean ignoreComments;

	public E2ATranslator(final Path esperFilePath, boolean ignoreComments) {
		this.filePath = esperFilePath;
		this.ignoreComments = ignoreComments;
	}

	/**
	 * Takes an esper file from @filePath and translate it to EPL.
	 * @return an EPLOutput object representing the translated EPL.
	 */
	public EPLOutput translate() throws IOException {
		final CharStream esperStream = CharStreams.fromPath(filePath);
		final EsperLexer lexer = new EsperLexer(esperStream);

		TranslateEsperFile translateEsperFile = new TranslateEsperFile(filePath.getFileName().toString());
		if (ignoreComments) {
			System.out.println("WARNING: All comments from the input Esper file are ignored and will be omitted from the output EPL translation.");
		} else {
			lexer.setTokenFactory(new CustomCommonTokenFactory(translateEsperFile, lexer));
		}

		final EsperParser parser = new EsperParser(new CommonTokenStream(lexer));
		parser.setErrorHandler(new CustomANTLRErrorStrategy());
		final ParseTree tree = parser.esperFile();

		return translateEsperFile.visit(tree);
	}

	/**
	 * This customized token factory extracts esper comments using channel as a filter
	 * and adds the comments to the @translateEsperFile to be used at later point in time
	 * */
	private static class CustomCommonTokenFactory extends CommonTokenFactory {

		private EsperLexer lexer;

		private int lastTokenIndex = 0;

		/**
		 * TranslateEsperFile visitor to add comments to
		 */
		private TranslateEsperFile translateEsperFile;

		CustomCommonTokenFactory (TranslateEsperFile translateEsperFile, EsperLexer lexer) {
			this.translateEsperFile = translateEsperFile;
			this.lexer = lexer;
		}

		/**
		 *  Parser only reads token from the default channel and ignores the hidden channel tokens
		 *  Grammar is configured to push the esper comments to the hidden channel
		 *  This overridden method collects the comments using channel as a filter during tokenization by lexer
		 */
		@Override
		public CommonToken create(Pair<TokenSource, CharStream> source, int type, String text, int channel, int start, int stop, int line, int charPositionInLine) {
			CommonToken token = super.create(source, type, text, channel, start, stop, line, charPositionInLine);
			if (Lexer.HIDDEN == channel ) {
				// if the lastTokenWasOnSameLine, this comment belongs to the last token
				translateEsperFile.addComment(lexer.lastTokenWasOnSameLine ? lastTokenIndex : token.getStartIndex(), token.getText());
			} else {
				lastTokenIndex = token.getStopIndex();
			}
			lexer.lastTokenWasOnSameLine = true;
			return token;
		}
	}
}
