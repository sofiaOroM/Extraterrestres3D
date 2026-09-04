grammar YLanguage;

// PROGRAMA

programa
    : seccionEstructuras? seccionFunciones EOF
    ;

// SECCION DE ESTRUCTURAS (opcional, puede tener varias estructuras)

seccionEstructuras
    : ESTRUCTURASKEY declaracionEstructuras+
    ;

declaracionEstructuras
    : ESTRUCTURA ID ':' atributoEstructura+
    ;

// Un atributo puede ser un tipo simple, un tipo anidado (otra estructura)
// o un arreglo de tamaño constante (obligatorio dentro de estructuras)
atributoEstructura
    : tipo ID ('[' ENTERO ']')*
    ;

// SECCION DE FUNCIONES (obligatoria, una o más funciones)

seccionFunciones
    : FUNCIONESKEY declaracionFunciones+
    ;

declaracionFunciones
    : DEFINIR ID '(' parametros? ')' ':' declaracionEstructuras? bloqueFuncion
    | DEFINIR ID '(' parametros? ')' RETORNO tipo ':' bloqueFuncion
    ;

parametros
    : parametro (',' parametro)*
    ;

// Los primitivos se pasan por valor.
// Los arreglos se pasan por referencia con el prefijo [].
// Las estructuras se pasan por referencia con el prefijo {}.
parametro
    : tipo ID                  # parametroSimple
    | '[' ']' tipo ID          # parametroArreglo
    | '{' '}' ID ID            # parametroEstructura
    ;

bloqueFuncion
    : sentencia*
    ;

// SENTENCIAS

sentencia
    : declaracionVariableLocal
    | declaracionArrayLocal
    | asignacion
    | incremento
    | entrada
    | salida
    | condicional
    | cicloWhile
    | cicloDo
    | cicloFor
    | interrupcion
    | retorno
    | llamadaFuncion
    ;

// ---------------- Declaraciones locales ----------------

// entero sinInicializacion
// entero edadUsuario = 25
// Persona alumno1
// Punto p1 = {10, 20, 85.5}
declaracionVariableLocal
    : tipo ID ('=' valorInicial)?
    ;

// entero numeros[10]
// entero matriz[3][3]
// entero numeros[5] = {10, 20, 30, 40, 50}
declaracionArrayLocal
    : tipo ID ('[' expresion ']')+ ('=' inicializacionEstructura)?
    ;

valorInicial
    : expresion
    | inicializacionEstructura
    ;

// {10, 20, 85.5}
inicializacionEstructura
    : '{' listaValores '}'
    ;

listaValores
    : expresion (',' expresion)*
    ;

// ---------------- Asignaciones ----------------

// numeros[2] = numeros[0] * 3
// alumno1.nombre = "Yennifer"
// p3 = p1
asignacion
    : acceso '=' expresion
    ;

// acceso a variable simple, campo de estructura o índice de arreglo
// (permite combinaciones como p1.promedio o matriz[i][j])
acceso
    : ID ('.' ID | '[' expresion ']')*
    ;

// ---------------- Incremento/decremento abreviado ----------------

// contador++
incremento
    : ID ('++' | '--')
    ;

// ---------------- Entrada/Salida ----------------

// leer()
// x = leer()   -> este caso ya lo cubre 'asignacion' + 'expresion'
entrada
    : LEER '(' ')'
    ;

// imprimir("...")
salida
    : IMPRIMIR '(' expresion ')'
    ;

// ---------------- Condicionales ----------------

// si(...) entonces / sino(...) entonces / contrario
condicional
    : SI '(' expresion ')' ENTONCES bloqueFuncion
      (SINO '(' expresion ')' ENTONCES bloqueFuncion)*
      (CONTRARIO bloqueFuncion)?
    | condicionalElegir
    ;

// elegir(opcion): caso 1: ... romper  ... siempre: ... romper
condicionalElegir
    : ELEGIR '(' expresion ')' ':' casoElegir+ casoDefault?
    ;

casoElegir
    : CASO (ENTERO | ID) ':' bloqueFuncion
    ;

casoDefault
    : SIEMPRE ':' bloqueFuncion
    ;

// ---------------- Ciclos ----------------
// while = mientras, do = hacer, for = para)

// mientras(...) hacer
cicloWhile
    : MIENTRAS '(' expresion ')' HACER bloqueFuncion
    ;

// hacer: ... mientras(...)
cicloDo
    : HACER ':' bloqueFuncion MIENTRAS '(' expresion ')'
    ;

// para(entero i = 0; i < 10; i++):
cicloFor
    : PARA '(' (declaracionVariableLocal | asignacion) ';' expresion ';' (incremento | asignacion) ')' ':' bloqueFuncion
    ;

// ---------------- Interrupciones de flujo ----------------

interrupcion
    : ROMPER
    | CONTINUAR
    ;

// ---------------- Retorno ----------------

// retornar 160
retorno
    : RETORNAR expresion?
    ;

// ---------------- Llamada a función definida por el usuario ----------------

llamadaFuncion
    : ID '(' argumentos? ')'
    ;

argumentos
    : expresion (',' expresion)*
    ;

// EXPRESIONES
// (orden de alternativas = precedencia, de mayor a menor)

expresion
    : '(' expresion ')'                            # exprParentesis
    | '!' expresion                                 # exprNegacion
    | acceso                                        # exprAcceso
    | llamadaFuncion                                # exprLlamadaFuncion
    | entrada                                       # exprEntrada
    | literal                                       # exprLiteral
    | expresion op=('*'|'/') expresion              # exprMultiplicativo
    | expresion op=('+'|'-') expresion              # exprAditivo
    | expresion op=('=='|'!='|'<'|'>') expresion    # exprRelacional
    | expresion op=('&&'|'||') expresion            # exprLogico
    ;

literal
    : ENTERO
    | DECIMAL
    | CADENA_LITERAL
    | CARACTER_LITERAL
    | VERDADERO
    | FALSO
    ;

tipo
    : ENTEROKEY
    | FLOTANTEKEY
    | CADENAKEY
    | CARACTERKEY
    | BOOLKEY
    | ID
    ;

// LEXER

// ---- Secciones ----
ESTRUCTURASKEY : '%estructuras';
FUNCIONESKEY   : '%funciones';

// ---- Palabras clave de estructura/función ----
ESTRUCTURA : 'estructura';
DEFINIR    : 'definir';
RETORNO    : '->';
RETORNAR   : 'retornar';

// ---- Tipos de datos ----
ENTEROKEY    : 'entero';
FLOTANTEKEY  : 'flotante';
CADENAKEY    : 'cadena';
CARACTERKEY  : 'caracter';
BOOLKEY      : 'bool';

// ---- Literales booleanos ----
VERDADERO : 'verdadero';
FALSO     : 'falso';

// ---- Entrada/salida ----
IMPRIMIR : 'imprimir';
LEER     : 'leer';

// ---- Condicionales ----
SI        : 'si';
ENTONCES  : 'entonces';
SINO      : 'sino';
CONTRARIO : 'contrario';
ELEGIR    : 'elegir';
CASO      : 'caso';
SIEMPRE   : 'siempre';

// ---- Ciclos y control de flujo ----
PARA      : 'para';
MIENTRAS  : 'mientras';
HACER     : 'hacer';
ROMPER    : 'romper';
CONTINUAR : 'continuar';

// ---- Operadores ----
// Aritméticos
MAS   : '+';
MENOS : '-';
POR   : '*';
DIV   : '/';
// Relacionales
IGUAL      : '==';
DIFERENTE  : '!=';
MENORQUE   : '<';
MAYORQUE   : '>';
// Lógicos
AND : '&&';
OR  : '||';
// Negación
NOT : '!';
// Suma/resta abreviada
MASMAS     : '++';
MENOSMENOS : '--';
// Asignación
ASIGNAR : '=';

// ---- Identificadores y literales ----
ID : [a-zA-Z_][a-zA-Z0-9_]*;
DECIMAL : [0-9]+ '.' [0-9]+;
ENTERO  : [0-9]+;

CADENA_LITERAL   : '"' (~["\\] | '\\' .)* '"';
CARACTER_LITERAL : '\'' (~['\\] | '\\' .) '\'';

// ---- Comentarios y espacios en blanco ----
LINE_COMMENT  : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT : '/*' .*? '*/' -> skip;
WS            : [ \t\r\n]+ -> skip;


// PUNTOYCOMA : ';';
// -> Aparece solo 2 veces en todo el documento ("contador++;" y
//    "continuar;"), ambas dentro de la sección de Ciclos, mientras
//    que el resto de ejemplos NO usan ';'.Si se confirma que sí es un
//    terminador de sentencia, se debería agregar en
//    'sentencia' (ej. sentencia PUNTOYCOMA?).
