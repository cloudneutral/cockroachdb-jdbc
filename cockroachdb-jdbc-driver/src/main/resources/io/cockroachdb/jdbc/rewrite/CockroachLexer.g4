/*
    Simplified ANLTR4 grammar for CockroachDB DML statements for the
    purpose of batch array rewrites.
 */
lexer grammar CockroachLexer;

options { caseInsensitive = true; }

// Lexer rules

INSERT : 'INSERT';
INTO : 'INTO';
VALUES : 'VALUES';
UPDATE : 'UPDATE';
UPSERT : 'UPSERT';
SET : 'SET';
WHERE : 'WHERE';
NULL : 'NULL';
TRUE : 'TRUE';
FALSE : 'FALSE';
ON: 'ON';
CONFLICT: 'CONFLICT';
DO: 'DO';
NOTHING: 'NOTHING';
CONSTRAINT: 'CONSTRAINT';

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
NOT : 'NOT' | '!';
AND: 'AND' | '&&';
XOR: 'XOR' | '^';
OR: 'OR' | '||';
IS: 'IS';

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

