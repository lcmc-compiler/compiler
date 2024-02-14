grammar FOOL;
 
@lexer::members {
public int lexicalErrors=0;
}
   
/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/
  
prog  : progbody EOF ;
     
progbody : LET dec+ IN exp SEMIC  #letInProg
         | exp SEMIC              #noDecProg
         ;
  
dec : VAR ID COLON type ASS exp SEMIC  #vardec
    | FUN ID COLON type LPAR (ID COLON type (COMMA ID COLON type)* )? RPAR 
        	(LET dec+ IN)? exp SEMIC   #fundec
    /***/
    | CLASS ID type LPAR (ID COLON type (COMMA ID COLON type)* )? RPAR
            CLPAR
            (FUN ID COLON type LPAR (ID COLON type (COMMA ID COLON type)* )? RPAR
                                 	(LET dec+ IN)? exp SEMIC)*
            CRPAR #classdec
    ;
           
exp     : exp TIMES exp #times
        | exp PLUS  exp #plus
        | exp EQ  exp   #eq 
        | LPAR exp RPAR #pars
    	| MINUS? NUM #integer
    	    /***/
        | exp LESSEQ exp #lesseq
        | exp GREQ exp #greq
        | exp OR exp #or
        | exp AND exp #and
        | exp DIV exp #div
        | NOT exp #not
        | exp MINUS exp #minus
            //TODO
        | NULL #null
        | NEW exp #new
        | exp DOT exp #dot
    	    /***/
	    | TRUE #true     
	    | FALSE #false
	    | IF exp THEN CLPAR exp CRPAR ELSE CLPAR exp CRPAR  #if   
	    | PRINT LPAR exp RPAR #print
	    | ID #id
	    | ID LPAR (exp (COMMA exp)* )? RPAR #call
        ; 
             
type    : INT #intType
        | BOOL #boolType
        /***/
        | ID #idType
 	    ;  
 	  		  
/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

PLUS  	: '+' ;
MINUS	: '-' ; 
TIMES   : '*' ;
LPAR	: '(' ;
RPAR	: ')' ;
CLPAR	: '{' ;
CRPAR	: '}' ;
SEMIC 	: ';' ;
COLON   : ':' ; 
COMMA	: ',' ;
EQ	    : '==' ;	
ASS	    : '=' ;
/***/
LESSEQ  : '<=';
GREQ    : '>=';
OR      : '||';
AND     : '&&';
DIV     : '/';
DOT     : '.';
NOT     : '!';
CLASS   : 'class';
NULL    : 'null';
NEW     : 'new';
/***/
TRUE	: 'true' ;
FALSE	: 'false' ;
IF	    : 'if' ;
THEN	: 'then';
ELSE	: 'else' ;
PRINT	: 'print' ;
LET     : 'let' ;	
IN      : 'in' ;	
VAR     : 'var' ;
FUN	    : 'fun' ;	  
INT	    : 'int' ;
BOOL	: 'bool' ;
NUM     : '0' | ('1'..'9')('0'..'9')* ; 

ID  	: ('a'..'z'|'A'..'Z')('a'..'z' | 'A'..'Z' | '0'..'9')* ;


WHITESP  : ( '\t' | ' ' | '\r' | '\n' )+    -> channel(HIDDEN) ;

COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;
 
ERR   	 : . { System.out.println("Invalid char "+getText()+" at line "+getLine()); lexicalErrors++; } -> channel(HIDDEN); 


