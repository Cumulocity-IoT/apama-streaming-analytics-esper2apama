# Esper-to-Apama EPL translation tool

This is an open-source tool to assist with the task of translating Esper(TM) CEL 
files to the Event Processing Language (EPL) used by Software AG's Apama 
Streaming Analytics platform for use in Cumulocity IoT. 

Software AG ended support for using CEL (Esper) in Cumulocity IoT on 
31 Dec 2020 following its deprecation in 2018. Cumulocity IoT now uses Apama to 
provide streaming analytics. Existing customers using CEL must migrate to 
Apama. For details on migration, refer to Migrating from CEL (Esper) to Apama 
in the [Streaming Analytics guide](https://cumulocity.com/guides/apama/overview-analytics/#migrate-from-esper).

This tool focuses on reducing (but not eliminating!) the amount of human 
involvement needed during migration by automating translation of some of the 
most commonly occurring Esper constructs. There are many Esper language 
features that the tool does not attempt to translate automatically or which 
require human checking, and comments are added to the generated Apama EPL file 
to flag these. See the disclaimer section below for more details. 

The tool will generate EPL that works as of version 10.6.6 of the Apama microservice.

# Getting started

1. Download the binaries from [releases](https://github.com/SoftwareAG/apama-streaming-analytics-esper2apama/releases) 
   and unpack the zip.
2. Open an Apama command prompt/shell. Alternatively if you don't have Apama 
   installed, ensure you have the `java` executable from Java 8 on your `PATH`. 
3. Run the tool using the provided `e2a` script, passing it the path to the 
   Esper `.cep` file to be translated. See the command line section below for additional command line options. 

## Help and resources
During migration you will want to refer to:
- The [Apama documentation](https://www.apamacommunity.com/docs), especially 
  the *User Guide* which explains the Apama EPL language itself, and 
  the *API Reference for EPL* which is where you'll find reference information 
  about the event definitions used for interacting with Cumulocity IoT from EPL. 
- The [Apama in Cumulocity documentation](https://cumulocity.com/guides/apama/overview-analytics/), 
  including the [migration guide](https://cumulocity.com/guides/apama/overview-analytics/#migrate-from-esper).

If you...
- Have a question: ask it on the [Apama community on Stack Overflow](https://stackoverflow.com/questions/ask?tags=apama)
- Have a bug or feature request: create an [issue on our GitHub project](https://github.com/SoftwareAG/apama-streaming-analytics-esper2apama/issues)

## Command line usage
@HELP_TEXT@

# Disclaimer on use
This tool is not intended to fully translate all possible Esper language
features to Apama EPL. It's intended to translate the most common Esper language
features, either fully or partially, in order to save time in a manual
translation project. The translation is as reliable as is possible, given the
different semantics of Esper and Apama.

For this reason, no guarantees of absolute correctness can be made. Any Apama
EPL output by this tool should always be reviewed by a human, amended if
necessary, and subjected to acceptance testing.

Special attention should be paid to auto-generated notices in the EPL with the 
following prefixes:
* `TODO:E2A` - Esper code that the tool did not know how to translate.
* `WARN:E2A` - Esper code that was successfully translated to EPL, but not with 100% fidelity.

## Known limitations
The following language features are not currently supported by the tool.

@UNSUPPORTED_FEATURES@

There are many built-in types and functions supported in Esper that are not
supported, but these are too many to enumerate.

# Building the E2A tool yourself
The Esper 2 Apama tool is built using Java 8, [xpybuild](https://github.com/xpybuild/xpybuild) 
and the [ANTLR 4 Java binaries](https://www.antlr.org/download.html).

To build, you will need to pass the ANTLR 4 location to xpybuild. For example:
```
xpybuild.py ANTLR4_JAR=../antlr-4.8-complete.jar
```

# License

Copyright (c) 2020-2021 Software AG, Darmstadt, Germany and/or its licensors

This tool is provided as-is and without warranty or support. It does not 
constitute part of the Software AG product suite. 

Licensed under the Apache License, Version 2.0 (the "License"); you may not use 
this file except in compliance with the License. You may obtain a copy of the 
License at http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed 
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and limitations 
under the License.

For reference, the LICENSE.txt file contains a full copy of the Apache 2.0 
license. 
