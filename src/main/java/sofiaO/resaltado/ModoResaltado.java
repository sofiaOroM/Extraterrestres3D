package sofiaO.resaltado;

import sofiaO.servicio.Lenguaje;

import java.util.List;
import java.util.Set;

/**
 * Qué reconoce el resaltado en cada lenguaje. Las palabras reservadas salen de las
 * gramáticas .g4, de modo que si algo se pinta como palabra reservada, el lexer también
 * lo reconoce como tal.
 */
public enum ModoResaltado {

    Y(new Reglas(
            Set.of("estructura", "definir", "retornar", "imprimir", "leer", "si", "entonces", "sino",
                    "contrario", "elegir", "caso", "siempre", "para", "mientras", "hacer", "romper", "continuar"),
            Set.of("entero", "flotante", "cadena", "caracter", "bool"),
            Set.of("verdadero", "falso"),
            Set.of(), List.of("%estructuras", "%funciones"),
            "//", "/*", "*/", false, true)),

    ZETARIANO(new Reglas(
            Set.of("public", "class", "void", "new", "if", "else", "switch", "case", "default", "for",
                    "while", "do", "break", "continue", "return"),
            Set.of("int", "double", "char", "boolean", "String"),
            Set.of("true", "false", "null"),
            Set.of(), List.of(),
            "//", "/*", "*/", false, true)),

    PIG(new Reglas(
            Set.of("import", "esto", "series", "novus", "si", "aliter", "dum", "facere", "per", "perge",
                    "interrumpe", "finis"),
            Set.of("numerus", "textum", "decimalis", "littera"),
            Set.of("verum", "falsus"),
            Set.of("FINIS"), List.of("VARIABILES>", "MAIOR>"),
            null, "##", "##", false, true)),

    /** Código C generado. */
    C(new Reglas(
            Set.of("if", "else", "while", "for", "do", "return", "goto", "break", "continue", "struct",
                    "typedef", "sizeof", "switch", "case", "default"),
            Set.of("int", "double", "char", "void", "float", "long", "short", "unsigned"),
            Set.of("NULL"),
            Set.of(), List.of(),
            "//", "/*", "*/", true, false)),

    /** Cuartetas (código de tres direcciones) en su forma de texto. */
    C3D(new Reglas(
            Set.of("goto", "if_false", "if_true", "call", "print", "read", "return", "halt", "func",
                    "endfunc", "param", "new", "newarray", "declare"),
            Set.of("ENTERO", "FLOTANTE", "CADENA", "CARACTER", "BOOL", "VOID", "ESTRUCTURA", "CLASE", "NULO"),
            Set.of(),
            Set.of(), List.of(),
            null, null, null, false, false)),

    NINGUNO(new Reglas(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), null, null, null, false, false));

    final Reglas reglas;

    ModoResaltado(Reglas reglas) {
        this.reglas = reglas;
    }

    public static ModoResaltado de(Lenguaje lenguaje) {
        return switch (lenguaje) {
            case Y -> Y;
            case ZETARIANO -> ZETARIANO;
            case PIG -> PIG;
        };
    }

    /** Todo el conjunto de reglas de un modo. */
    record Reglas(Set<String> palabrasClave, Set<String> tipos, Set<String> literales,
                  Set<String> palabrasDeSeccion, List<String> marcadoresDeSeccion,
                  String comentarioDeLinea, String comentarioInicio, String comentarioFin,
                  boolean preprocesador, boolean clasesEnMayuscula) {
    }
}
