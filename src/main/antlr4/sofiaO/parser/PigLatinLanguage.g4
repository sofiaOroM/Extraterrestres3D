grammar PigLatinLanguage;


// Pig Latin es el lenguaje de ENTRADA del programa. Es case
// sensitive. NO permite definir estructuras propias: todo tipo
// que no sea primitivo (numerus, textum, decimalis, littera)
// debe importarse desde un archivo .y (estructuras/funciones) o
// .z (clases). El programa tiene una sección de importaciones,
// una sección opcional de variables globales, y una sección de
// función principal (obligatoria).



programa
    : importacion* seccionVariables? seccionPrincipal EOF
    ;

// import carpeta.Objeto1.z
// import carpeta.Funciones.y
// (en el documento NO llevan ';' al final; se deja así porque
// ambos ejemplos coinciden en omitirlo)
importacion
    : IMPORT rutaImport
    ;

rutaImport
    : ID ('.' ID)*
    ;

// VARIABILES>
// esto edad : numerus 20;
// esto cifrado : falsus;
seccionVariables
    : VARIABLESKEY declaracionGlobal+
    ;

declaracionGlobal
    : declaracionVariable
    | declaracionArreglo
    ;

// MAIOR>
// ...
// FINIS;
seccionPrincipal
    : MAIORKEY sentencia+ FINISPROGRAMA ';'
    ;


// SENTENCIAS


sentencia
    : declaracionVariable
    | declaracionArreglo
    | asignacion
    | incrementoDecremento
    | escritura
    | lectura
    | condicional
    | cicloDum
    | cicloFacere
    | cicloPer
    | PERGE ';'
    | INTERRUMPE ';'
    | llamada ';'
    ;

// ---------------- Declaraciones ----------------

// esto mi_entero : numerus 10;
// esto total : numerus fuerza * 2;
// esto nombre : textum "Somos la resistencia";
// esto mi_booleano : falsus;
// esto ciudadano : Persona {"Valeria", 25, {"Avenida Central", 500}};
// esto miObjeto : novus Persona(12, "Profesor");
// Los booleanos no llevan palabra de tipo: se infiere del literal
// (verum/falsus), tal como aparece en todos los ejemplos del documento.
declaracionVariable
    : ESTO ID ':' VERUM ';'
    | ESTO ID ':' FALSUS ';'
    | ESTO ID ':' tipo valor? ';'
    ;

// series mis_enteros[2] : numerus {1, 1};
// series nombres_[2] : textum;
// series resistencia[3] : Persona;
declaracionArreglo
    : SERIES ID '[' expresion ']' ':' tipo ('{' listaValores '}')?  ';'
    ;

valor
    : expresion
    | '{' listaValores '}'
    ;

listaValores
    : expresion (',' expresion)*
    ;

tipo
    : NUMERUSKEY
    | TEXTUMKEY
    | DECIMALISKEY
    | LITTERAKEY
    | ID              // tipo importado: estructura (.y) o clase (.z)
    ;

// ---------------- Asignaciones ----------------

// mi_entero = 23;
// nombres[0] = "Capitán Espárragos";
// miObjeto.nombre = "Yennifer";
asignacion
    : acceso '=' expresion ';'
    ;

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

// llamada usada como sentencia: misObjetos[9].hablar(...);
llamada
    : acceso
    ;

incrementoDecremento
    : ID ('++'|'--') ';'
    ;

// ---------------- Entrada/Salida ----------------

// >> "Holaaaaa";
// >> mi_string >> mi_textum;
escritura
    : '>>' expresion ('>>' expresion)* ';'
    ;

// <<              (lee y descarta)
// mi_textum <<    (lee y guarda en la variable)
lectura
    : ID? '<<' ';'
    ;

// ---------------- Condicionales ----------------

// si (edad >= 18) { ... } finis;
// si (...) { ... } aliter (...) { ... } aliter { ... } finis;
condicional
    : SI '(' expresion ')' '{' sentencia* '}'
      (ALITER '(' expresion ')' '{' sentencia* '}')*
      (ALITER '{' sentencia* '}')?
      FINISBLOQUE ';'
    ;

// ---------------- Ciclos ----------------

// dum (x < 100) { ... } finis;
cicloDum
    : DUM '(' expresion ')' '{' sentencia* '}' FINISBLOQUE ';'
    ;

// facere { ... } dum (x < 10);
cicloFacere
    : FACERE '{' sentencia* '}' DUM '(' expresion ')' ';'
    ;

// per (esto i : numerus 0; i < 10; i++) { ... }
cicloPer
    : PER '(' forInit ';' expresion ';' forUpdate ')' '{' sentencia* '}'
    ;

forInit
    : ESTO ID ':' tipo valor
    ;

forUpdate
    : ID ('++'|'--')
    | acceso '=' expresion
    ;


// EXPRESIONES
// (orden de alternativas = precedencia, de mayor a menor)


expresion
    : '(' expresion ')'                                  # exprParentesis
    | NOVUS ID '(' argumentos? ')'                        # exprNuevoObjeto
    | acceso                                              # exprAcceso
    | literal                                             # exprLiteral
    | expresion op=('*'|'/') expresion                    # exprMultiplicativo
    | expresion op=('+'|'-') expresion                    # exprAditivo
    | expresion op=('=='|'!='|'<'|'>'|'<='|'>=') expresion # exprRelacional
    | expresion op=('&&'|'||') expresion                  # exprLogico
    ;

literal
    : ENTERO
    | DECIMAL
    | CADENA_LITERAL
    | CARACTER_LITERAL
    | VERUM
    | FALSUS
    ;


// LEXER


IMPORT        : 'import';
VARIABLESKEY  : 'VARIABILES>';
MAIORKEY      : 'MAIOR>';
FINISPROGRAMA : 'FINIS';   // cierre del programa (mayúscula)
FINISBLOQUE   : 'finis';   // cierre de si/dum (minúscula, distinto por ser case sensitive)

ESTO     : 'esto';
SERIES   : 'series';
NOVUS    : 'novus';
SI       : 'si';
ALITER   : 'aliter';
DUM      : 'dum';
FACERE   : 'facere';
PER      : 'per';
PERGE    : 'perge';
INTERRUMPE : 'interrumpe';

VERUM  : 'verum';
FALSUS : 'falsus';

NUMERUSKEY   : 'numerus';
TEXTUMKEY    : 'textum';
DECIMALISKEY : 'decimalis';
LITTERAKEY   : 'littera';

// Operadores
IGUAL      : '==';
DIFERENTE  : '!=';
MENORIGUAL : '<=';
MAYORIGUAL : '>=';
MENORQUE   : '<';
MAYORQUE   : '>';
AND : '&&';
OR  : '||';
MASMAS     : '++';
MENOSMENOS : '--';
ASIGNAR : '=';
MAS   : '+';
MENOS : '-';
POR   : '*';
DIV   : '/';
ESCRITURA_OP : '>>';
LECTURA_OP   : '<<';

// Identificadores y literales
ID : [a-zA-Z_][a-zA-Z0-9_]*;
DECIMAL : [0-9]+ '.' [0-9]+;
ENTERO  : [0-9]+;

CADENA_LITERAL   : '"' (~["\\] | '\\' .)* '"';
CARACTER_LITERAL : '\'' (~['\\] | '\\' .) '\'';

// El documento solo muestra este estilo de comentario para Pig
// Latin: dos '##' que abren y dos '##' que cierran un bloque.
BLOCK_COMMENT : '##' .*? '##' -> skip;
WS            : [ \t\r\n]+ -> skip;

//
// 1) "mi_booleano = verum || 1 = 1;" usa un solo '=' donde por
//    contexto parece necesitarse una comparación de igualdad
//    ('=='). Se asumió que es un error de tipeo del documento y
//    NO se agregó un '=' con doble uso (asignación/igualdad); la
//    gramática solo acepta '==' dentro de expresiones.
//
// 2) La línea "miObjeto.nombre = \"Yennifer\"" no lleva ';' al
//    final, a diferencia de todas las demás asignaciones. Se
//    asumió error de tipeo y se exige ';' siempre.
//
// 3) La sección de "Uso de estructuras" muestra, bajo el
//    encabezado "En el archivo .y", una sintaxis de estructura
//    con llaves y punto y coma:
//        estructura {
//            cadena calle;
//            entero numero;
//        } Direccion;
//    Esto CONTRADICE la sintaxis de Y? ya definida en
//    YLanguage.g4 (basada en indentación, "estructura Nombre:").
//    Se ignoró este fragmento por considerarlo una inconsistencia
//    del documento (posible copia de una versión anterior del
//    enunciado) y se mantiene YLanguage.g4 como fuente de verdad
//    para la sintaxis de estructuras. Lo único que sí se tomó de
//    esa sección es el uso de un tipo importado dentro de un
//    .pig, ej.: "esto mi_direccion : Direccion {\"Calle Real\", 42};".
//
// 4) El ejemplo "series nombres[2] : textum {"Hola", "Adios"};"
//    usa comillas tipográficas (“ ”) en vez de comillas rectas.
//    Se asume que es un artefacto del procesador de texto del
//    documento y NO una sintaxis real; CADENA_LITERAL solo
//    reconoce comillas rectas (").
//
// NOT_OP : '!';
// -> No se muestra negación explícita ('!') en ningún ejemplo de
//    Pig Latin (a diferencia de Y? y Zetariano); se deja pendiente.
