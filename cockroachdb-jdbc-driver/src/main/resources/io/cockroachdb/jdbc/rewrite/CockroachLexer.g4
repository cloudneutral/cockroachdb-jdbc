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
TYPE_CAST: '::';
SEMICOLON: ';';
COMMENT: '//';
COMMA: ',';
ASTERISK: '*';
LEFT_PAREN: '(';
RIGHT_PAREN: ')';
EQUALS: '=';
MINUS : '-';
PLUS: '+';
DIV: '/';
MOD: '%';
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

DECIMAL_LITERAL
    : DECIMAL_DIGIT+
    ;

FLOAT_LITERAL
    : DECIMAL_DOT_DIGITS
    ;

IDENTIFIER
    : LETTER LETTER_OR_DIGIT*
    ;

fragment DECIMAL_DOT_DIGITS
    : (DECIMAL_DIGIT+ '.' DECIMAL_DIGIT+ |  DECIMAL_DIGIT+ '.' | '.' DECIMAL_DIGIT+);

fragment DECIMAL_DIGIT
    : [0-9]
    ;

fragment LETTER
    : [A-Z$_] ;

fragment LETTER_OR_DIGIT
    : [A-Z0-9$_] ;

WS  : [ \r\n\t]+ -> skip ;

