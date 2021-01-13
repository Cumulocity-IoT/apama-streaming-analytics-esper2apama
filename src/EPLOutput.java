/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/** Like a StringBuilder, but for generated EPL */
public class EPLOutput {

	private final static String TODO_COMMENT_PREFIX = "TODO:E2A unsupported";
	private final static String WARN_COMMENT_PREFIX = "WARN:E2A";

	public EPLOutput() {
		lines.add("");
	}

	public EPLOutput(String s) {
		lines.add(s);
		this.exprType = new Type.Unknown(s);
	}

	/** Append to the current line */
	public EPLOutput add(String s) {
		clearSem();
		String last = lines.remove(lines.size() - 1);
		lines.add(last + s);
		return this;
	}

	/**
	 * Set of reserved words in Apama that are not also reserved words in Esper. This set is definitely incomplete, but we'll add to it on-demand - otherwise it's quite large.
	 * 
	 * @see escapeEsperIdentifier
	 */
	private final static Set<String> APAMA_KEYWORDS = Collections
			.unmodifiableSet(new HashSet<String>(Arrays.asList("event", "monitor", "print", "action", "using", "log")));

	/**
	 * Because some identifiers in Esper could innocently be EPL keywords, this method protects us from putting such an
	 * identifier verbatim into generated EPL.
	 * To avoid needing to take care in all of the code that generates output, we call this method to do the escaping in
	 * the parser at the point we parse an Esper identifier e.g. we parse 'event' into an identifier token '#event'.
	 */
	public static void escapeEsperIdentifier(Token identifierToken) {
		if(EPLOutput.APAMA_KEYWORDS.contains(identifierToken.getText())) {
			((org.antlr.v4.runtime.CommonToken)identifierToken).setText("#" + identifierToken.getText());
		}
	}

	/**
	 * Returns an @EPLOutput instance with all of the comments relevant to current context.
	 * It also removes all of these comments from @availableComments as they get added to the EPL
	 * @param availableComments comments still available for translation to EPL
	 * @param stopTokenIndex	stop index for the context. All the comments above this index belong to the context
	 * @return @{@link EPLOutput} containing all of the comments for the context
	 */
	public EPLOutput addRelatedComments(NavigableMap<Integer, String> availableComments, int stopTokenIndex) {
		for(int key : new TreeSet<Integer>(availableComments.headMap(stopTokenIndex, true).keySet())) {
			// remove the comment from available comments and add it to the EPL Output
			this.addLine(availableComments.remove(key));
		}
		return this;
	}

	/** Append to the current line */
	public EPLOutput add(EPLOutput e) {
		clearSem();
		String s1 = this.lines.remove(this.lines.size() - 1);
		String s2 = e.lines.remove(0);
		this.lines.add(s1 + s2);
		this.lines.addAll(e.lines);
		return this;
	}

	/** Append to the current line */
	public EPLOutput add(ParserRuleContext ctx) {
		clearSem();
		return this.add(new EPLOutput(ctx));
	}

	/** Append to the current line */
	public EPLOutput add(Token tok) {
		clearSem();
		return this.add(tok.getText());
	}

	/** Start a new line, then append */
	public EPLOutput addLine(String s) {
		clearSem();
		if(lines.get(lines.size() - 1).isEmpty()) {
			add(s);
		} else {
			lines.add(s);
		}
		return this;
	}

	/** Add an empty line (actually adds a line with a single space as implementation requires it to be non-empty string)*/
	public EPLOutput addLine() {
		return addLine(" ");
	}

	/** Start a new line, then append */
	public EPLOutput addLine(EPLOutput e) {
		clearSem();
		if(lines.get(lines.size() - 1).isEmpty())
		 	lines.remove(lines.size() - 1);
		lines.addAll(e.lines);
		return this;
	}

	/** Start a new line, then append */
	public EPLOutput addLine(ParserRuleContext ctx) {
		clearSem();
		return this.addLine(new EPLOutput(ctx));
	}

	/** Add block, but indented and bracketed with curly brackets. First brace can go on a new line or not */
	public EPLOutput addBlock(EPLOutput block, boolean newLine) {
		clearSem();
		if(newLine) {
			this.addLine("{");
		} else {
			this.add(" {");
		}
		for (String s : block.lines) {
			if (s.isEmpty()) {
				this.lines.add("");
			} else {
				this.lines.add("\t" + s);
			}
		}
		this.addLine("}");
		return this;
	}

	/** An addBlock implementation with newLine = true */
	public EPLOutput addBlock(EPLOutput block) {
		return this.addBlock(block, true);
	}

	// Returns true if expr occurs at least once in any of the lines.  
	public boolean contains(String expr){
		for(int i = 0; i < lines.size(); i++){
			if(lines.get(i).contains(expr)){
				return true;
			}
		}
		return false; // No lines contain expr. 
	}

	/** Turn this whole thing into a String */
	public String formatOutput() {
		StringBuilder ret = new StringBuilder();
		for(String s : lines) {
			ret.append(s);
			if(lines.size() > 1) ret.append("\n");
		}
		return ret.toString();
	}

	/** Extracts all the text covered by a given rule context */
	public EPLOutput(ParserRuleContext ctx) {
		lines.add("");
		this.add(ctx.getText());
	}

	/** Creates a warning comment in the EPL translation */
	public EPLOutput addWarning(String message){
		this.add("/* " + WARN_COMMENT_PREFIX + " - "+ message + " */");
		return this;
	}

	/** Creates a comment (block comment style, otherwise '//' style) saying that this bit of Esper cannot be translated, including all of the Esper enclosed by the given rule.
	 * The 'reason' should be appropriate to go in an auto-generated README under a list of language features we don't support.
	 * */
	public static EPLOutput cannotTranslate(ParserRuleContext ctx, String reason, boolean blockComments) {
		int start = ctx.start.getStartIndex();
		int stop = ctx.stop.getStopIndex();
		String[] lines = ctx.start.getInputStream().getText(new Interval(start, stop)).split("[\\r\\n]+");

		if(lines.length == 1 && lines[0].length() < 60) {
			return new EPLOutput().addLine((blockComments ? "/**" : "//") + " " +
				TODO_COMMENT_PREFIX + " - " + reason + (reason.isEmpty() ? "" : " ") +
				"\"" + lines[0] + "\" " + (blockComments ? "*/" : ""));
		} else {
			EPLOutput ret = new EPLOutput().add((blockComments ? "/**" : "//") + " " + TODO_COMMENT_PREFIX + " - " + reason + ":");
			// Conserve the (relative) whitespace at the start of each line in the output Esper code
			int leadingWSFirst = lines[0].length() - lines[0].replaceFirst("^\\s*", "").length();
			int leadingWSLast = lines[lines.length-1].length() - lines[lines.length-1].replaceFirst("^\\s*", "").length();
			// Calculate the difference between the whitespace at the start of the last line, and the whitespace at the start of the first line
			int whitespaceDiff = leadingWSLast - leadingWSFirst;
			for(String line : lines) {
				ret.addLine((blockComments ? "    " : "// ") + line.replaceFirst("^\\s{0,"+whitespaceDiff+"}", "")); // trim leading whitespace 
			}
			if(blockComments) ret.addLine("*/");
			return ret;
		}
	}

	/** See the main cannotTranslate method - this has block comments on by default, which is usually sensible */
	public static EPLOutput cannotTranslate(ParserRuleContext ctx, String reason) {
		return cannotTranslate(ctx, reason, true);
	}

	/** See the main cannotTranslate method - normally you should use a method that provides a reason, but use this for more generic cases that you can't describe e.g. a kind of statement or expression we just haven't implemented yet */
	public static EPLOutput cannotTranslate(ParserRuleContext ctx) {
		return cannotTranslate(ctx, "", true);
	}

	/** Creates a comment saying that something cannot be translated */
	public static EPLOutput cannotTranslate(String reason) {
		return new EPLOutput("/** " + TODO_COMMENT_PREFIX + " - " + reason + " */");
	}

	public boolean isEmpty(){
		return lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty());
	}

	/**
	 * Check if this EPLOutput is an expression of a specific type, by its EPL base name.
	 * This is in no way reliable - there is no thorough type checker here, but some simple expressions get given types. So often this will return false, even if it's clearly an expression of that type.
	 * */
	public boolean isExprType(String eplName) {
		if(this.exprType != null && this.exprType.getEPLName().equals(eplName)) {
			return true;
		}
		return false;
	}

	/** Finds the first occurence of a line containing the given expr, and inserts all the lines in linesToInsert immediately before it. */
	public EPLOutput insertLinesBeforeExpr(EPLOutput linesToInsert, String expr) {
		for(int i = 0; i < lines.size(); ++i){
			String line = lines.get(i);
			if(line.contains(expr)){
				for(String lineToInsert : linesToInsert.lines){
					lines.add(i, lineToInsert);
					++i;
				}
				return this;
			}
		}
		throw new RuntimeException("EPLOutput.insertLinesBeforeExpr error:\"" +expr+ "\" not found in lines.");
	}

	/** Say that this EPLOutput is an expression of type t */
	public EPLOutput setExprType(Type t) {
		this.exprType = t;
		return this;
	}

	/** Returns the expression of type t for this  EPLOutput */
	public Type getExprType() {
		return this.exprType;
	}

	/**
	 * Clear all semantic information for this object - right now, that's just the expression type.
	 * Should call this every time it has some text added to it; an EPLOutput that is an expression of a particular type is not likely to remain so after having something appended.
	 */
	private void clearSem() {
		if(!lines.isEmpty()){
			String[] firstLineTokens = lines.get(0).split("\\s+");
			if(firstLineTokens.length > 0){
				this.exprType = new Type.Unknown(firstLineTokens[0]+"...");
			} else {
				this.exprType = new Type.Unknown("");
			}
		} else {
			this.exprType = new Type.Unknown("");
		}
	}

	/** @see setExprType */
	private Type exprType = new Type.Unknown("");

	private List<String> lines = new ArrayList<String>();
}
