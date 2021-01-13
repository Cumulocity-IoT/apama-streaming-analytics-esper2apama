/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;

/** Immutable object describing the surrounding lexical scope of something in the syntax tree that we're currently visiting */
class Scope {
	public Scope(TranslateEsperFile file) {
		this.file = file;
	}

	/** Add a variable (like a coassignment, or a local) into scope, which will be in the returned Scope */
	public Scope addVariableToLocalScope(String name, Type type) {
		Scope ret = this.clone();
		ret.variables.put(name, type);
		return ret;
	}

	/** Create a scope nested beneath this one, for when we enter a select */
	public Scope inSelect(TranslateUnwindowedSelectClause select) {
		Scope ret = this.clone();
		ret.select = select;
		return ret;
	}

	/** All variables in scope, names and types */
	public Map<String, Type> getVars() {
		TreeMap<String, Type> mergedVariables = new TreeMap<String,Type>(this.variables);
		// Add the global scope vars
		for (Map.Entry<String, Type> entry : globalVariables.entrySet()) {
			mergedVariables.put(entry.getKey(), entry.getValue());
		}
		return Collections.unmodifiableMap(mergedVariables);
	}

	/**
	 * Returns the Type of the variable in the scope with the given variableName. If
	 * the variable with given name does not exist in scope, then null is returned.
	 */
	public Type getVar(String variableName){
		// First check in the local scope
		if (this.variables.containsKey(variableName)) {
			return this.variables.get(variableName);
		}
		// If not found in the local scope, check in global scope
		if (globalVariables.containsKey(variableName)) {
			return globalVariables.get(variableName);
		}
		return null;
	}

	/** Translation visitor for the overall file we're in */
	public TranslateEsperFile getFile() {
		return this.file;
	}

	/** Add a global variable/constant. Once added, this variable will be available to all scope instances for that esper file. */
	public void addVariableToGlobalScope(String name, Type type) {
		globalVariables.put(name, type);
	}

	/** Translation visitor for the select statement we're working inside - only works if we're actually in one! */
	public TranslateUnwindowedSelectClause getSelect() {
		assert(select != null);
		return select;
	}

	protected Scope clone() {
		Scope ret = new Scope(this.file);
		ret.select = this.select;
		ret.variables = new TreeMap<String,Type>(this.variables);
		ret.globalVariables = this.globalVariables;
		return ret;
	}

	/**
	 * Creates a new Scope object, where the 'variables' and 'globalVariables'
	 * fields have been deep-copied, and the 'select' and 'file' fields are new
	 * TranslateUnwindowedSelectClause and TranslateEsperFile default-initialized
	 * objects.
	 * 
	 * This is intended for situations where we require access to the variable
	 * information contained in the scope, but we do not wish to modify the scope
	 * in any way.
	 */
	public Scope variablesCopy() {
		Scope ret = new Scope(new TranslateEsperFile(this.file.getDefaultMonitorName()));
		ret.variables = new TreeMap<String, Type>(this.variables);
		ret.globalVariables = new TreeMap<String, Type>(this.globalVariables);
		ret.select = new TranslateUnwindowedSelectClause(ret);
		return ret;
	}

	/** @see addVariableToLocalScope */
	private Map<String, Type> variables = new TreeMap<String, Type>();

	/** @see addVariableToGlobalScope */
	private Map<String, Type> globalVariables  = new TreeMap<String, Type>();

	/** @see getFile */
	private TranslateEsperFile file;

	/** @see inSelect */
	private TranslateUnwindowedSelectClause select;

	/** Default name of the EPL local variable that we generate to receive the incoming event for the statement - that is, a coassignment for an event expression */
	public static final String COASSIGNEE_NAME = "output";
}
