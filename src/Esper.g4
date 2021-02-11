grammar Esper;

@header {
	package com.apama.e2a;
}

@lexer::members {
	boolean lastTokenWasOnSameLine = false;
}

options {
	language=Java;
}

identifier
	: IDENTIFIER { EPLOutput.escapeEsperIdentifier($IDENTIFIER); }
	| 'last'
	| 'context'
	| 'events'
	| 'type'
	| 'fragments'
	| 'min'
	;

esperFile
	: moduleDecl? statement* EOF
	;

moduleDecl
	: 'module'  moduleName = identifier (';'+)
	;

createVariable: statementAnnotation* ('create'|'CREATE') constant='constant'? ('variable'|'VARIABLE') type=typeName name=identifier ('=' expr)?;

createExpression: statementAnnotation* 'create'? 'expression' (returnType=typeName)? ('js:')? name=identifier LPAREN (identifier (',' identifier)*)? RPAREN createExpressionBody;

createContext: statementAnnotation* 'create' 'context' name=identifier createContextBody;

createContextBody
	: 'partition' 'by'? expr (('and'|',') expr)* 'from' identifier
	| ('context' identifier createContextBody) (',' 'context' identifier createContextBody)*
	| 'start' LPAREN expr (',' (expr))* RPAREN 'end' LPAREN expr (',' (expr))* RPAREN
	;

// This is not actually a proper parsing of the body of an expression, Javascript or otherwise. It's just sufficient to recognise an expression - balanced brackets of all kinds, surrounding arbitrary tokens
createExpressionBody
	: LPAREN createExpressionBody* RPAREN
	| LBRACKET createExpressionBody* RBRACKET
	| '{' createExpressionBody* '}'
	| ~(LPAREN|RPAREN|LBRACKET|RBRACKET|'{'|'}')
	| '&'|'\\'
	;
	
schemaDecl
	: ('CREATE'|'create') ('SCHEMA'|'schema'|'Schema') schemaName=identifier (
		('(' ((fieldNames+=identifier fieldTypes+=typeName) (',' fieldNames+=identifier fieldTypes+=typeName)*)? ')')|
		('as' typedef=typeName))
	;

typeName: identifier ('.' identifier)* ('[' ']')?;

statement
	: statementAnnotation*
		(schemaDecl|
		createVariable|
		createExpression|
		createContext|
		createWindow|
		selectClause|
		onSet)
		';'*
	;

contextStatement:
	('context'|'CONTEXT') identifier;

createWindow:
	('create'|'CREATE') ('window'|'WINDOW') windowname = identifier (windowSpecifier)+  windowmodifier=winmodifier?
		'as'?  alias = identifier;

winmodifier: 'retain-intersection';

// From http://esper.espertech.com/release-6.0.1/esper-reference/html/epl_clauses.html#epl-syntax-annotation
// there are a number of built in annotations (see 5.2.7.3 Built-In Statement Annotations)
// the statement can take various forms 
// @ident 
// @ident ()
// @ident ( literal )
// @ident ( literal , ...)
// @ident ( param_ident = literal | identifier, ...)

//optional params list - bare @ident = valid
statementAnnotation:
	'@' identifier (
		'(' (statementAnnotationAttribute)? (',' statementAnnotationAttribute )* ')')?;

//optional named param
statementAnnotationAttribute: (param_name = identifier '=')? expr;

insertTo: (('insert'|'INSERT') ('INTO'|'into')  output=identifier);

insertInput
	: inputStream=identifier (filter=arguments|containedEventSelection)? windowSpecifier*?  (('as'|'AS')? coassignee=identifier)? unidirectional='unidirectional'?
	| ('pattern'|'PATTERN') LBRACKET pattern RBRACKET unidirectional='unidirectional'?
	;

// See section 5.2 of the Esper docs (v5.4.0)
selectClause
	: 
		context=contextStatement?
		insertTo?
		('SELECT'|'select')  (
			(selects+=selectColumnExpr (','  selects+=selectColumnExpr  )*)
		)
		('from'|'FROM') insertInput (',' insertInput)*
		havingClause?
		whereClause?
		insertStatementOutputThrottling?
	;

whereClause:
	('where'|'WHERE'|'Where') condition=expr;


havingClause: 'having' expr;

insertStatementOutputThrottling: 'output' 'last'? ('every' expr|'when' 'terminated');

pattern: 
	every=('every'|'EVERY') pattern
	| not=('not'|'NOT') pattern
	| pattern operator=('and'|'AND'|'or'|'OR'|'->') pattern
	| 'timer' ':' 'at' timerAtArgs=arguments
	| 'timer' ':' 'interval' LPAREN timerIntervalArgs=timePeriod RPAREN
	| 'timer' ':' 'within' LPAREN timerWithinArgs=timePeriod RPAREN //TODO
	| (coassignee=identifier '=')? eventFilter=identifier arguments?
	| LPAREN enclosed=pattern RPAREN
	| LBRACKET INTEGER RBRACKET pattern
	| pattern ('until'|'UNTIL') pattern
	| distinct=('every-distinct'|'EVERY-DISTINCT') LPAREN expr (',' expr)* (',' timePeriod)? RPAREN (coassignee=identifier '=')? identifier
	| boundedRange pattern until=('until'|'UNTIL') pattern
	| pattern '-' LBRACKET limitExpression=INTEGER RBRACKET '>' pattern
	| pattern 'where' whereGuard=pattern
	| pattern whileGuard='while' LPAREN expr RPAREN
	| identifier ':' identifier customObserverArgs=arguments 
	;

timePeriod: (amounts+=expr units+=timeUnit)+ | amount=expr;

boundedRange: 
	LBRACKET (lowEndpoint=INTEGER? ':') highEndpoint=INTEGER RBRACKET
	| LBRACKET lowEndpoint=INTEGER (':' highEndpoint=INTEGER?) RBRACKET
	;

containedEventSelection: '[' expr ('@' 'type' '(' typeName ')')? ']';

onSet: 'on' onSetInput 'set' (identifier '=' expr (',' identifier '=' expr)*);

onSetInput
    : typeName (filter=arguments)? (('as'|'AS')? coassignee=identifier)?
    | ('pattern'|'PATTERN') LBRACKET pattern RBRACKET
    ;

arguments: LPAREN (expr (',' expr)*)? RPAREN;

windowSpecifier:
	'.' 'win:length' windowargs
	| '.' 'win:length_batch' windowargs
	| '.' 'stat:linest' windowargs
	| '.' 'win:time' windowargs
	| '.' 'std:firstunique' windowargs;

windowargs:
	(LBRACKET | LPAREN) (','?  identifier)*? (
		RBRACKET
		| RPAREN
	)
	| (LBRACKET | LPAREN) timePeriod (RBRACKET | RPAREN)
	;
	
selectColumnExpr
	: fragments ('AS'|'as') 'fragments'
	| expr (('AS'|'as') identifier)?
	;

expr
	: literal
	| expr operator=('+'|'-'|'/'|'*'|'%'|'||'|'<>'|'regexp'|'in'|'IN'|'like') expr 
	| expr comparisonOperator=('<='|'>='|'!='|'is not'|'IS NOT'|'='|'>'|'<'|'is'|'IS') expr 
	| expr booleanOperator=('and'|'AND'|'or'|'OR') expr 
	| operator=('not'|'NOT') expr
	| LPAREN enclosed=expr RPAREN
	| memberLookup
	| exprUnsupported
	| array
	| dictionary
	| functionCall
	| objectForMemberCall=expr '.' functionCall
	| expr timeUnit // An 'inline' of timePeriod - unfortunately the only way to avoid mutual left-recursion of rules, forbidden in ANTLR4
	| expr ordering=('asc'|'desc')
	;

exprUnsupported
	: case_
	| '*'
	| 'new' typeName arguments
	| LPAREN (setMembers+=expr (',' setMembers+=expr)+) RPAREN
	| lambda
	| '[' (rangeMembers+=expr (',' rangeMembers+=expr)+) ']'
	;

case_: ('case'|'CASE') ('when'|'WHEN') expr ('then'|'THEN') expr ('else'|'ELSE') expr ('end'|'END');

lambda: identifier '=>' expr;

literal
	: stringLiteral
	| ('-')? FLOAT
	| ('-')? LONG
	| ('-')? DOUBLE
	| ('-')? INTEGER
	| booleanLiteral
	| 'null'
	;

booleanLiteral: 'True'|'true'|'False'|'false';

timeUnit: 
	'milliseconds'|'millisecond'|'msec'
	|'seconds'|'second'|'sec'
	|'minutes'|'minute'|'min'
	|'hours'|'hour'
	|'day'|'days'
	|'weeks'|'week'
	|'months'|'month'
	|'years'|'year' 
	|'events'
	;

functionCall: memberLookup arguments;

memberLookup
	: identifier ('.' identifier)*
	;

fragments
	: '{' (( kvPairs+=fragKeyValuePair ) ( ',' kvPairs+=fragKeyValuePair )*)? '}'
	;

fragKeyValuePair
	: key=stringLiteral ',' value=expr
	;

array
	: '{' (expr (',' expr)*)? '}'
	;

dictionary
	: '{' keys+=literal ':' values+=expr (',' keys+=literal ':' values+=expr)*'}'
	;

stringLiteral : STRING | STRING_SINGLE_QUOTES;

fragment LETTER : 'a'..'z'|'A'..'Z';
fragment IDCHAR : LETTER | '_' | '$'; // Same as Java

fragment DIGIT    : '0'..'9';
fragment HEXDIGIT : 'a'..'f' | 'A'..'F' | DIGIT;

fragment EXPONENT : ('e' | 'E') ('+' | '-')? (DIGIT)+;
fragment WHOLENUM : (DIGIT)+;

IDENTIFIER
	: IDCHAR (IDCHAR | DIGIT)*
	;

INTEGER
	// Hex (leading 0x)
	: '0' 'x' (HEXDIGIT)+
	// Decimal integer
	| WHOLENUM
	;

DOUBLE
	// Integer part, decimal point, exponent
	: WHOLENUM '.' EXPONENT
	// Integer part, decimal point, fractional part, optional exponent
	| WHOLENUM '.' WHOLENUM (EXPONENT)?
	// fractional part, optional exponent
	| '.' WHOLENUM (EXPONENT)?
	// Integer part, exponent
	| WHOLENUM EXPONENT
	;

FLOAT: DOUBLE 'f';

LONG: INTEGER 'l';

DECIMAL
	// Integer part, decimal point, optional exponent
	: WHOLENUM '.' (EXPONENT)? 'd'
	// Integer part, decimal point, fractional part, optional exponent
	| WHOLENUM '.' WHOLENUM (EXPONENT)? 'd'
	// fractional part, optional exponent
	| '.' WHOLENUM (EXPONENT)? 'd'
	// Integer part, exponent
	| WHOLENUM EXPONENT 'd'
	;

STRING
	: '"' (ESC|~('"'))*? '"';
  
STRING_SINGLE_QUOTES
	: '\'' (ESC | ~('\''))*? '\'';

fragment ESC
	: '\\' ( '"' | '\\' | 'n' | 't' | 'r' )
	;

WS  
	: (NON_UNICODE_WHITESPACE | UNICODE_WHITESPACE) -> skip
	;
  
// Whitespace that isn't categorized as such by Unicode - this is basically what Java adds
fragment NON_UNICODE_WHITESPACE
	: '\t'
	| '\u000b'    // Vertical tab   (octal '013')
	| '\f'      // Form feed
	| '\r'      // Carriage return
	| '\u001c'    // File separator (octal '\034')
	| '\u001d'    // Group separator  (octal '\035')
	| '\u001e'    // Record separator (octal '\036')
	| '\u001f'    // Unit separator (octal '\037')
	;

// Unicode whitespace as literal unicode codepoints; Java is (mostly) native unicode, so we don't need to do the UTF8 byte-literal matching
// that we do in the (byte-based) correlator lexer
fragment UNICODE_WHITESPACE
	// Zs
	: '\u0020' | '\u00a0' | '\u1680' | '\u180e' | '\u2000' | '\u2001' | '\u2002' | '\u2003' | '\u2004' | '\u2005' | '\u2006' | '\u2007' |
	  '\u2008' | '\u2009' | '\u200a' | '\u202f' | '\u205f' | '\u3000'
	// Zl
	| '\u2028'
	// Zp
	| '\u2029'
	;

NEWLINE:
	'\n' {
		lastTokenWasOnSameLine=false;
		skip();
	}
	;

LPAREN: '(';

RPAREN: ')';

LBRACKET: '['; 

RBRACKET: ']';

ASTERISK: '*';

COMMENT: '//' (~('\n'))* -> channel(HIDDEN);

ML_COMMENT: '/*' .*? '*/'-> channel(HIDDEN);
