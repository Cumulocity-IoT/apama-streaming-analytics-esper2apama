# Version 0.2 - 13/01/2020
* PAB-1989 - Documentation comments in Esper are present in the translation output
* PAB-1987 - Can use SendEmail/SendSms output streams
* PAB-1997 - Translate primitive types BigDecimal and object id
* PAB-1988 - Non-ASCII unicode characters should not get mangled during translation or injection
* PAB-2020 - Esper can still be translated if it uses Apama keywords as identifiers

# Version 0.1 - 14/12/2020
* Fixed a listener leak in the FindManagedObjectById implementation
* Miscellaneous formatting changes to the generated EPL
* Fixed a bug in fragment translation where non-literal measurement values were not being translated correctly
* Support for translating 'int' - yet another spelling of 'integer'
* PAB-1986 - Translate 'on ... set' statements
* PAB-1976 - Translate global variables
* PAB-1912 - Translate the 'getNumber' function
* PAB-1975 - Where clauses are sometimes translated into more efficient event expressions

# Early prototype - 02/12/2020

# Early prototype - 24/11/2020
