/**
 * Copyright (C) 2016 Adenops Consultants Informatique Inc.
 *
 * This file is part of the Moustack project, see http://www.moustack.org for
 * more information.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adenops.moustack.lib.argsparser;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.adenops.moustack.lib.argsparser.annotation.Argument;
import com.adenops.moustack.lib.argsparser.annotation.Argument.Type;
import com.adenops.moustack.lib.argsparser.annotation.PositionalArgument;
import com.adenops.moustack.lib.argsparser.exception.ParserException;
import com.adenops.moustack.lib.argsparser.exception.ParserInternalException;

/**
 * This is a very messy but handle all our cases nicely.
 *
 * TODO:
 * - refactoring, cleanup multiples lists of arguments
 * - how to handle default values set with properties???
 * - add regex validation support
 */
public class ArgumentsParser {
	private static final int FIRST_COLUMN_WITH = 30;
	private static final int SECOND_COLUMN_WITH = 70;

	private final Class clazz;
	private final String name;
	private final String command;
	private final String version;
	private final String helpHeader;
	private final String helpFooter;

	private String configurationFile;

	private final Map<String, Method> shortArgsMethods = new HashMap<>();
	private final Map<String, Method> longArgsMethods = new HashMap<>();
	private Method positionalArgMethod;

	private final List<Argument> nonPositionalArguments = new ArrayList<>();
	private PositionalArgument positionalArgument;

	private class ParsedArgument {
		private final Method pojoMethod;
		private final Class argumentClass;
		private final String value;

		public ParsedArgument(Method pojoMethod, Class argumentClass, String value) {
			this.pojoMethod = pojoMethod;
			this.argumentClass = argumentClass;
			this.value = value;
		}

		public Method getPojoMethod() {
			return pojoMethod;
		}

		public Class getArgumentClass() {
			return argumentClass;
		}

		public String getValue() {
			return value;
		}
	}

	public ArgumentsParser(String name, String command, String version, String helpHeader, String helpFooter,
			Class clazz) {
		this.name = name;
		this.command = command;
		this.version = version;
		this.clazz = clazz;
		this.helpHeader = helpHeader;
		this.helpFooter = helpFooter;
	}

	private boolean isNull(String string) {
		return string == null || string.isEmpty();
	}

	private String blank(int size) {
		return new String(new char[size]).replace('\0', ' ');
	}

	private String substringSafe(String string, int beginIndex, int endIndex) {
		if (isNull(string))
			return string;

		int begin = Math.min(beginIndex, string.length());
		int end = Math.min(endIndex, string.length());

		return string.substring(begin, end);
	}

	private String substringSafe(String string, int beginIndex) {
		if (isNull(string))
			return string;

		int begin = Math.min(beginIndex, string.length());

		return string.substring(begin);
	}

	private StringBuffer formatText(String text, int width, int padding) {
		return formatText(text, width, padding, padding);
	}

	private StringBuffer formatText(String text, int width, int firstLinePadding, int padding) {
		StringBuffer sb = new StringBuffer();

		if (isNull(text))
			return sb;

		// padding for first line should be at least as great as normal padding
		if (padding > firstLinePadding)
			firstLinePadding = padding;

		// insert first line padding
		if (firstLinePadding > 0)
			sb.append(blank(firstLinePadding - padding));

		String line = substringSafe(text, 0, width - (firstLinePadding - padding));
		do {
			// remove the line we print from the description
			text = substringSafe(text, line.length());
			sb.append(line.trim());
			sb.append("\n");

			if (text.isEmpty())
				break;

			// add the padding
			if (padding > 0)
				sb.append(blank(padding));

		} while (!isNull(line = substringSafe(text, 0, width)));

		return sb;
	}

	private String join(String delimiter, String str1, String str2) {
		if (isNull(str1))
			return str2;
		if (isNull(str2))
			return str1;
		return String.join(delimiter, str1, str2);
	}

	private String placeHolder(Argument argument) {
		if (isNull(argument.placeholder()))
			return argument.clazz().getSimpleName().toUpperCase();
		else
			return argument.placeholder();
	}

	private StringBuffer formatArgument(Argument argument) {
		String shortarg = argument.shortarg();
		String longarg = argument.longarg();

		StringBuffer sb = new StringBuffer("  ");

		// append arguments
		sb.append(join(", ", shortarg, longarg));

		// append the placeholder for generic type
		if (argument.type().equals(Argument.Type.GENERIC)) {
			sb.append(" ");
			sb.append(placeHolder(argument));
		}

		// fill with space until the description
		int spaces = FIRST_COLUMN_WITH - sb.length();
		if (spaces > 0)
			sb.append(blank(spaces));
		else {
			// if it is too long, start on the next line
			sb.append("\n");
			sb.append(blank(FIRST_COLUMN_WITH));
		}

		// now the description
		String description = argument.description();
		if (argument.mandatory())
			description = "MANDATORY. " + description;
		sb.append(formatText(description, SECOND_COLUMN_WITH, FIRST_COLUMN_WITH));

		return sb;
	}

	private StringBuffer formatPositionalArgument(PositionalArgument argument) {
		StringBuffer sb = new StringBuffer(argument.placeholder());
		sb.append(":\n");

		String description = argument.description();
		if (argument.mandatory())
			description = "MANDATORY. " + description;
		sb.append(formatText(description, FIRST_COLUMN_WITH + SECOND_COLUMN_WITH, 4, 0));

		return sb;
	}

	private void help() {
		StringBuffer sb = new StringBuffer("Usage: ");
		sb.append(command);
		sb.append(" [OPTIONS]");
		if (positionalArgument != null) {
			sb.append(" ");
			sb.append(positionalArgument.placeholder());
		}
		sb.append("\n\n");

		sb.append(formatText(helpHeader, FIRST_COLUMN_WITH + SECOND_COLUMN_WITH, 0));

		sb.append("\nOPTIONS:\n");
		for (Argument argument : nonPositionalArguments)
			sb.append(formatArgument(argument));
		sb.append("\n");
		if (positionalArgument != null) {
			sb.append(formatPositionalArgument(positionalArgument));
			sb.append("\n");
		}

		sb.append(formatText(helpFooter, FIRST_COLUMN_WITH + SECOND_COLUMN_WITH, 0));

		System.out.println(sb);
	}

	private void helpConfiguration() {
		StringBuffer sb = new StringBuffer("Sample configuration: \n\n");
		sb.append("###########################\n");
		for (Argument argument : nonPositionalArguments) {
			if (isNull(argument.property()))
				continue;

			if (!isNull(argument.description())) {
				sb.append("# ");
				sb.append(argument.description());
			} else
				sb.append("# no description");
			sb.append("\n");

			sb.append(argument.property());
			sb.append("=");
			if (!isNull(argument.defaultvalue()))
				sb.append(argument.defaultvalue());

			sb.append("\n");
		}
		sb.append("###########################");
		System.out.println(sb);
	}

	private void version() {
		StringBuffer sb = new StringBuffer(name);
		sb.append(" version ");
		sb.append(version);
		System.out.println(sb);
	}

	private Object cast(Class clazz, String value) throws ParserException {
		if (value == null)
			return null;

		try {
			// if it's an enum
			if (clazz.isEnum())
				return Enum.valueOf(clazz, value);

			// if by chance it is the same class
			if (value.getClass().equals(clazz))
				return value;

			// if the class has a valueof method
			try {
				Method valueOf = clazz.getDeclaredMethod("valueOf", String.class);
				return valueOf.invoke(clazz, value);
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
			}

			// last resort, try the simple cast
			return clazz.cast(value);
		} catch (ClassCastException | IllegalArgumentException e) {
			throw new ParserException("value " + value + " cannot be cast to " + clazz.getName());
		}
	}

	private void invoke(Method method, Object target, Object parameter) throws ParserException {
		try {
			method.invoke(target, parameter);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			Class<?>[] paramTypes = method.getParameterTypes();
			String expected = paramTypes.length == 1 ? paramTypes[0].getName() : "[invalid]";

			throw new ParserException("error while invoking method" + method.getName() + "(" + expected
					+ ") with parameter " + parameter);
		}
	}

	private List<ParsedArgument> parseArguments(String[] args, List<Object> requiredArguments)
			throws ParserException, ParserInternalException {
		// where we store everything we need to set values after the arguments parsing
		List<ParsedArgument> parsedArguments = new ArrayList();

		// convert args to queue for easier manipulation
		LinkedList<String> argsQueue = new LinkedList<>(Arrays.asList(args));

		// iterate over all arguments
		String arg;
		while ((arg = argsQueue.poll()) != null) {
			Method method = null;

			if (arg.startsWith("--"))
				method = longArgsMethods.get(arg);

			else if (arg.startsWith("-"))
				method = shortArgsMethods.get(arg);

			else {
				// positional argument, there won't be any more non-positional arguments
				if (positionalArgument == null)
					throw new ParserException("received positional argument but none declared");

				if (!argsQueue.isEmpty())
					throw new ParserException("invalid arguments after positional argument " + arg);

				parsedArguments.add(new ParsedArgument(positionalArgMethod, positionalArgument.clazz(), arg));
				requiredArguments.remove(positionalArgMethod);
				break;
			}

			// if method is null, we didn't find the argument
			if (method == null)
				throw new ParserException("argument " + arg + " not allowed");

			Argument argument = method.getAnnotation(Argument.class);

			switch (argument.type()) {
			case FLAG:
				parsedArguments.add(new ParsedArgument(method, Boolean.class, "true"));
				break;
			case GENERIC:
				// we get the argument value
				String value = argsQueue.poll();

				if (isNull(value))
					throw new ParserException("value not found for argument " + arg);

				parsedArguments.add(new ParsedArgument(method, argument.clazz(), value));
				requiredArguments.remove(method.getAnnotation(Argument.class));
				break;
			case CONFIGURATION:
				configurationFile = argsQueue.poll();
				if (isNull(configurationFile))
					throw new ParserException("configuration value not found for argument " + arg);
				break;
			case VERSION:
				version();
				return null;
			case HELP:
				help();
				return null;
			case HELP_CONFIGURATION:
				helpConfiguration();
				return null;
			default:
				throw new ParserInternalException("should not happen");
			}
		}

		return parsedArguments;
	}

	private void setDefaultValues(Object pojo) throws ParserException {
		for (Method method : clazz.getMethods()) {
			if (!method.isAnnotationPresent(Argument.class))
				continue;

			Argument annotation = method.getAnnotation(Argument.class);

			if (annotation.type().equals(Type.CONFIGURATION) && isNull(configurationFile)) {
				configurationFile = annotation.defaultvalue();
				continue;
			}

			if (!annotation.type().equals(Type.GENERIC) || (annotation.defaultvalue() == null))
				continue;

			invoke(method, pojo, cast(annotation.clazz(), annotation.defaultvalue()));
		}
	}

	private void setConfigurationValues(Object pojo, List<Object> requiredArguments)
			throws ParserException, ParserInternalException {
		if (configurationFile == null)
			throw new ParserInternalException("configuration file is null, this should not happen");

		try {
			Properties properties = new Properties();
			properties.load(new FileInputStream(configurationFile));

			for (Method method : clazz.getMethods()) {
				if (!method.isAnnotationPresent(Argument.class))
					continue;

				Argument annotation = method.getAnnotation(Argument.class);
				if (!annotation.type().equals(Type.GENERIC) || isNull(annotation.property()))
					continue;

				String configValue = properties.getProperty(annotation.property());
				if (configValue != null) {
					invoke(method, pojo, cast(annotation.clazz(), configValue));
					requiredArguments.remove(annotation);
				}
			}

		} catch (IOException e) {
			System.out.println("cannot load configuration file: " + e.getMessage());
		}
	}

	private void parseAnnotations() throws ParserInternalException {
		List<Argument> arguments = new ArrayList<>();

		// we parse the class and extract the information we will need
		for (Method method : clazz.getMethods()) {

			// try first to handle positional argument
			if (method.isAnnotationPresent(PositionalArgument.class)) {
				if (positionalArgument != null)
					throw new ParserInternalException("there must be at most one positional argument");

				positionalArgument = method.getAnnotation(PositionalArgument.class);
				positionalArgMethod = method;

				// basic validation of annotations declarations
				validateArgument(positionalArgument);

				continue;
			}

			if (!method.isAnnotationPresent(Argument.class))
				continue;

			Argument argument = method.getAnnotation(Argument.class);

			// basic validation of annotations declarations
			validateArgument(argument);

			// add to the global arguments list
			arguments.add(argument);

			// also add to the list dedicated to non-positional arguments
			nonPositionalArguments.add(argument);

			String shortarg = argument.shortarg();
			String longarg = argument.longarg();

			if (!isNull(shortarg)) {
				validateShortArg(shortarg);
				shortArgsMethods.put(shortarg, method);
			}

			if (!isNull(longarg)) {
				validateLongArg(longarg);
				longArgsMethods.put(longarg, method);
			}
		}
	}

	private Object instantiate(Class pojoClass) throws ParserInternalException {
		// first try to handle as a singleton
		try {
			return clazz.getMethod("getInstance").invoke(null);
		} catch (Exception e) {
		}

		// fallback to default constructor
		try {
			return clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new ParserInternalException("cannot instanciate the class " + clazz, e);
		}
	}

	public Object parse(String[] args) throws ParserInternalException {
		// first parse annotation to extract the information we need
		parseAnnotations();

		// track required arguments
		List<Object> requiredArguments = new ArrayList<>();
		for (Argument argument : nonPositionalArguments) {
			if (argument.mandatory())
				requiredArguments.add(argument);
		}
		if (positionalArgument != null && positionalArgument.mandatory())
			requiredArguments.add(positionalArgument);

		// instantiate the target object
		Object pojo = instantiate(clazz);

		try {
			// parse command line arguments
			List<ParsedArgument> parsedArguments = parseArguments(args, requiredArguments);

			// if we didn't received parsed arguments, this means a display action has been triggered
			// (version, help, ...)
			if (parsedArguments == null)
				return null;

			// set default values (from annotations, defaultValue)
			setDefaultValues(pojo);

			// set values from configuration file
			setConfigurationValues(pojo, requiredArguments);

			// set values from arguments
			for (ParsedArgument parsedArgument : parsedArguments)
				invoke(parsedArgument.getPojoMethod(), pojo,
						cast(parsedArgument.getArgumentClass(), parsedArgument.getValue()));

			// check if required arguments are present
			if (!requiredArguments.isEmpty()) {
				StringBuffer sb = new StringBuffer();
				for (Object object : requiredArguments) {
					if (sb.length() > 0)
						sb.append(", ");
					if (object instanceof Argument) {
						Argument argument = (Argument) object;
						sb.append(join("/", argument.shortarg(), argument.longarg()));

					} else if (object instanceof PositionalArgument)
						sb.append(((PositionalArgument) object).placeholder());
					else
						throw new ParserInternalException("should not happen");
				}
				sb.insert(0, "some mandatory arguments are missing: ");
				throw new ParserException(sb.toString());
			}

			return pojo;

		} catch (ParserException e) {
			// if we got an error from parsing, display help
			help();

			// then display error message if possible
			if (!isNull(e.getMessage()))
				System.err.println("ERROR: " + e.getMessage());

			return null;
		}
	}

	private void validateArgument(PositionalArgument argument) throws ParserInternalException {
		validateClass(argument.clazz());
	}

	private void validateArgument(Argument argument) throws ParserInternalException {
		switch (argument.type()) {
		case GENERIC:
			validateClass(argument.clazz());
			if (isNull(argument.shortarg()) && isNull(argument.shortarg()) && isNull(argument.property()))
				throw new ParserInternalException(
						"argument of type " + argument.type() + " must have shortarg, longarg or property defined");
			if (!isNull(argument.defaultvalue()) && argument.mandatory())
				throw new ParserInternalException(
						"argument of type " + argument.type() + " cannot have a default value and be mandatory");
			break;
		case CONFIGURATION:
			if (isNull(argument.shortarg()) && isNull(argument.shortarg()))
				throw new ParserInternalException("configuration argument must have shortarg or defined");
			if (isNull(argument.defaultvalue()))
				throw new ParserInternalException("configuration argument must have a default value defined");
			break;
		case FLAG:
		case HELP:
		case VERSION:
			if (!isNull(argument.defaultvalue()))
				throw new ParserInternalException(
						"argument of type " + argument.type() + " should not have a default value");
			if (argument.mandatory())
				throw new ParserInternalException("argument of type " + argument.type() + " should not be required");
			break;
		}
	}

	private void validateClass(Class clazz) throws ParserInternalException {
		if (clazz == null)
			throw new ParserInternalException("clazz property cannot be null");
		if (!clazz.isAssignableFrom(String.class) && !clazz.isAssignableFrom(Short.class)
				&& !clazz.isAssignableFrom(Integer.class) && !clazz.isAssignableFrom(Long.class) && !clazz.isEnum())
			throw new ParserInternalException("clazz " + clazz + " not allowed");
	}

	private void validateLongArg(String longarg) throws ParserInternalException {
		if (!longarg.startsWith("--"))
			throw new ParserInternalException("longarg should start with '--'");
	}

	private void validateShortArg(String shortarg) throws ParserInternalException {
		if (!shortarg.startsWith("-"))
			throw new ParserInternalException("shortarg should start with '-'");
	}
}
