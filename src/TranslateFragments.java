/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Iterator;

/**
 * Class for Translating custom fragments
 *
 * Fragments are a list of key-value pairs, where the keys give the full JSON
 * path to the value e.g. {"keyComponent1.keyComponent2.keyComponent3a", 1,
 * "keyComponent1.keyComponent2.keyComponent3b", "C"} will result in the
 * following JSON structure: {"keyComponent1": {"keyComponent2":
 * {"keyComponent3a": 1, "keyComponent3b": "C"}}}
 *
 * see
 * https://cumulocity.com/guides/event-language/advanced-cel/#custom-fragment
 */
public class TranslateFragments extends EsperBaseVisitor<EPLOutput> {

	private EsperParser.FragmentsContext ctx;
	private final Scope scope;
	private EPLOutput eplOut = new EPLOutput();

	TranslateFragments(final Scope scope) {
		this.scope = scope;
	}

	@Override
	/**
	 * Outputs EPL translation of custom fragments.
	 * 
	 * In general case, it maps fragments to the EPL parameter "params". It does
	 * this by creating a tree structure, where the internal nodes store the
	 * fragment's key information (full JSON path to value), and the leaf nodes
	 * store the fragment's value information.
	 */
	public EPLOutput visitFragments(final EsperParser.FragmentsContext ctx) {
		this.ctx = ctx;
		// Use a tree structure to represent the JSON path information in the fragments' keys.
		Node fragmentsTreeRoot = new Node();
		
		// Iterate over each fragment's key-value pair
		for (EsperParser.FragKeyValuePairContext kvPair : ctx.kvPairs) {
			EPLOutput valueTranslation = new TranslateExpr(this.scope).visit(kvPair.value);
			// Add fragment to JSON tree structure
			String[] keyComponents = kvPair.key.getText().replace("\"", "").split("\\.");
			List<String> jsonPath = new ArrayList<String>(Arrays.asList(keyComponents));
			fragmentsTreeRoot.addPathToTree(jsonPath, valueTranslation);
		}

		// Variables for adding translation of special cases
		final boolean isManagedObject = scope.getVar(Scope.COASSIGNEE_NAME) instanceof Type.ManagedObject;
		final boolean isMeasurement = scope.getVar(Scope.COASSIGNEE_NAME) instanceof Type.Measurement;
		if (isManagedObject || isMeasurement) {
			FragmentOutputTarget target = isManagedObject? FragmentOutputTarget.MANAGED_OBJECT : FragmentOutputTarget.MEASUREMENTS;
			EPLOutput specialCaseAssignments = fragmentsTreeRoot.generateSpecialCaseEPL(target);
			if(!specialCaseAssignments.isEmpty()){
				eplOut.add(specialCaseAssignments);
			}
		}
		if (!fragmentsTreeRoot.childNodes.isEmpty()) {
			// Fragments maps to "params" field of object
			eplOut
				.addLine(Scope.COASSIGNEE_NAME + ".params :=")
				.add(fragmentsTreeRoot.generateEPLExpression(FragmentOutputTarget.GENERAL_PARAMS))
				.add(";");
		}
		return eplOut;
	}

	/**
	 * Class for creating tree structure to represent custom fragments.
	 * 
	 * Each leaf node stores the value of each fragment's KV pair.
	 * 
	 * The internal nodes of the tree (between root and leaf nodes) store the
	 * information about each fragment's key (i.e. the components of the JSON path).
	 */
	private class Node {

		/** The fragment's value. This should only be non-null for leaf nodes. */
		private EPLOutput value = null;

		/**
		 * A map of the child nodes, where the key is the path component to follow to
		 * visit the child node
		 */
		private SortedMap<String, Node> childNodes = new TreeMap<String, Node>();

		/**
		 * The name of the path component that should be taken to move from the parent
		 * of the current node to the current node
		 */
		final private String pathComponent;

		private Node(final String pathComponent) {
			this.pathComponent = pathComponent;
		}

		/** Constructor for root node */
		public Node() {
			this.pathComponent = null;
		}

		/**
		 * Wrapper for generateInternalEPLExpression. This should only be called on root
		 * node of tree.
		 */
		public EPLOutput generateEPLExpression(final FragmentOutputTarget target) {
			return new EPLOutput().addBlock(generateInternalEPLExpression(0, target), false);
		}

		/**
		 * Generates an EPLOutput object from the given tree structure. This method is
		 * recursive, and follows a depth-first traversal.
		 * 
		 * @param depth  The depth of the current node in the tree (root is depth 0).
		 * @param target The EPL component (i.e. field on output variable) being
		 *               produced.
		 */
		private EPLOutput generateInternalEPLExpression(final int depth, final FragmentOutputTarget target) {
			if (value != null) {
				return value;
			}
			EPLOutput eplOut = new EPLOutput();
			for (final String key : childNodes.keySet()) {
				Node child = childNodes.get(key);
				eplOut.addLine("\"" + child.pathComponent + "\":");
				// Cast nested fragments to "any" when required
				if (child.pathComponent.equals(childNodes.firstKey()) 
					&& ( target.equals(FragmentOutputTarget.MEASUREMENT_VALUE_PARAMS) ||
						 ( target.equals(FragmentOutputTarget.GENERAL_PARAMS) && depth > 0))) {
					eplOut.add(" <any>");
				}
				EPLOutput childOutput = child.generateInternalEPLExpression(depth + 1, target);
				if (child.childNodes.isEmpty() || child.value != null) {
					eplOut.add(" ").add(childOutput);
				} else { // nested object should be enclosed in curly braces
					eplOut.addBlock(childOutput, false);
				}
				// Separate entries with commas
				if (!child.pathComponent.equals(childNodes.lastKey())) {
					eplOut.add(",");
				}
			}
			return eplOut;
		}

		/**
		 * This method should be used for when the output stream is either a Measurement
		 * or ManagedObject type.
		 * 
		 * If the target is MEASUREMENTS, this generates the EPL where the
		 * "measurements" field is assigned and removes any "MeasurementValue" nodes
		 * from the tree it is called on.
		 * 
		 * If the target is MANAGED_OBJECT, this generates the EPL where the "position"
		 * field is assigned and removes any position nodes from the tree.
		 * 
		 * This method should be called on the root node of the general fragments tree.
		 */
		public EPLOutput generateSpecialCaseEPL(final FragmentOutputTarget target){
			boolean isMeasurement = target.equals(FragmentOutputTarget.MEASUREMENTS);
			boolean isManagedObject = target.equals(FragmentOutputTarget.MANAGED_OBJECT);
			EPLOutput eplOut = new EPLOutput(); 
			Node measurementsTree = new Node();
			// Iterate over child nodes at depth 1
			Iterator<String> depth1Iter = this.childNodes.keySet().iterator();
			while (depth1Iter.hasNext()) {
				final String PATH1 = depth1Iter.next();
				Node child1 = this.visitChild(PATH1);
				if(isManagedObject && PATH1.equals("c8y_Position")) {
					// Can map node to output.position field on ManagedObject 
					eplOut.addLine(Scope.COASSIGNEE_NAME + ".position :=").addBlock(child1.generateInternalEPLExpression(2, FragmentOutputTarget.MANAGED_OBJECT)).add(";");
					// Remove nodes that have been dealt with from general tree
					depth1Iter.remove();
				}
				if(isMeasurement) {
					// Iterate over child nodes at depth 2
					Iterator<String> depth2Iter = child1.childNodes.keySet().iterator();
					while (depth2Iter.hasNext()) {
						final String PATH2 = depth2Iter.next();
						Node child2 = child1.visitChild(PATH2);
						if (isMeasurement && child2.isMeasurementValueNode()) {
							// Can map to output.measurements field on Measurement 
							scope.getFile().addUsing("com.apama.cumulocity.MeasurementValue");
							EPLOutput measurementValue = child2.generateMeasurementValueEPL();
							List<String> pathToMeasurementValue = new ArrayList<String>();
							pathToMeasurementValue.add(PATH1);
							pathToMeasurementValue.add(PATH2);
							measurementsTree.addPathToTree(pathToMeasurementValue, measurementValue);
							// Remove nodes that have been dealt with from general tree
							depth2Iter.remove();
							if (child1.childNodes.isEmpty()) {
								depth1Iter.remove();
							}
						}
					}
				}
			}
			if (!measurementsTree.childNodes.isEmpty()) {
				eplOut.add(Scope.COASSIGNEE_NAME + ".measurements :=").add(measurementsTree.generateEPLExpression(target)).add(";");
			}
			return eplOut;
		}

		/**
		 * For a given MeasurementValue node, this method generates the EPL
		 * MeasurementValue object. MeasurementValue has 3 fields: "unit" (a string);
		 * "value" (a float); and "params" (a dictionary<string, any>).
		 */
		private EPLOutput generateMeasurementValueEPL() {
			EPLOutput measurementValue = new EPLOutput().add("MeasurementValue(");
			EPLOutput val = new EPLOutput("0.0");
			if (this.visitChild(VALUE) != null 
				&& this.visitChild(VALUE).value != null) {
				val = tryCastToFloat(this.visitChild(VALUE).value);
				// Remove "value" child node so it does not also get mapped to params
				this.childNodes.remove(VALUE);
			}
			String unit = "\"\"";
			if (this.visitChild(UNIT) != null 
				&& this.visitChild(UNIT).value != null) {
				unit = this.visitChild(UNIT).value.formatOutput();
				// Remove "unit" child node so it does not also get mapped to params
				this.childNodes.remove(UNIT);
			}
			// Any remaining children get mapped to MeasurementValue's "params" field
			EPLOutput params = new EPLOutput(" new dictionary<string, any>");
			if (!this.childNodes.isEmpty()) {
				params = this.generateEPLExpression(FragmentOutputTarget.MEASUREMENT_VALUE_PARAMS);
			}
			return measurementValue.add(val).add(", ").add(unit).add(",").add(params).add(")");
		}

		private final static String VALUE = "value";
		private final static String UNIT = "unit";

		/**
		 * Returns true if the current node has a child node that can be reached by
		 * following a pathComponent of "value" or "unit", and that child node is a leaf
		 * node.
		 */
		private boolean isMeasurementValueNode() {
			return ((this.childNodes.containsKey(VALUE) && this.visitChild(VALUE).value != null)
					|| this.childNodes.containsKey(UNIT) && this.visitChild(UNIT).value != null);
		}

		/**
		 * Adds the json path to the tree structure. A new node is added for each new
		 * path component
		 */
		public void addPathToTree(final List<String> path, final EPLOutput value) {
			Node leafNode = traverseTree(path);
			// Note: elements of 'path' that were successfully traversed were removed from
			// 'path' array by traverseTree.
			// This prevents them from been added multiple times when fragment keys follow
			// same json path.
			for (String pathComponent : path) {
				// Add new Node to tree for each new path component
				leafNode.childNodes.put(pathComponent, new Node(pathComponent));
				leafNode = leafNode.visitChild(pathComponent);
			}
			leafNode.value = value;
		}

		/**
		 * Traverses the tree until a pathComponent doesn't match. Returns the final
		 * node in tree before the given path diverges from it. Elements of path that
		 * were successfully traversed are removed from path array
		 */
		private Node traverseTree(final List<String> path) {
			if (path.isEmpty() || !childNodes.containsKey(path.get(0))) {
				// Return final node along path before path diverges
				return this;
			}
			// If path still exists in tree, go to the next node along the path
			Node next = visitChild(path.remove(0));
			// continue tree traversal from the next node
			return next.traverseTree(path);
		}

		/**
		 * Returns the child node with given pathComponent. If the node does not have a
		 * child with the given pathComponent, then the node itself returned.
		 */
		private Node visitChild(final String pathComponent) {
			if (childNodes.containsKey(pathComponent)) {
				return childNodes.get(pathComponent);
			}
			return this;
		}
	}

	/** Directive for the generate methods, saying what type of EPL expression a node should turn into (and what for) */
	private enum FragmentOutputTarget {
		GENERAL_PARAMS, /** dictionary<string, any> for assigning to arbitrary non-special fragments */
		MEASUREMENTS, /** dictionary<string, dictionary<string, MeasurementValue> > for assigning to a 'measurements' field */
		MEASUREMENT_VALUE_PARAMS, /** dictionary<string, any> for assigning to the params field in a MeasurementValue */
		MANAGED_OBJECT /** dictionary<string, float> for assigning to the position field in a ManagedObject */
	}

	/**
	 * Method used when translating measurement fragments. The MeasurementValue
	 * "value" field needs to be of type float.
	 *
	 * If eplout is an integer, simply add ".0" to the end. If it is already a valid
	 * float, output it unchanged.
	 *
	 * If it's not a numerical literal, we don't necessarily know what type it is,
	 * so just leave it unmolested and hope it's right.
	 */
	private static EPLOutput tryCastToFloat(final EPLOutput value) {
		try {
			Double.parseDouble(value.formatOutput()); // throws if value cannot be parsed to a number
			if (!value.contains(".")) {
				return value.add(".0"); // convert integer value to a float
			}
		} catch (NumberFormatException nfe) {
			// Do nothing - if there a type error in the resulting EPL then PS can deal with this.
		}
		return value;
	}
}
