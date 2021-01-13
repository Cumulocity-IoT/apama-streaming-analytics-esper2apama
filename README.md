# Usage
Esper to Apama EPL translation tool
Version 0.2

> e2a.bat [OPTION] ESPER_FILE [APAMA_EPL_FILE]      (on Windows)

> ./e2a.sh [OPTION] ESPER_FILE [APAMA_EPL_FILE]     (on Linux)

You must be running from an Apama command prompt, or have 'java' on the path
from the Java 8+ JRE


If the `APAMA_EPL_FILE` argument is specified then the resulting Apama EPL
will be written to a file in the current working directory with the specified
name.

If the `APAMA_EPL_FILE` argument is not specified, the output file will be 
written in the current working directory with the same name as the esper file,
suffixed with '.mon' - the normal extension for Apama EPL files. For example:

> e2a.bat MyEsper.cep

will create a file named `MyEsper.cep.mon`

Options:
	--ignoreComments	ignore all comments from ESPER_FILE while translating
	-h, --help		display this help and exit


# Disclaimer on use
This tool is not intended to fully translate all possible Esper language
features to Apama EPL. It's intended to translate the most common Esper language
features, either fully or partially, in order to save time in a manual
translation project. The translation is as reliable as is possible, given the
different semantics of Esper and Apama.

For this reason, no guarantees of absolute correctness can be made. Any Apama
EPL output by this tool should always be reviewed by a human, amended if
necessary, and subjected to acceptance testing.

Special attention should be paid to auto-generated notices in the EPL.
* TODO:E2A - Esper code that the tool did not know how to translate.
* WARN:E2A - EPL that was successfully translated, but not with 100% fidelity.

# Known limitations
The following language features are not currently supported by the tool.

* Many built-in annotations
* Create window statement
* Custom expressions
* Select statement with context
* Select statement using a window
* Time literals
* Asc/desc ordering
* SQL-style not-equals operator
* 'null' values
* Lambda functions
* Fragment paths that are not string literals
* Extracting fragments from custom schemas
* Not supporting filters yet
* Cannot translate patterns
* Cannot translate filter expressions in pattern
* Cannot translate custom plug-in observers in pattern
* Schemas defined as aliases to other types
* Multiple inputs to a select statement
* Select output throttling
* Unidirectional keyword
* Contained-event selection
* Event patterns
* Expressions in select without an 'as'

There are many built-in types and functions supported in Esper that are not
supported, but these are too many to enumerate.

# Building the E2A tool yourself
The Esper 2 Apama tool is built using Java 8, [xpybuild](https://github.com/xpybuild/xpybuild) and the [ANTLR 4 Java binaries](https://www.antlr.org/download.html).

To build, you will need to pass the ANTLR 4 location to xpybuild. For example:
```
xpybuild.py ANTLR4_JAR=../antlr-4.8-complete.jar
```
