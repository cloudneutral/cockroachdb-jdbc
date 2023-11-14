/*
    Simplified ANLTR4 grammar for CockroachDB DML statements for the
    purpose of batch array rewrites.
 */
parser grammar CockroachParser;

options { tokenVocab = CockroachLexer; }

// Parser rules

root
  : statement ignore? EOF
  ;

statement
    : insertStatement
    | upsertStatement
    | updateStatement
    ;

ignore
    : SEMICOLON*
    | COMMENT
    ;

//
// Insert
//

insertStatement
    : INSERT INTO tableName columnNames VALUES valueList (COMMA valueList)*
    (optionalOnConflict)?
    ;

optionalOnConflict
   : ON CONFLICT optionalConflictExpression DO NOTHING
   ;

optionalConflictExpression
    : LEFT_PAREN columnName (COMMA columnName)* RIGHT_PAREN
    | ON CONSTRAINT columnName
    ;

//
// Upsert
//
upsertStatement
    : UPSERT INTO tableName columnNames VALUES valueList (COMMA valueList)*
    ;

columnNames
    : LEFT_PAREN columnName (COMMA columnName)* RIGHT_PAREN
    ;

columnName
    : identifier
    ;

valueList
    :  LEFT_PAREN atomList RIGHT_PAREN
    ;

atomList
    : atom (COMMA atom)*;

//
// Update
//
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
