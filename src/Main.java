/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Deque;
import java.util.ArrayDeque;

public class Main {

	public static final Character UNICODE_BOM_CHAR = 0xfeff;
	
	public static void main(final String args[]) {
		final Deque<String> argss = new ArrayDeque<>(Arrays.asList(args));
		String firstArg = argss.poll();
		boolean ignoreComments = false;
		if ("--ignoreComments".equalsIgnoreCase(firstArg)) {
			ignoreComments = true;
			firstArg = argss.poll();
		}

		if (firstArg == null || "--help".equals(firstArg) || "-h".equals(firstArg)) {
			printUsage();
			return;
		}
		final String esperFileName = firstArg;
		String apamaFileName = argss.poll();
		if (apamaFileName == null) {
			final Path esperPath = Paths.get(esperFileName);
			apamaFileName = esperPath.getFileName().toString();
			apamaFileName += ".mon";
		}
		// Any extra args are silently ignored

		try {
			final EPLOutput epl = new E2ATranslator(FileSystems.getDefault().getPath(esperFileName), ignoreComments).translate();
			final Path outPath = FileSystems.getDefault().getPath(apamaFileName);
			generateOutputEPLFile(outPath.toString(), epl);
			System.exit(0);
		} catch (final NoSuchFileException nsfe) {
			System.err.println("File not found: " + nsfe.getMessage());
		} catch (final IOException ioe) {
			ioe.printStackTrace();
		}
		System.exit(1);
	}

	/**
	 * Write the translated EPL code to a file
	 * @param outPath path for the output file
	 * @param epl translated epl code
	 * @throws IOException
	 */
	private static void generateOutputEPLFile(String outPath, EPLOutput epl) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), StandardCharsets.UTF_8))) {
			writer.append(UNICODE_BOM_CHAR);
			writer.append(epl.formatOutput());
		} catch (IOException e) {
			throw e;
		}
	}

	/**
	 * Print usage info for the e2a tool.
	 */
	private static void printUsage() {
		String usage[] = {
			"Esper to Apama EPL translation tool",
			"Version " + (new Main().getClass().getPackage().getImplementationVersion()),
			"",
			"> e2a.bat [OPTION] ESPER_FILE [APAMA_EPL_FILE]      (on Windows)",
			"> ./e2a.sh [OPTION] ESPER_FILE [APAMA_EPL_FILE]     (on Linux)",
			"",
			"You must be running from an Apama command prompt, or have 'java' on the path",
			"from the Java 8+ JRE",
			"",
			"",
			"If the `APAMA_EPL_FILE` argument is specified then the resulting Apama EPL",
			"will be written to a file in the current working directory with the specified",
			"name.",
			"",
			"If the `APAMA_EPL_FILE` argument is not specified, the output file will be ",
			"written in the current working directory with the same name as the esper file,",
			"suffixed with '.mon' - the normal extension for Apama EPL files. For example:",
			"",
			"> e2a.bat MyEsper.cep",
			"",
			"will create a file named `MyEsper.cep.mon`",
			"",
			"Options:",
			"\t--ignoreComments\tignore all comments from ESPER_FILE while translating",
			"\t-h, --help\t\tdisplay this help and exit"
		};

		for(String line : usage) {
			System.out.println(line);
		}
	}

}
