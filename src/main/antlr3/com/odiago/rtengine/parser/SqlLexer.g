// (c) Copyright 2010 Odiago, Inc.

lexer grammar SqlLexer;

options {
  language = Java;
  superClass = AbstractSqlLexer;
}

@header {
  package com.odiago.rtengine.parser;
}



CREATE : C R E A T E ;
EXPLAIN: E X P L A I N;
FROM : F R O M;
SELECT : S E L E C T;
STREAM : S T R E A M ;
WHERE : W H E R E;

ID  : ('a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'0'..'9'|'_')*
    ;

ALL_FIELDS : '*';

LPAREN : '(';

RPAREN : ')';

COMMA : ',';

INT : '0'..'9'+
    ;

COMMENT
    :   '//' ~('\n'|'\r')* '\r'? '\n' {skip();}
    |   '/*' ( options {greedy=false;} : . )* '*/' {skip();}
    ;

WS  :   ( ' '
        | '\t'
        | '\r'
        | '\n'
        ) {skip();}
    ;

STRING
    :  '"' ( ESC_SEQ | ~('\\'|'"') )* '"'
    ;

fragment
HEX_DIGIT : ('0'..'9'|'a'..'f'|'A'..'F') ;

fragment
ESC_SEQ
    :   '\\' ('b'|'t'|'n'|'f'|'r'|'\"'|'\''|'\\')
    |   UNICODE_ESC
    |   OCTAL_ESC
    ;

fragment
OCTAL_ESC
    :   '\\' ('0'..'3') ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7')
    ;

fragment
UNICODE_ESC
    :   '\\' 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;



// Fragments for individual letters for case-insensitivity in keywords.

fragment A : 'a' | 'A';
fragment B : 'b' | 'B';
fragment C : 'c' | 'C';
fragment D : 'd' | 'D';
fragment E : 'e' | 'E';
fragment F : 'f' | 'F';
fragment G : 'g' | 'G';
fragment H : 'h' | 'H';
fragment I : 'i' | 'I';
fragment J : 'j' | 'J';
fragment K : 'k' | 'K';
fragment L : 'l' | 'L';
fragment M : 'm' | 'M';
fragment N : 'n' | 'N';
fragment O : 'o' | 'O';
fragment P : 'p' | 'P';
fragment Q : 'q' | 'Q';
fragment R : 'r' | 'R';
fragment S : 's' | 'S';
fragment T : 't' | 'T';
fragment U : 'u' | 'U';
fragment V : 'v' | 'V';
fragment W : 'w' | 'W';
fragment X : 'y' | 'X';
fragment Y : 'x' | 'Y';
fragment Z : 'z' | 'Z';
