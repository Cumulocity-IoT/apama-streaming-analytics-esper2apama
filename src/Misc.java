/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */

package com.apama.e2a;
import java.util.Collections;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.apama.e2a.EPLOutput;
import com.apama.e2a.Type.SendEmail;
import com.apama.e2a.Type.SendSms;

import java.util.Arrays;

/** Class containing useful utilities for dealing with Cumulocity Esper versus EPL quirks */
final class Misc {

	// This class should not be instantiated.
	private Misc() {
	}

	private static Map<String, String> initC8yRestInterfacePaths() {
		final Map<String, String> result = new TreeMap<String, String>();
		result.put("Alarm", "/alarm/alarms");
		result.put("ManagedObject", "/inventory/managedObjects");
		result.put("Event", "/event/events");
		result.put("Measurement", "/measurement/measurements");
		result.put("Operation", "/devicecontrol/operations");
		return Collections.unmodifiableMap(result);
	}

	/** The paths for performing rest requests on artifacts in Cumulocity */
	public final static Map<String, String> C8Y_REST_INTERFACE_PATHS = initC8yRestInterfacePaths();

	private static final String LOSS_OF_PRECISION_WARNING_MESSAGE = "No EPL equivalent for Esper's BigDecimal. The EPL 'decimal' type does decimal floating point, but it is not infinite precision.";

	// This is a map for types in Esper which we are approximately mapping to EPL and with the corresponing warning message it should add while translating.
	public final static Map<String, String> APPROXIMATE_TYPE_MATCHING;

	static {
		final Map<String, String> aMap = new HashMap<String, String>();
		aMap.put("BigDecimal", LOSS_OF_PRECISION_WARNING_MESSAGE);
		aMap.put("java.math.BigDecimal", LOSS_OF_PRECISION_WARNING_MESSAGE);
		APPROXIMATE_TYPE_MATCHING = Collections.unmodifiableMap(aMap);
	}

	/**
	 * Make a key for a Map from the given params. E.g. makeKey(SendEmail.class,
	 * "cc") returns key for translating the "cc" field of an Esper SendEmail event.
	 * 
	 * @param esperClass class this key is associated with (which is a recognised
	 *                   Esper type)
	 * @param path       path to member variable.
	 * @return the key
	 */
	private static String makeKey(final Class<? extends Type> esperClass, final String path) {
		return esperClass.getSimpleName() + ":" + path;
	}

	/**
	 * Make mapping for Esper's 'SendEmail' type.
	 * @return Map of Epser key -> EPL equivalents.
	 */
	private static Map<String, String> makeSendEmailTranslations() {
		// Mapping to something more type safe than String would be preferable
		final Map<String, String> result = new HashMap<String, String>();
		// Cannot translate 'sender'. It's not actually documented in Esper(!)
		// but Esper doesn't error with it. Not sure what Esper does with it.
		// But there's no 'sender' equivalent in EPL so we can't translate it.
		result.put(makeKey(SendEmail.class, "sender"), "");
		return Collections.unmodifiableMap(result);
	}

	/**
	 * Make mapping for Esper's 'SendSms' type.
	 * @return Map of Epser key -> EPL equivalents.
	 */
	private static Map<String, String> makeSendSmsTranslations() {
		final Map<String, String> result = new HashMap<String, String>();
		result.put(makeKey(SendSms.class, "receiver"), "address");
		result.put(makeKey(SendSms.class, "text"), "message");
		result.put(makeKey(SendSms.class, "deviceId"), "sourceAssetId");
		return Collections.unmodifiableMap(result);
	}

	private static Map<String, String> initCommonPathMappings() {
		final Map<String, String> result = new HashMap<String, String>();
		result.put("id", "id");
		result.put("source", "source");
		result.put("name", "name");
		result.put("time", "time");
		result.put("count", "count");
		result.put("status", "status");
		result.put("severity", "severity");
		result.put("text", "text");
		result.put("type", "type");
		result.put("C8Y:creationTime", "params[\"creationTime\"]");
		result.put("C8Y:lastUpdated", "params[\"lastUpdated\"]");
		result.put("C8Y:id.value", "id");
		result.put("C8Y:source.value", "source");
		result.put("ManagedObject:childAssets", "childAssetIds");
		result.put("ManagedObject:childDevices", "childDeviceIds");
		result.put("ManagedObject:assetParents", "assetParentIds");
		result.put("ManagedObject:deviceParents", "deviceParentIds");
		result.put("ManagedObject:owner", "params[\"owner\"]");
		result.put("ManagedObject:c8y_Position.lat", "position[\"lat\"]");
		result.put("ManagedObject:c8y_Position.lng", "position[\"lng\"]");
		result.put("ManagedObject:c8y_Position.alt", "position[\"alt\"]");
		// ManagedObject also has position["accuracy"] entry in EPL - cannot find any Esper equivalent of this in samples
		result.put("Operation:deviceId", "source");
		result.put("Operation:deviceId.value", "source");
		result.putAll(makeSendEmailTranslations());
		result.putAll(makeSendSmsTranslations());
		return Collections.unmodifiableMap(result);
	}

	private final static Map<String, String> COMMON_PATH_MAPPINGS = initCommonPathMappings();

	/**
	 * Given a path expression (foo.bar.baz) that matches a known path in C8Y's data
	 * model, converts it to equivalent EPL.
	 *
	 * Can be given an object - that is, the object that this path is accessing -
	 * and converts to EPL based on all of the quirks in C8Y's data models. Note
	 * that the exprType variable on the EPLOutput object should be set. This allows
	 * some type safety in appyling the path mappings. See
	 * https://cumulocity.com/guides/event-language/data-model/#input-streams
	 *
	 * Or the object might be null, in which case we're getting something entirely
	 * static. Such as the enums that map directly to string literals in the
	 * equivalent EPL -
	 * https://cumulocity.com/guides/event-language/data-model/#additional-data-models
	 * .
	 *
	 * isRetrieval should be false if we are setting a memberlookup value: e.g.
	 * output.foo.bar.baz := x; isRetrieval should be true if we are retrieiving a
	 * lookup value: e.g. x := m.foo.bar.baz;
	 */
	public final static EPLOutput commonPathMapping(EPLOutput object, String path, boolean isRetrieval){
		if(path.isEmpty()) {
			return null;
		}

		String[] pathArray = path.split("\\.");

		if(object == null) {
			// Mapping static enums - see https://cumulocity.com/guides/event-language/data-model/#additional-data-model 
			switch(pathArray[0]) {
				case "CumulocitySeverities":
				case "CumulocityAlarmStatuses":
				case "OperationStatus":
					return new EPLOutput().add("\"").add(pathArray[1]).add("\""); // FIXME: Possible index OOB?
				default:
					return null;
			}
		}

		if (object.getExprType() == null) {
			throw new IllegalArgumentException("EPLOutput object must have an expr type");
		}
		
		// Don't map path for custom or unknown types
		if (object.getExprType() instanceof Type.Unknown) {
			return null;
		}

		if (object.getExprType() instanceof Type.CustomSchema || object.getExprType() instanceof SpecialEsperType.ModelId) {
			String trailingPath = object.getExprType().translateMemberName(path);
			return trailingPath.isEmpty() ? object : object.add(".").add(trailingPath);
		}

		// From here on, we know that we are mapping path of a known type
		switch (pathArray[0]) {
			case "measurement":
			case "managedObject":
			case "alarm":
			case "operation":
			case "#event":
				path = path.replaceFirst(pathArray[0], "");
				if(!path.isEmpty() && path.charAt(0) == '.') 
					path = path.replaceFirst(".", "");
				if(path.isEmpty()) {
					return object;
				}
				break;
			default:
				break;
		}

		// Simple direct 1-to-1 mappings
		if(COMMON_PATH_MAPPINGS.containsKey(path) && isRetrieval){
			 return object.add(".").add(COMMON_PATH_MAPPINGS.get(path));
		}

		// Path mappings that applies to keys with C8Y key prefix
		if(COMMON_PATH_MAPPINGS.containsKey("C8Y:"+path)){
			if(path.equals("lastUpdated") || path.equals("creationTime")){ // Special case - needs cast
				object = new EPLOutput("<float> ").add(object);
			}
			return object.add(".").add(COMMON_PATH_MAPPINGS.get("C8Y:"+path));
		}
		boolean isManagedObjectType = object.getExprType() instanceof Type.ManagedObject;
		if(isManagedObjectType && COMMON_PATH_MAPPINGS.containsKey("ManagedObject:"+path)){
			EPLOutput eplOut = object.add(".").add(COMMON_PATH_MAPPINGS.get("ManagedObject:"+path)); 
			if(isRetrieval && path.contains("owner")){
				eplOut.addWarning("This throws if this fragment is not present");
			}
			return eplOut;
		}

		boolean isOperationType = object.getExprType() instanceof Type.Operation;
		if(isOperationType && COMMON_PATH_MAPPINGS.containsKey("Operation:"+path)){
			return object.add(".").add(COMMON_PATH_MAPPINGS.get("Operation:"+path)); 
		}	

		final String key = makeKey(object.getExprType().getClass(), path);
		final String translation = COMMON_PATH_MAPPINGS.get(key);
		if (translation != null) {
			if (translation.isEmpty()) {
				return EPLOutput.cannotTranslate("Cannot translate \"" + key + "\"");
			} else {
				return object.add(".").add(translation);
			}
		}

		// not a known mapping
		return null;
	}
}
