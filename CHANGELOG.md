# Version 0.3 - 2020-02-11
* PAB-2037 - Patterns that contain a single timer:interval
* PAB-2039 - Translate the findOneManagedObjectByType function
* PAB-1998 - Do a low-effort translation of the Esper 'cast' function
* PAB-2038 - Translate the findFirstAlarmBySourceAndStatusAndType function

# Version 0.2 - 2020-01-13
* PAB-1989 - Documentation comments in Esper are present in the translation output
* PAB-1987 - Can use SendEmail/SendSms output streams
* PAB-1997 - Translate primitive types BigDecimal and object id
* PAB-1988 - Non-ASCII unicode characters should not get mangled during translation or injection
* PAB-2020 - Esper can still be translated if it uses Apama keywords as identifiers

# Version 0.1 - 2020-12-14
* Fixed a listener leak in the FindManagedObjectById implementation
* Miscellaneous formatting changes to the generated EPL
* Fixed a bug in fragment translation where non-literal measurement values were not being translated correctly
* Support for translating 'int' - yet another spelling of 'integer'
* PAB-1986 - Translate 'on ... set' statements
* PAB-1976 - Translate global variables
* PAB-1912 - Translate the 'getNumber' function
* PAB-1975 - Where clauses are sometimes translated into more efficient event expressions

# Early prototype - 2020-12-02

# Early prototype - 2020-11-24
