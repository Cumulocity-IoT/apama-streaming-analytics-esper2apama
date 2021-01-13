@echo off

where java >NUL 2>&1 
if %errorlevel%==0 (
	java -jar "%~dp0\e2a.jar" %*
) else (
	echo Cannot find a Java installation - try running this within an Apama command prompt.
	exit /b 1
)
