grammar ZetarianoLanguage;

// Zetariano es orientado a objetos, basado en sintaxis Java.
// Extensión: .z
// El archivo debe llamarse igual que la clase definida (esto es
// una restricción SEMÁNTICA, no se puede validar en la gramática).

programa
    : claseDecl EOF
    ;

claseDecl
    : PUBLIC CLASS ID '{' miembroClase* '}'
    ;

miembroClase
    : atributo
    | constructorDecl
    | metodoDecl
    ;

// String nombre;  int edad;
atributo
    : tipo ID ';'
    ;

// public Persona(String nombreParametro, int edadParametro) { ... }
constructorDecl
    : PUBLIC ID '(' parametros? ')' bloque
    ;

// public void saludar() { ... }
// public int calcularAnioNacimiento(int anioActual) { ... }
metodoDecl
    : PUBLIC (tipo | VOID) ID '(' parametros? ')' bloque
    ;

parametros
    : parametro (',' parametro)*
    ;

parametro
    : tipo ID
    ;

bloque
    : '{' sentencia* '}'
    ;

// Si el cuerpo de un si/sino-si/sino tiene una sola instrucción,
// las llaves son opcionales (según el documento)
sentenciaOBloque
    : bloque
    | sentencia
    ;

// SENTENCIAS

sentencia
    : declaracionVariable
    | asignacion
    | incrementoDecremento
    | expresionSentencia
    | condicional
    | switchStmt
    | cicloFor
    | cicloWhile
    | cicloDoWhile
    | BREAK ';'
    | CONTINUE ';'
    | retorno
    | bloque
    ;

// int edad = 25;
// int[] calificaciones = new int[5];
// String[] nombres = {"Carlos", "Ana", "Pedro"};
// int[][] matriz = new int[3][3];
// Persona alumno1;
declaracionVariable
    : tipo ID ('=' valorInicial)? ';'
    ;

valorInicial
    : expresion
    | arrayLiteral
    ;

// {"Carlos", "Ana", "Pedro"}
arrayLiteral
    : '{' listaValores? '}'
    ;

listaValores
    : expresion (',' expresion)*
    ;

// x += 3;  x -= 2;  x *= 2;  mi_entero = 23;
asignacion
    : acceso op=('='|'+='|'-='|'*=') expresion ';'
    ;

// a++;  b--;
incrementoDecremento
    : ID ('++'|'--') ';'
    ;

// llamadas usadas como sentencia: println(...), readln();, obj.metodo(...);
expresionSentencia
    : expresion ';'
    ;

// ---------------- Condicionales ----------------

// if (edad > 18) {...} else if (edad == 18) {...} else {...}
// (la cadena de "else if" se resuelve por recursión natural:
//  el 'else' de aquí puede contener, como sentencia, otro 'condicional')
condicional
    : IF '(' expresion ')' sentenciaOBloque (ELSE sentenciaOBloque)?
    ;

// switch (opcion) { case 1: ... break; case 2: ... default: ... break; }
switchStmt
    : SWITCH '(' expresion ')' '{' casoSwitch* defaultSwitch? '}'
    ;

casoSwitch
    : CASE (ENTERO | CADENA_LITERAL) ':' sentencia*
    ;

defaultSwitch
    : DEFAULT ':' sentencia*
    ;

// ---------------- Ciclos ----------------

// for (int i = 0; i < 5; i++) {...}
// for ( ; ; ) {...}   -> todos los parametros son opcionales
cicloFor
    : FOR '(' forInit? ';' expresion? ';' forUpdate? ')' sentenciaOBloque
    ;

forInit
    : tipo ID '=' expresion    # forInitDeclaracion
    | acceso '=' expresion     # forInitAsignacion
    ;

forUpdate
    : ID ('++'|'--')           # forUpdateIncremento
    | acceso '=' expresion     # forUpdateAsignacion
    ;

// while (contador < 3) {...}
cicloWhile
    : WHILE '(' expresion ')' sentenciaOBloque
    ;

// do {...} while (intentos < 5);
cicloDoWhile
    : DO bloque WHILE '(' expresion ')' ';'
    ;

// return anioActual - edad;
retorno
    : RETURN expresion? ';'
    ;

// EXPRESIONES
// (orden de reglas = precedencia, de menor a mayor uso aquí:
//  ternario es el de MENOR precedencia, se evalúa último)

expresion
    : expresionTernaria
    ;

// (edad >= 18) ? "Es mayor de edad" : "Es menor de edad"
expresionTernaria
    : expresionLogicaOr ('?' expresion ':' expresion)?
    ;

expresionLogicaOr
    : expresionLogicaAnd ('||' expresionLogicaAnd)*
    ;

expresionLogicaAnd
    : expresionIgualdad ('&&' expresionIgualdad)*
    ;

expresionIgualdad
    : expresionRelacional (('=='|'!=') expresionRelacional)*
    ;

expresionRelacional
    : expresionAditiva (('<'|'>'|'<='|'>=') expresionAditiva)*
    ;

expresionAditiva
    : expresionMultiplicativa (('+'|'-') expresionMultiplicativa)*
    ;

// incluye % (modulo)
expresionMultiplicativa
    : expresionUnaria (('*'|'/'|'%') expresionUnaria)*
    ;

expresionUnaria
    : '!' expresionUnaria
    | '-' expresionUnaria
    | expresionPrimaria
    ;

expresionPrimaria
    : '(' expresion ')'                              # exprParentesis
    | NEW ID '(' argumentos? ')'                      # exprNuevoObjeto
    | NEW (INT|DOUBLE|CHAR|BOOLEAN) ('[' expresion ']')+ # exprNuevoArreglo
    | acceso                                          # exprAcceso
    | literal                                         # exprLiteral
    ;

// obj.campo[i].metodo(...)  encadenable
acceso
    : ID sufijo*
    ;

sufijo
    : '.' ID
    | '[' expresion ']'
    | '(' argumentos? ')'
    ;

argumentos
    : expresion (',' expresion)*
    ;

literal
    : ENTERO
    | DECIMAL
    | CADENA_LITERAL
    | CARACTER_LITERAL
    | TRUE
    | FALSE
    | NULL
    ;

tipo
    : (INT | DOUBLE | CHAR | BOOLEAN | STRINGKEY | ID) ('[' ']')*
    ;

// LEXER

PUBLIC   : 'public';
CLASS    : 'class';
VOID     : 'void';
NEW      : 'new';

INT      : 'int';
DOUBLE   : 'double';
CHAR     : 'char';
BOOLEAN  : 'boolean';
STRINGKEY: 'String';

TRUE  : 'true';
FALSE : 'false';
NULL  : 'null';

IF       : 'if';
ELSE     : 'else';
SWITCH   : 'switch';
CASE     : 'case';
DEFAULT  : 'default';
FOR      : 'for';
WHILE    : 'while';
DO       : 'do';
BREAK    : 'break';
CONTINUE : 'continue';
RETURN   : 'return';

// Operadores
IGUAL      : '==';
DIFERENTE  : '!=';
MENORIGUAL : '<=';
MAYORIGUAL : '>=';
MENORQUE   : '<';
MAYORQUE   : '>';
AND        : '&&';
OR         : '||';
NOT        : '!';
MASMAS     : '++';
MENOSMENOS : '--';
MASIGUAL   : '+=';
MENOSIGUAL : '-=';
PORIGUAL   : '*=';
ASIGNAR    : '=';
MAS   : '+';
MENOS : '-';
POR   : '*';
DIV   : '/';
MOD   : '%';
INTERROGACION : '?';

// Identificadores y literales
ID : [a-zA-Z_][a-zA-Z0-9_]*;
DECIMAL : [0-9]+ '.' [0-9]+;
ENTERO  : [0-9]+;

CADENA_LITERAL   : '"' (~["\\] | '\\' .)* '"';
CARACTER_LITERAL : '\'' (~['\\] | '\\' .) '\'';

LINE_COMMENT  : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT : '/*' .*? '*/' -> skip;
WS            : [ \t\r\n]+ -> skip;

// +' para concatenar String con otros
// tipos (esto se resuelve en el análisis semántico, no aquí).
