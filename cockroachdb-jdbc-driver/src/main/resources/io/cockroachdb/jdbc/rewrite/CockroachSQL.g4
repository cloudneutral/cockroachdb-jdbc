/*
    Simplified ANLTR4 grammar for CockroachDB UPDATE DML statements for the
    purpose of batch array rewrites.
 */
grammar CockroachSQL;

// Parser rules

root
  : statement ignore? EOF
  ;

// Only support UPDATEs atm
statement
    : updateStatement
    ;

ignore
    : SEMICOLON
    | COMMENT
    ;

updateStatement
    : UPDATE tableId SET setClauseList whereClause
    ;

setClauseList
   : setClause (COMMA setClause)*
   ;

setClause
   : identifier EQUALS constant
   | identifier EQUALS identifier (MINUS | PLUS)? constant
   ;

whereClause
    : WHERE expression
    ;

expression
    : LEFT_PAREN expression RIGHT_PAREN              # parenExpression
    | NOT expression                                 # notExpression
    | left=constant op=compare right=expression      # compareExpression
    | left=expression op=(AND|OR) right=expression   # binaryExpression
    | left=constant op=('IS'|'is') NOT? right=NULL   # nullExpression
    | constant                                       # constantExpression
    ;

compare
    : EQUALS
    | GT
    | GE
    | LT
    | LE
    | NE
    ;

identifier
    : IDENTIFIER DOT IDENTIFIER
    | IDENTIFIER
    ;

parameter
    : COLON IDENTIFIER
    | QUESTION (INTEGER_LITERAL)?
    ;

constant
    : NULL
    | booleanConstant
    | identifier
    | (MINUS | PLUS)? parameter
    | (MINUS | PLUS)? INTEGER_LITERAL
    | (MINUS | PLUS)? DECIMAL_LITERAL
    | STRING_LITERAL+
    ;

booleanConstant
    : TRUE | FALSE
    ;

tableId
    : name=identifier
    ;

// Lexer rules

CREATE : 'CREATE' | 'create';
SELECT: 'SELECT' | 'select';
FROM: 'FROM' | 'from';
INSERT : 'INSERT' | 'insert';
INTO : 'INTO' | 'into';
VALUES : 'VALUES' | 'values';
ROWS : 'ROWS' | 'rows';
UPDATE : 'UPDATE' | 'update';
UPSERT : 'UPSERT' | 'upsert';
SET : 'SET' | 'set';
WHERE : 'WHERE' | 'where';
DELETE : 'DELETE' | 'delete';
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
OR: 'OR' | 'or' | '||';

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

