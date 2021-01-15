#!/usr/bin/env python
# This is the xpybuild build script for Esper2Apama tool (see https://xpybuild.github.io/xpybuild/ for more information)

# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
#   http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

from xpybuild.propertysupport import *
from xpybuild.targets.custom import CustomCommand
from xpybuild.targets.java import Jar
from xpybuild.pathsets import *
from xpybuild.targets.copy import *
from xpybuild.targets.archive import Unpack, Zip
import os, time

definePropertiesFromFile('build.properties')

# Auto-generated parser/visitor classes based on our Esper antlr grammar
CustomCommand("${OUTPUT_DIR}/e2a/antlr4-output/",
	command=[os.getenv("APAMA_COMMON_JRE") + "/../bin/java", "-jar", "${ANTLR4_JAR}", "-visitor", "-o", "${OUTPUT_DIR}/e2a/antlr4-output/", "src/Esper.g4"],
	dependencies=["src/Esper.g4"],
	cwd=".")

# Unpack all the classes from the antlr runtime, in preparation for embedding in the final jar
Unpack("${OUTPUT_DIR}/e2a/antlr4-runtime-unzipped/", "${ANTLR4_JAR}")

# Single unified jar for the tool, with the antlr runtime embedded in it so there's no classpath faff
Jar("${OUTPUT_DIR}/e2a/e2a.jar",
	compile=[
		FindPaths(DirGeneratedByTarget("${OUTPUT_DIR}/e2a/antlr4-output/"), includes=["**/*.java"]),
		FindPaths("src/", includes=["**/*.java"]),
	],
	manifest={"Main-Class":"com.apama.e2a.Main", "Implementation-Version":"${E2A_VERSION}"},
	package=FindPaths(DirGeneratedByTarget("${OUTPUT_DIR}/e2a/antlr4-runtime-unzipped/"), includes=["**/*.class"]),
	classpath=["${ANTLR4_JAR}"]).option("javac.options", ["-Xlint:all", "-Werror"]).tags("e2a")

# Put our wrapper script next to the jar, exactly as it would be in an installation
Copy("${OUTPUT_DIR}/e2a/e2a.sh", "src/e2a.sh").tags("e2a")
Copy("${OUTPUT_DIR}/e2a/e2a.bat", "src/e2a.bat").tags("e2a")

# Generate the README from a template - using e2a itself to insert the full --help text, and scanning source for the missing features
# Deliberately make sure we're using CRLF even if we're packaging on Linux, so that even Windows Notepad can view it
definePathProperty("E2A_SRC", "./src")
class ReplaceReadme(FileContentsMapper):
	def mapLine(self, context, line):
		if "@HELP_TEXT@" in line:
			help_text = open(PathSet("${OUTPUT_DIR}/e2a/help_text.txt").resolveWithDestinations(context)[0][0], "r").read()
			help_text = help_text.replace(os.linesep, "\r\n")
			return line.replace("@HELP_TEXT@", help_text)
		if "@UNSUPPORTED_FEATURES@" in line:
			outputLines = []
			for (f, _) in FindPaths("${E2A_SRC}/", includes=["*.java"]).resolveWithDestinations(context):
				for srcLine in open(f, "r"):
					m = re.match(".*\.cannotTranslate\([^\"]*\"([^\"]*)\"[,\)]", srcLine)
					if(m):
						outputLines.append("* " + m.group(1))
			return line.replace("@UNSUPPORTED_FEATURES@", "\r\n".join(outputLines))
		return line

	def getDescription(self, context):
		return "ReplaceReadme"
	
	def getDependencies(self):
		return ["${OUTPUT_DIR}/e2a/help_text.txt", FindPaths("src/", includes=["*.java"])]

CustomCommand("${OUTPUT_DIR}/e2a/help_text.txt",
	command=
	["${OUTPUT_DIR}/e2a/e2a.bat", "--help"] if IS_WINDOWS else
	["/bin/bash", "${OUTPUT_DIR}/e2a/e2a.sh", "--help"],
	dependencies=["${OUTPUT_DIR}/e2a/e2a.sh", "${OUTPUT_DIR}/e2a/e2a.jar"],
	redirectStdOutToTarget=True).disableInFullBuild()

FilteredCopy("${OUTPUT_DIR}/e2a/README.md", "./README.tmpl.md",
	[ReplaceReadme()],
).disableInFullBuild()

# Package up the jar, wrapper and documentation for ease of distribution
Zip("${OUTPUT_DIR}/e2a/e2a-tool.zip", AddDestPrefix("e2a-tool/", [
	"${OUTPUT_DIR}/e2a/e2a.jar", "${OUTPUT_DIR}/e2a/e2a.sh", "${OUTPUT_DIR}/e2a/e2a.bat", "CHANGELOG.md", "${OUTPUT_DIR}/e2a/README.md", 
	"LICENSE.txt", "LICENSE.antlr4.txt"])).tags("e2a-package").disableInFullBuild()
