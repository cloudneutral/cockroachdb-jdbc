/*
    Parser for the subset of CockroachDB SELECT statements that qualify for implicit
    FOR UPDATE rewrites: simple single-table selections without function calls.

    Anything outside this subset fails to parse and is passed through unchanged. Checks that
    are not expressed in the grammar (system schemas, nesting depth) are applied by
    io.cockroachdb.jdbc.rewrite.SelectForUpdateProcessor.
 */
parser grammar CockroachSelectParser;

options { tokenVocab = CockroachSelectLexer; }

root
    : selectStatement SEMI* EOF
    ;

selectStatement
    : SELECT ALL? selectItems FROM tableReference whereClause? orderByClause? paginationClause?
    ;

selectItems
    : selectItem (COMMA selectItem)*
    ;

selectItem
    : STAR
    | name DOT STAR
    | expression alias?
    ;

tableReference
    : qualifiedName alias?
    ;

alias
    : AS? name
    ;

whereClause
    : WHERE expression
    ;

orderByClause
    : ORDER BY orderItem (COMMA orderItem)*
    ;

orderItem
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

paginationClause
    : limitClause offsetClause?
    | offsetClause limitClause?
    ;

limitClause
    : LIMIT (NUMBER | PARAMETER | ALL)
    ;

offsetClause
    : OFFSET (NUMBER | PARAMETER) (ROW | ROWS)?
    ;

// No operator precedence is needed since the expression is only recognized, never rewritten.
expression
    : operand (binaryOperator operand)*
    ;

binaryOperator
    : EQ | NE | LT | LE | GT | GE | PLUS | MINUS | STAR | SLASH | PERCENT | CONCAT | AND | OR
    ;

operand
    : (NOT | MINUS | PLUS)* primary postfix*
    ;

postfix
    : IS NOT? (NULL | TRUE | FALSE)
    | NOT? IN LPAREN expression (COMMA expression)* RPAREN
    | NOT? BETWEEN operand AND operand
    | NOT? (LIKE | ILIKE) operand
    | TYPE_CAST name
    ;

// Function calls are not part of the grammar: a name followed by '(' fails to parse.
primary
    : literal
    | PARAMETER
    | LPAREN expression RPAREN
    | qualifiedName
    ;

literal
    : STRING | ESCAPE_STRING | DOLLAR_STRING | NUMBER | TRUE | FALSE | NULL
    ;

qualifiedName
    : name (DOT name (DOT name)?)?
    ;

name
    : IDENTIFIER | QUOTED_IDENTIFIER | FIRST | LAST | ROW | ROWS
    ;
