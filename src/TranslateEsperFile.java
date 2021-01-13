/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.antlr.v4.runtime.tree.ParseTree;

import com.apama.e2a.EsperParser.CreateExpressionContext;
import com.apama.e2a.EsperParser.CreateVariableContext;
import com.apama.e2a.EsperParser.CreateWindowContext;


public class TranslateEsperFile extends EsperBaseVisitor<EPLOutput> {
	
	private final Scope scope = new Scope(this);
	private NavigableMap<Integer, String> comments = new TreeMap<>();
	private String defaultMonitorName;

	/**
	 * Create a TranslateEsperFile instance.
	 * @param esperFilename the name of the esper filename we're translating (without any path prefix)
	 */
	TranslateEsperFile(String esperFilename) {
		final int index = esperFilename.lastIndexOf(".");
		if (index > 0) {
			esperFilename = esperFilename.substring(0, index);
		}
		this.defaultMonitorName = esperFilename.replaceAll("\\s|-|\\.", "_"); // Replace whitespace, dash and period with _
	}

	/** add a comment to the @comments map */
	void addComment(int index, String comment) {
		comments.put(index, comment);
	}

	@Override
	/** Turns the whole file into a single monitor and top-level event declarations, spinning up various statements as part of onload */
	public EPLOutput visitEsperFile(EsperParser.EsperFileContext ctx) {
		EPLOutput monitorGlobals = new EPLOutput();
		EPLOutput fileGlobals = new EPLOutput();
		EPLOutput onloadContents = new EPLOutput();
		EPLOutput monitorDecl = new EPLOutput();
		String monitorName = this.defaultMonitorName;
		if (ctx.moduleDecl() != null) {
			monitorDecl.addRelatedComments(comments, ctx.moduleDecl().getStop().getStopIndex());
			monitorName = ctx.moduleDecl().moduleName.getText();
		} else {
			monitorDecl.addWarning("The Esper input file did not contain a module declaration. The monitor name was inferred from the name of the file.");
		}
		monitorDecl.addLine("monitor " + monitorName);


		for (EsperParser.StatementContext s : ctx.statement()) {
			EPLOutput dest = monitorGlobals;
			if (s.schemaDecl() != null) {
				dest = fileGlobals;
			}
			
			if (s.selectClause() != null || s.onSet() != null) {
				dest = onloadContents;
			}

			dest.addRelatedComments(comments, s.getStop().getStopIndex());

			for (int i = 0; i < s.getChildCount(); i++) {
				ParseTree i_ = s.getChild(i);
				if (!i_.getText().equals(";")) {
					EPLOutput e = this.visit(s.getChild(i));
					if (e == null) {
						dest.addLine(EPLOutput.cannotTranslate(s));
					} else {
						dest.addLine(e);
					}
				}
			}
			dest.addLine("\t");
		}

		EPLOutput monitorSubscriptions = new EPLOutput();
		for(String channel: this.channelSubscriptions){
			monitorSubscriptions.addLine("monitor.subscribe(" + channel + ");");
		}
		if(!this.channelSubscriptions.isEmpty()) {
			monitorSubscriptions.addLine();
		}
		onloadContents = monitorSubscriptions.addLine(onloadContents);

		EPLOutput usings = new EPLOutput();
		for(String s : this.used) {
			usings.addLine("using " + s + ";");
		}
		if(!this.used.isEmpty()) {
			usings.addLine();
		}

		EPLOutput utilityEPLActionsOutput = new EPLOutput();
		for(UtilityAction action : this.utilityEPLActions){
			utilityEPLActionsOutput.add(action.addEPLAction());
		}

		return new EPLOutput()
			.addLine(usings)
			.addLine(fileGlobals)
			.addLine(monitorDecl)
			.addBlock(
				monitorGlobals.
				addLine("action onload()").addBlock(onloadContents).
				add(utilityEPLActionsOutput)
			)
			// Add comments from the end of the esper file if any
			.addRelatedComments(comments, Integer.MAX_VALUE);
	}

	@Override
	public EPLOutput visitCreateWindow(CreateWindowContext ctx) {
		return EPLOutput.cannotTranslate(ctx, "Create window statement");
	}

	@Override
	/** We don't currently support creating variables/constants */
	public EPLOutput visitCreateVariable(CreateVariableContext ctx) {
		return new TranslateCreateVariable(this.scope).visitCreateVariable(ctx);
	}

	@Override
	/** We don't currently support creating expressions */
	public EPLOutput visitCreateExpression(CreateExpressionContext ctx) {
		return EPLOutput.cannotTranslate(ctx, "Custom expressions", false);
	}

	@Override
	/** @see TranslateOnSet */
	public EPLOutput visitOnSet(EsperParser.OnSetContext ctx) {
		return new TranslateOnSet(this.scope).visitOnSet(ctx);
	}

	@Override
	/** @see TranslateUnwindowedSelectClause */
	public EPLOutput visitSelectClause(EsperParser.SelectClauseContext ctx) {
		if (ctx.context != null) {
			return EPLOutput.cannotTranslate(ctx, "Select statement with context");
		} else if (new ClassifySelectClause().visit(ctx)) {
			return EPLOutput.cannotTranslate(ctx, "Select statement using a window");
		} else {
			return new TranslateUnwindowedSelectClause(this.scope).visitSelectClause(ctx);
		}
	}

	@Override
	/** @see TranslateAnnotation */
	public EPLOutput visitStatementAnnotation(EsperParser.StatementAnnotationContext ctx) {
		return new TranslateAnnotation().visit(ctx);
	}

	@Override
	/** @see TranslateSchemaDecl */
	public EPLOutput visitSchemaDecl(EsperParser.SchemaDeclContext ctx) {
		return new TranslateSchemaDecl(this.scope).visitSchemaDecl(ctx);
	}

	/** Called by other translation visitors to say what types they're using, so we can turn it into a bunch of (de-duplicated) 'using' declarations if necessary */
	public void addUsing(Type t) {
		if(!t.fqNameInEPL().formatOutput().equals(t.nameInEPL().formatOutput())) {
			used.add(t.fqNameInEPL().formatOutput());
		}
	}

	/** Like the other addUsing, but used for an EPL type that didn't come directly from an equivalent Esper type. For example, a utility type like 'AnyExtractor'. */
	public void addUsing(String fqNameInEPL) {
		used.add(fqNameInEPL);
	}

	/** Add to the list of monitor subscriptions */
	public void addChannelSubscription(Type t) {
		if(t instanceof InputType){
			addChannelSubscription(t.getSubscribeChannel());
		}
	}

	public String getDefaultMonitorName(){
		return defaultMonitorName;
	}

	/** Add to the list of monitor subscriptions */
	public void addChannelSubscription(String s) {
		channelSubscriptions.add(s);
	}

	/** Adds an action to the bottom of the monitor */
	public void addEPLUtilityAction(UtilityAction action) {
		for(UtilityAction a : utilityEPLActions){
			if(action.getName().equals(a.getName())){
				return; // Action already exists 
			}
		}
		// if action is not in the list, add it 
		utilityEPLActions.add(action);
	}

	/** Generates a file-unique variable name, named after root */
	public String uniqueVarName(String root) {
		String ret;
		if(uniqueVarName_counter == 1) {
			ret = root;
		} else {
			ret = root + uniqueVarName_counter;
		}
		uniqueVarName_counter = uniqueVarName_counter + 1;
		return ret;
	}

	/** Used by uniqueVarName */
	private int uniqueVarName_counter = 1;

	/** @see addUsing */
	private Set<String> used = new TreeSet<String>();

	/** @see addChannelSubscription */
	private Set<String> channelSubscriptions = new TreeSet<String>();

	/** @see addEPLUtilityAction */
	private List<UtilityAction> utilityEPLActions = new ArrayList<UtilityAction>();
	
}
