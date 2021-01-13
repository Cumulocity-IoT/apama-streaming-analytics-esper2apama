IFS=$'\n'

# Directory this script is in
DIRNAME=$(dirname "$0")

if which java > /dev/null; then
	# Execute e2a.jar that's in the same directory as this script, passing all arguments through
	java -jar $DIRNAME/e2a.jar $@
else
	echo "Cannot find a Java installation - try running this within an Apama command prompt"
	exit 1
fi
