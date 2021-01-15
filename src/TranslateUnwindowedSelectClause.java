/*
 * Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.apama.e2a;

import java.util.Map;

import com.apama.e2a.Type.SendEmail;
import com.apama.e2a.Type.SendSms;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/** Handles a select clause that deals with single events at a time - no implicit or explicit windowing */
public class TranslateUnwindowedSelectClause extends EsperBaseVisitor<EPLOutput> {
	public TranslateUnwindowedSelectClause(Scope scope) {
		this.scope = scope.inSelect(this);
	}

	private Scope scope;
	private EventExpression eventExpression;

	/**
	 * The name of the identifier that the input event gets bound to e.g. "from FooType coassignee"
	 */
	private String coassignee = "unknown";
	private Type outputType = new Type.Unknown("???");
	private Type inputType = new Type.Unknown("???");
	private EPLOutput inputListenerSetupTodos = new EPLOutput();

	@Override
	/** Turns a select clause into an EPL listener for the input type;
	 * creates and populates the fields of the output type;
	 * optionally surrounded by an 'if' for the where clause;
	 * then sends it.
	 */
	public EPLOutput visitSelectClause(EsperParser.SelectClauseContext ctx) {
		this.outputType = outputType(ctx);
		scope.getFile().addUsing(outputType);
		this.inputType = new Type.Unknown("???");

		// Setup the listener for this statement's input
		if(ctx.insertInput().size() > 1) {
			inputListenerSetupTodos = new EPLOutput();
			for(int i = 0; i < ctx.insertInput().size(); i++) {
				inputListenerSetupTodos.addLine(EPLOutput.cannotTranslate(ctx.insertInput().get(i), "Multiple inputs to a select statement"));
			}
		} else {
			// TODO - PAB-2036/2037 Append patternsEPLOut to end of eventExpression EPLOutput
			EPLOutput patternsEplOut = visit(ctx.insertInput().get(0));
		}
		this.scope = this.scope.addVariableToLocalScope(coassignee, inputType);
		this.eventExpression = new EventExpression(inputType.nameInEPL().formatOutput(), coassignee);
		scope.getFile().addUsing(inputType);
		scope.getFile().addChannelSubscription(inputType);

		// Construct and send the output event
		EPLOutput generateOutput = new EPLOutput();
		if(outputType.getClassName().contains("Delete")){
			// Special case if output stream of statement is to a "Delete"-type stream
			String actionName = this.generateEPLDeleteUtilityAction();
			generateOutput.add(actionName+"(").add(new TranslateExpr(this.scope).visit(ctx.selectColumnExpr(0).expr())).add(");");
		} else {
			scope = scope.addVariableToLocalScope(Scope.COASSIGNEE_NAME, outputType);
			generateOutput.addLine(outputType.nameInEPL()).add(" " + Scope.COASSIGNEE_NAME + " := new ").add(outputType.nameInEPL()).add(";");

			for(EsperParser.SelectColumnExprContext ex : ctx.selects) {
				generateOutput.addLine(visit(ex));
			}
			generateOutput.addLine(outputType.howToSend());
		}
		if(!this.asyncSetup.isEmpty()) {
			generateOutput = this.asyncCallPreamble().addBlock(generateOutput);
		}
		if(ctx.insertStatementOutputThrottling() != null) {
			generateOutput = generateOutput.addLine(EPLOutput.cannotTranslate(ctx.insertStatementOutputThrottling(), "Select output throttling"));
		}

		// Any sort of discrimination around the input before we go on to generate output
		List<EPLOutput> nestedFiltering = new ArrayList<EPLOutput>(); 
		if (ctx.where != null) {
			TranslateWhereClause translateWhere = new TranslateWhereClause(scope, ctx.where, eventExpression);
			translateWhere.updateEventExpression();
			// Some "where" conditions may still need to be covered in nested if
			if (translateWhere.requiresNestedIf()) {
				nestedFiltering.add(new EPLOutput("if (").add(translateWhere.getNestedIf()).add(")"));
			} 
			if(!this.asyncSetup.isEmpty()) {
				nestedFiltering.add(this.asyncCallPreamble());
			}
		}
		nestedFiltering.add(this.filterEventsOnInputStreamType());

		EPLOutput ret = generateOutput;
		for(EPLOutput i : nestedFiltering) {
			if(!i.isEmpty()) {
				ret = i.addBlock(ret);
			}
		}

		EPLOutput inputListenerSetup = new EPLOutput("on all ").add(eventExpression.toEPLOutput());
		if (!this.inputListenerSetupTodos.isEmpty()) {
			inputListenerSetup.addLine(this.inputListenerSetupTodos);
		}
		return inputListenerSetup.addBlock(ret);
	}

	/** Figure out the output type of this statement - it's usually in the 'insert into'*/
	private Type outputType(EsperParser.SelectClauseContext ctx) {
		if(ctx.insertTo() == null) {
			return new Type.Unknown("???");
		} else {
			return Type.getByEsperName(ctx.insertTo().output);
		}
	}

	@Override
	/**
	 * Sets this.inputType and this.coassignee, where it's possible to do so, and
	 * adds any TODO comments for Esper components that cannot be translated to
	 * EPL in the input listener setup, and returns the EPLOuput object of the patterns translation (which is empty is pattern is not specified).
	 */
	public EPLOutput visitInsertInput(EsperParser.InsertInputContext ctx) {
		EPLOutput patternsEplOut = new EPLOutput();
		if(ctx.unidirectional != null) {
			inputListenerSetupTodos.addLine(EPLOutput.cannotTranslate("Unidirectional keyword"));
		}
		if(ctx.inputStream != null) {
			this.inputType = Type.getByEsperName(ctx.inputStream);
			if(ctx.coassignee == null){
				// If coassignee not assigned, then create one
				this.coassignee = this.inputType.getClassName().substring(0, 1).toLowerCase();
			} else {
				this.coassignee = ctx.coassignee.getText();
			}
			if(ctx.filter != null){
				inputListenerSetupTodos.addLine(
					EPLOutput.cannotTranslate("Filters on incoming events \""+ctx.inputStream.getText()+ctx.filter.getText() +"\""));
			}
		} else if (ctx.containedEventSelection() != null) {
			inputListenerSetupTodos.addLine(EPLOutput.cannotTranslate(ctx, "Contained-event selection"));
		} else {
			inputListenerSetupTodos.addLine(EPLOutput.cannotTranslate(ctx, "Event patterns")); // TODO PAB-2036/2037 REMOVE THIS LINE
			patternsEplOut.addLine(new TranslatePattern(scope).visit(ctx.pattern()));
		}
		return patternsEplOut;
	}

	@Override
	/**
	 * Each comma-separated bit of the 'select' is just an assignment to the output
	 * variable that we've created for emission
	 */
	public EPLOutput visitSelectColumnExpr(EsperParser.SelectColumnExprContext ctx) {
		if (ctx.fragments() != null) {
			return new TranslateFragments(this.scope).visit(ctx.fragments());
		} else {
			// Comma-separated string in Esper, sequence<string> in EPL.
			// These values need to be converted by splitting on ','.
			boolean splitCommaSepStr = false;

			EPLOutput outAssignment;
			if(ctx.identifier() == null) {
				outAssignment = new EPLOutput().add(Scope.COASSIGNEE_NAME).add(".").add(EPLOutput.cannotTranslate("Expressions in select without an 'as'"));
			} else {
				outAssignment = Misc.commonPathMapping(new EPLOutput(Scope.COASSIGNEE_NAME).setExprType(outputType),
						ctx.identifier().getText(), false);
				if (outAssignment == null) {
					// If esper field not listed in commonPathMappings, just assign field in EPL to
					// be same name as esper (if field does not exist this will prevent EPL from
					// executing)
					outAssignment = new EPLOutput(Scope.COASSIGNEE_NAME + "." + ctx.identifier().getText());
				}

				// Special cases for fields with different types between Esper and EPL
				final String eplOut = outAssignment.formatOutput();
				splitCommaSepStr = SendEmail.class.equals(outputType.getClass()) && 
						((Scope.COASSIGNEE_NAME + ".receiver").equals(eplOut) || (Scope.COASSIGNEE_NAME + ".cc").equals(eplOut));
				splitCommaSepStr = splitCommaSepStr || (SendSms.class.equals(outputType.getClass())
					&& (Scope.COASSIGNEE_NAME + ".address").equals(eplOut));
			}

			outAssignment.add(" := ");
			String rhs = new TranslateExpr(this.scope).visit(ctx.expr()).formatOutput();
			if (splitCommaSepStr) {
				rhs = "\",\".split(" + rhs + ")";
			}
			outAssignment.add(rhs).add(";");
			return outAssignment;
		}
	}

	/**
	 * Called for an expression inside this statement to say that it needs an asynchronous call (via event protocol) before it's evaluated.
	 * @param setup EPL to run before the expressions are evaluated. Usually a send/route to initiate the asynchronous call.
	 * @param receive The partial event expression that receives the result of the asynchronous call.
	 * @param terminate The partial event expression that means we're done with all receiving (usually an ack).
	 */
	public void asyncCall(EPLOutput setup, EventExpression receive, EventExpression terminate) {
		asyncSetup.addLine(setup).addLine(" ");
		asyncEventExpressionReceive.add(receive);
		asyncEventExpressionTerminate.add(terminate);
	}

	/**
	 * Using what went into asyncCall, generated the code to send the events, and an event listener that triggers when they're all done.
	 * Clears whatever went into asyncCall, so we're ready to start again with another completely unrelated expression in this statement e.g. the where clause.
	 */
	private EPLOutput asyncCallPreamble() {
		assert(!this.asyncSetup.isEmpty());
		EPLOutput ret = new EPLOutput();
		ret.add(this.asyncSetup);
		ret.addLine("on ");
		for(int i = 0; i < this.asyncEventExpressionReceive.size(); i++) {
			ret.add(this.asyncEventExpressionReceive.get(i).toEPLOutput());
			if(i != this.asyncEventExpressionReceive.size() - 1) {
				ret.add(" and ").addLine("   ");
			}
		}
		if(!this.asyncEventExpressionTerminate.isEmpty()) {
			ret.add(" and not");
			ret.add(this.asyncEventExpressionTerminate.size() == 1 ? new EPLOutput(" ") : new EPLOutput("(").addLine("       "));
			for(int i = 0; i < this.asyncEventExpressionTerminate.size(); i++) {
				ret.add(this.asyncEventExpressionTerminate.get(i).toEPLOutput());
				if(i != this.asyncEventExpressionTerminate.size() - 1) {
					ret.add(" and ").addLine("       ");
				}
			}
			ret.add(this.asyncEventExpressionTerminate.size() == 1 ? "" : ")");
		}

		// Reset async call state so the next asyncCall starts with a fresh slate
		this.asyncSetup = new EPLOutput();
		this.asyncEventExpressionReceive = new ArrayList<EventExpression>();
		this.asyncEventExpressionTerminate = new ArrayList<EventExpression>();
		return ret;
	}

	/** Used by asyncCall */
	private EPLOutput asyncSetup = new EPLOutput();

	/** Used by asyncCall */
	private List<EventExpression> asyncEventExpressionReceive = new ArrayList<EventExpression>(); 
	private List<EventExpression> asyncEventExpressionTerminate = new ArrayList<EventExpression>();
	
	/** Check if we are creating or updating object in C8Y.
	Esper has two separate input streams for Created/Updated. 
	Listeners in EPL will pick up events in both updated/created scenarios. 
	Creates an if condition to filter out irrelevant events. */
	private EPLOutput filterEventsOnInputStreamType() {
		UtilityAction filterAction = InputType.getFilterActionForInputStreamType(inputType);
		if (filterAction != null) {
			scope.getFile().addEPLUtilityAction(filterAction);
			return new EPLOutput().addLine("if (").add(filterAction.getName() + "(").add(coassignee).add(")").add(")");
		}

		return new EPLOutput();
	}

	/** Adds a utility action to the EPL translation for the insert statement. This action sends a request to Cumulocity to delete an object of the type defined by the output stream of the statement. The action takes a single parameter - the id of the object to be deleted. */
	private String generateEPLDeleteUtilityAction(){
		String type = outputType.getClassName().replace("Delete","");
		String name = "delete"+type;
		String path = Misc.C8Y_REST_INTERFACE_PATHS.get(type)+"/";
		EPLOutput actionBody = new EPLOutput()
			.add("GenericRequest deleteRequest := new GenericRequest;")
			.addLine("deleteRequest.reqId := Util.generateReqId();")
			.addLine("deleteRequest.path := \"").add(path).add("\" + id;")
			.addLine("deleteRequest.method := \"DELETE\";")
			.addLine("send deleteRequest to GenericRequest.SEND_CHANNEL;");
		Map<String, String> params = new HashMap<String, String>();
		params.put("id", "string");
		scope.getFile().addEPLUtilityAction(new UtilityAction(name, actionBody, params, "", "Deletes "+type+" with given id from Cumulocity."));
		// Utility method uses Util package
		scope.getFile().addUsing("com.apama.cumulocity.Util");
		return name;
	}
}
