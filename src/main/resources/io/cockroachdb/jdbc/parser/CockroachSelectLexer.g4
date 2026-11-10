/*
    Lexer for qualifying CockroachDB SELECT statements for implicit FOR UPDATE rewrites.
    Separate from the batch DML grammar (CockroachSQLLexer) on purpose.

    Fail closed: any character not covered by a rule becomes an UNSUPPORTED token, which no
    parser rule accepts. Unclosed literals and quoted identifiers therefore never lex as
    literals; their opening quote becomes UNSUPPORTED.
 */
lexer grammar CockroachSelectLexer;

options { caseInsensitive = true; }

// Keywords of the supported subset
SELECT  : 'SELECT';
ALL     : 'ALL';
FROM    : 'FROM';
WHERE   : 'WHERE';
ORDER   : 'ORDER';
BY      : 'BY';
ASC     : 'ASC';
DESC    : 'DESC';
NULLS   : 'NULLS';
LIMIT   : 'LIMIT';
OFFSET  : 'OFFSET';
AS      : 'AS';
AND     : 'AND';
OR      : 'OR';
NOT     : 'NOT';
IS      : 'IS';
NULL    : 'NULL';
TRUE    : 'TRUE';
FALSE   : 'FALSE';
IN      : 'IN';
BETWEEN : 'BETWEEN';
LIKE    : 'LIKE';
ILIKE   : 'ILIKE';

// Contextual keywords, also accepted as names
FIRST   : 'FIRST';
LAST    : 'LAST';
ROW     : 'ROW';
ROWS    : 'ROWS';

// Keywords introducing unsupported constructs. Never accepted by the parser, so their
// presence anywhere outside literals, quoted identifiers and comments disqualifies a query.
RESERVED
    : 'ANY' | 'ARRAY' | 'CASE' | 'CAST' | 'COLLATE' | 'CROSS' | 'DISTINCT' | 'ELSE' | 'END'
    | 'ESCAPE' | 'EXCEPT' | 'EXISTS' | 'FETCH' | 'FOR' | 'FULL' | 'GROUP' | 'HAVING' | 'INNER'
    | 'INTERSECT' | 'INTO' | 'JOIN' | 'LATERAL' | 'LEFT' | 'NATURAL' | 'OF' | 'ON' | 'ONLY'
    | 'OVER' | 'RETURNING' | 'RIGHT' | 'SIMILAR' | 'SOME' | 'SYMMETRIC' | 'TABLE' | 'THEN'
    | 'UNION' | 'USING' | 'VALUES' | 'WHEN' | 'WINDOW' | 'WITH'
    ;

// Literals. Standard strings treat backslash literally; '' is an escaped quote.
STRING          : '\'' ( ~'\'' | '\'\'' )* '\'';
ESCAPE_STRING   : 'E\'' ( ~['\\] | '\\' . | '\'\'' )* '\'';
// Only untagged dollar quotes are supported ($tag$..$tag$ is UNSUPPORTED)
DOLLAR_STRING   : '$$' .*? '$$';
NUMBER          : ( DIGIT+ ( '.' DIGIT* )? | '.' DIGIT+ ) ( 'E' [+-]? DIGIT+ )?;
// Numbers running into identifier characters (like hex 0x1F) are not supported
UNSUPPORTED_NUMBER : ( DIGIT+ ( '.' DIGIT* )? | '.' DIGIT+ ) [A-Z_$] [A-Z0-9_$]*;

PARAMETER       : '?' | '$' DIGIT+;

QUOTED_IDENTIFIER : '"' ( ~'"' | '""' )* '"';
IDENTIFIER        : [A-Z_\p{L}] [A-Z0-9_$\p{L}\p{N}]*;

// Operators and punctuation
TYPE_CAST : '::';
CONCAT    : '||';
LE        : '<=';
GE        : '>=';
NE        : '<>' | '!=';
EQ        : '=';
LT        : '<';
GT        : '>';
PLUS      : '+';
MINUS     : '-';
STAR      : '*';
SLASH     : '/';
PERCENT   : '%';
DOT       : '.';
COMMA     : ',';
LPAREN    : '(';
RPAREN    : ')';
SEMI      : ';';

// Comments (block comments nest) and whitespace. Block comments are kept on the hidden
// channel because the processor verifies their nesting: if an outer comment is unclosed,
// this rule falls back to ending at the first '*/', which must not be accepted.
LINE_COMMENT  : '--' ~[\r\n]* -> skip;
BLOCK_COMMENT : '/*' ( BLOCK_COMMENT | . )*? '*/' -> channel(HIDDEN);
WS            : [ \t\r\n\f]+ -> skip;

// Anything else
UNSUPPORTED : .;

fragment DIGIT : [0-9];
