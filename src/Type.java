/*
 * $Copyright (c) 2020 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.$
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG
 */
package com.apama.e2a;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import org.antlr.v4.runtime.ParserRuleContext;

/** Represents a type in Esper, with details on how to translate its use into EPL. For most types, the name of the sub-class is the name of the type in Esper. */
class Type {
	Type(String fqNameInEPL) {
		this.fqNameInEPL = fqNameInEPL;
	}

	/** Fully qualified name in EPL (including package) */
	private final String fqNameInEPL;

	/** Get the base name (in EPL) of this type - that is, not fully-qualified */
	public EPLOutput nameInEPL() {
		String[] split = fqNameInEPL.split("\\.");
		return new EPLOutput(split[split.length - 1]);
	}

	/** Return string of class name */
	public String getClassName(){
		return this.getClass().getSimpleName();
	}

	/** Returned base name (in EPL) of this type as a String - i.e. not fully qualified */
	public String getEPLName() {
		return nameInEPL().formatOutput();
	}

	/** Get the fully-qualified name (in EPL) of this type */
	public EPLOutput fqNameInEPL() {
		return new EPLOutput(fqNameInEPL);
	}

	/** Name as it appears in the Esper source */
	public String getNameInEsper() {
		String ret = this.getClassName();
		if(ret.startsWith("_")) ret = ret.substring(1);
		return ret;
	}

	/** Synthesise a statement for sending an instance of this for the 'insert into' part of a statement */
	public EPLOutput howToSend() {
		return new EPLOutput(String.format("send %s to %s.SEND_CHANNEL;", Scope.COASSIGNEE_NAME, getEPLName()));
	}

	/** Find a type by its name (in Esper). Returns Unknown if it's not there. */
	public static final Type getByEsperName(ParserRuleContext nameInEsper) {
		return getByEsperName(nameInEsper.getText());
	}
	
	/** Find a type by its name (in Esper). Returns Unknown if it's not there. */
	public static final Type getByEsperName(String nameInEsper) {
		for(Type e : all) {
			if(e.getNameInEsper().toLowerCase().equals(nameInEsper.toLowerCase())) return e;
		}
		return new Unknown(nameInEsper);
	}

	/** If Esper contains a custom schema, the codegen should add it as a type here, so it is accessible to getByEsperName */
	public static final void declType(Type t) {
		all.add(t);
	}

	/** Check if there is any EPL mapping defined for fields in Esper. Else return the path as it is. */
	public String translateMemberName(String path) {
		return path;
	}

	/**
	 * Name of the EPL channel to subscribe to, if you want to receive events of this type. Only works for types implementing InputType.
	 * The common default is "NameOfEPLType.SUBSCRIBE_CHANNEL" .
	 */
	public String getSubscribeChannel() {
		InputType this_ = (InputType)this;
		return getEPLName() + ".SUBSCRIBE_CHANNEL";
	}

	/** List of all types for 'getByEsperName' */
	private static List<Type> all = populateTypeList();

	private static List<Type> populateTypeList() {
		List<Type> result = new ArrayList<>();

		// List of Type and all its (non-static) child classes
		List<Class<?>> typeClasses = new ArrayList<>();
		typeClasses.add(Type.class);
		typeClasses.add(InputType.class);
		typeClasses.add(SpecialEsperType.class);
		List<Class<?>> declaredTypes = new ArrayList<>();
		for(Class<?> c: typeClasses){
			for (Class<?> declaredClass: c.getDeclaredClasses()){
				declaredTypes.add(declaredClass);
			}
		}

		for(Class<?> c : declaredTypes) {
			if(Type.class.isAssignableFrom(c)) {
				if(c.getDeclaredConstructors()[0].getParameterTypes().length == 0) {
					try {
						result.add((Type) c.getDeclaredConstructors()[0].newInstance());
					} catch ( InstantiationException|IllegalAccessException|
						java.lang.reflect.InvocationTargetException e) {
						System.err.println(e.getMessage());
					}
				}
			}
		}
		return result;
	}

	/** Represents any type we don't know about */
	static class Unknown extends Type {
		Unknown(String badName) {
			super(null);
			this.badName = badName;
		}

		@Override
		public EPLOutput nameInEPL() { return EPLOutput.cannotTranslate("Don't know " + badName); }
		@Override
		public EPLOutput fqNameInEPL() { return nameInEPL(); }

		private String badName;
	}

	/** For schemas declared within an Esper file - by definition, the name in EPL will be the same as the name in Esper, and they only get sent internally (routed) */
	static class CustomSchema extends Type {
		private Map<String, Type> members = new HashMap<String, Type>();
		public CustomSchema(ParserRuleContext nameInEsper, Map<String, Type> members) {
			super(nameInEsper.getText());
			this.members = members;
		}

		/** Works only for single level. Doesn't check for nested levels as it will increase complexity and use case is very less */
		@Override
		public String translateMemberName(String path) {
			String[] pathArray = path.split("\\.");
			if(pathArray.length != 2) {
				return path;
			}
			Type t = members.get(pathArray[0]);
			String mapping = t.translateMemberName(pathArray[1]);
			if (mapping.equals(SpecialEsperType.UNSUPPORTED_FIELD)) {
				// Adding the original field along with the TODO, so that EPL fails to compile. For some cases adding empty field might generate a valid EPL code but with wrong logic.
				mapping = pathArray[1] + EPLOutput.cannotTranslate(mapping).formatOutput();
			}
			return pathArray[0] + (mapping.equals("") ? "" : "." + mapping);
		}

		@Override
		public String getNameInEsper() {
			return this.getEPLName();
		}

		@Override
		public EPLOutput howToSend() {
			return new EPLOutput("route " + Scope.COASSIGNEE_NAME + ";");
		}
	}

	// Built-in C8Y types, plus the wrapper types for their input and output streams, create/update/delete etc.
	static class Alarm extends Type { Alarm() { super("com.apama.cumulocity.Alarm");} }
	static class Event extends Type { Event() { super("com.apama.cumulocity.Event");} }
	static class ManagedObject extends Type { ManagedObject() { super("com.apama.cumulocity.ManagedObject");} }
	static class Measurement extends Type { Measurement() { super("com.apama.cumulocity.Measurement");} }
	static class Operation extends Type { Operation() { super("com.apama.cumulocity.Operation");} }
	
	static class CreateAlarm extends Alarm {}
	static class UpdateAlarm extends Alarm {}
	static class AlarmCreated extends Alarm implements InputType {}
	static class AlarmUpdated extends Alarm implements InputType {}

	static class CreateEvent extends Event {}
	static class UpdateEvent extends Event {}
	static class DeleteEvent extends Type {
		DeleteEvent() { super("com.apama.cumulocity.GenericRequest"); }
	}
	static class EventCreated extends Event implements InputType {}
	static class EventUpdated extends Event implements InputType {}
	static class EventDeleted extends Type implements InputType {
		EventDeleted() { super("com.apama.cumulocity.EventDeleted"); }
		public String getSubscribeChannel() { return "Event.SUBSCRIBE_CHANNEL";  }
	}

	static class CreateManagedObject extends ManagedObject {}
	static class UpdateManagedObject extends ManagedObject {}
	static class DeleteManagedObject extends Type {
		DeleteManagedObject() { super("com.apama.cumulocity.GenericRequest");}
	}
	static class ManagedObjectCreated extends ManagedObject implements InputType {}
	static class ManagedObjectUpdated extends ManagedObject implements InputType {}
	static class ManagedObjectDeleted extends Type implements InputType {
		ManagedObjectDeleted() { super("com.apama.cumulocity.ManagedObjectDeleted"); }
		public String getSubscribeChannel() { return "ManagedObject.SUBSCRIBE_CHANNEL"; }
	}

	static class CreateMeasurement extends Measurement {}
	static class MeasurementCreated extends Measurement implements InputType {}
	static class DeleteMeasurement extends Type {
		DeleteMeasurement() { super("com.apama.cumulocity.GenericRequest"); }
	}
	static class MeasurementDeleted extends Type implements InputType {
		MeasurementDeleted() { super("com.apama.cumulocity.MeasurementDeleted"); }
		public String getSubscribeChannel() { return "Measurement.SUBSCRIBE_CHANNEL"; }
	}

	static final class SendEmail extends Type {
		SendEmail() { super("com.apama.cumulocity.SendEmail"); }
	}

	static final class SendSms extends Type {
		SendSms() { super("com.apama.cumulocity.SendSMS"); }
	}

	static class CreateOperation extends Operation {}
	static class UpdateOperation extends Operation {}
	static class OperationCreated extends Operation implements InputType {}
	static class OperationUpdated extends Operation implements InputType {}

	// built-in types
	static class Number extends Type { Number() { super("float"); } }
	static class _String extends Type { _String() { super("string"); } }
	static class _boolean extends Type { _boolean() { super("boolean"); } }
	static class _float extends Type { _float() { super("float"); } }
	static class _double extends Type { _double() { super("float"); } }
	static class _int extends Type { _int() { super("integer"); } }
	static class integer extends Type { integer() { super("integer"); } }
	static class Date extends Type { Date() { super("float"); } }
	static class _BigDecimal extends Type { _BigDecimal() { super("float"); } }
}
