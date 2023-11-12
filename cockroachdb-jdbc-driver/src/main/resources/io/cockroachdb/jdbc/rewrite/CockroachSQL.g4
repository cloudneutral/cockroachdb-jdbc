/*
    Simplified ANLTR4 grammar for CockroachDB UPDATE DML statements for the
    purpose of batch array rewrites.
 */
grammar CockroachSQL;

options { caseInsensitive = false; }

// Parser rules

root
  : statement ignore? EOF
  ;

// Only support UPDATEs atm
statement
    : insertStatement
    | upsertStatement
    | updateStatement
    ;

ignore
    : SEMICOLON*
    | COMMENT
    ;

// Insert and upsert  rewrite

insertStatement
    : INSERT INTO tableName columnNames VALUES valueList (COMMA valueList)*
    ;

upsertStatement
    : UPSERT INTO tableName columnNames VALUES valueList (COMMA valueList)*
    ;

columnNames
    : LEFT_PAREN identifier (COMMA identifier)* RIGHT_PAREN
    ;

valueList
    :  LEFT_PAREN atomList RIGHT_PAREN
    ;

atomList
    : atom (COMMA atom)*;

// Update rewrite

updateStatement
    : UPDATE tableName SET setClauseList whereClause
    ;

setClauseList
   : setClause (COMMA setClause)*
   ;

setClause
   : identifier EQUALS atom
   ;

whereClause
    : WHERE expression
    ;

expression
    : NOT expression                                                # notExpression
    | left=expression comparisonOperator right=expression           # comparisonExpression
    | expression logicalOperator expression                         # logicalExpression
    | expression IS NOT? NULL                                       # isNullExpression
    | LEFT_PAREN expression RIGHT_PAREN                             # nestedExpression
    | atom                                                          # atomExpression
    ;

atom
    : literal                                                       # literalAtom
    | identifier                                                    # identifierAtom
    | (MINUS | PLUS)? placeholder                                   # placeholderAtom
    | functionCall                                                  # functionCallAtom
    ;

logicalOperator
    : AND
    | XOR
    | OR
    ;

comparisonOperator
    : EQUALS
    | GT
    | GE
    | LT
    | LE
    | NE
    ;

literal
    : NULL
    | TRUE
    | FALSE
    | (MINUS | PLUS)? INTEGER_LITERAL
    | (MINUS | PLUS)? DECIMAL_LITERAL
    | STRING_LITERAL+
    ;

identifier
    : IDENTIFIER DOT IDENTIFIER
    | IDENTIFIER
    ;

placeholder
    : QUESTION
    ;

functionCall
    : functionName LEFT_PAREN expressionList? RIGHT_PAREN
    ;

functionName
    : name=identifier
    ;

expressionList
	:  expression (COMMA expression)*
	;

tableName
    : name=identifier
    ;


// Lexer rules

INSERT : 'INSERT' | 'insert';
INTO : 'INTO' | 'into';
VALUES : 'VALUES' | 'values';
UPDATE : 'UPDATE' | 'update';
UPSERT : 'UPSERT' | 'upsert';
SET : 'SET' | 'set';
WHERE : 'WHERE' | 'where';
NULL : 'NULL' | 'null';
TRUE : 'TRUE' | 'true';
FALSE : 'FALSE' | 'false';

DOT: '.';
COLON: ':';
SEMICOLON: ';';
COMMENT: '//';
COMMA: ',';
ASTERISK: '*';
LEFT_PAREN: '(';
RIGHT_PAREN: ')';
EQUALS: '=';
MINUS : '-';
PLUS: '+';
GT: '>';
GE: '>=';
LT: '<';
LE: '<=';
NE: '!=';
QUESTION: '?';
NOT : 'NOT' | 'not' | '!';
AND: 'AND' | 'and' | '&&';
XOR: 'XOR' | 'xor' | '^';
OR: 'OR' | 'or' | '||';
IS: 'IS' | 'is';

STRING_LITERAL
    : '\'' ( ~('\''|'\\') | ('\\' .) )* '\''
    | '"' ( ~('"'|'\\') | ('\\' .) )* '"'
    ;

INTEGER_LITERAL
    : DIGIT+
    ;

DECIMAL_LITERAL
    : DECIMAL_DIGITS
    ;

IDENTIFIER
    : LETTER LETTER_OR_DIGIT*
    ;

fragment SIGN
    : [+-]
    ;

fragment DECIMAL_DIGITS
    : SIGN? DIGIT+ ('.' DIGIT+)?
    ;

fragment DIGIT
    : [0-9]
    ;

fragment LETTER
    : [a-zA-Z$_] ;

fragment LETTER_OR_DIGIT
    : [a-zA-Z0-9$_] ;


WS  : [ \r\n\t]+ -> skip ;

