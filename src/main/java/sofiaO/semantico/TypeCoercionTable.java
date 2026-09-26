package sofiaO.semantico;

import sofiaO.util.Type;

import java.util.Objects;

public class TypeCoercionTable {

    public static boolean compatibles(Type destino, Type origen) {
        if (destino == origen) return true;
        if (destino == Type.FLOTANTE && origen == Type.ENTERO) return true;
        return false;
    }

    public static boolean compatibles(Type destino, String tipoUsuarioDestino,
                                      Type origen, String tipoUsuarioOrigen) {
        if (destino == Type.ESTRUCTURA || destino == Type.CLASE) {
            if (origen == Type.NULO) return true; // a un struct/objeto SÍ se le puede asignar null
            return destino == origen && Objects.equals(tipoUsuarioDestino, tipoUsuarioOrigen);
        }
        return compatibles(destino, origen);
    }

    public static Type resultado(Type izq, Type der) {
        if (izq == Type.ERROR || der == Type.ERROR) return Type.ERROR;
        // Las estructuras/clases NO participan en aritmética (+,-,*,/,%).
        // La comparación == / != con estructuras/clases/null se resuelve
        // aparte, directamente en SemanticAnalyzer.visit(BinaryExprNode),
        // porque ahí sí hace falta comparar tipoUsuario, no solo Type.
        if (izq == Type.ESTRUCTURA || izq == Type.CLASE || der == Type.ESTRUCTURA || der == Type.CLASE) {
            return Type.ERROR;
        }
        if (izq == der) return izq;
        if ((izq == Type.ENTERO && der == Type.FLOTANTE) || (izq == Type.FLOTANTE && der == Type.ENTERO)) {
            return Type.FLOTANTE;
        }
        return Type.ERROR;
    }

    // ------------------------------------------------------------------
    // Operador '+' : suma numérica O concatenación, según los tipos.
    //
    //   CADENA + (CADENA|ENTERO|FLOTANTE|CARACTER|BOOL)  -> CADENA  (en cualquier orden)
    //   ENTERO + ENTERO                                  -> ENTERO
    //   ENTERO + FLOTANTE / FLOTANTE + FLOTANTE          -> FLOTANTE
    //   CARACTER + CARACTER                              -> CARACTER (se mantiene lo que ya había)
    //   BOOL + algo que no sea cadena                    -> ERROR
    //   ESTRUCTURA / CLASE / NULO / VOID con cualquiera  -> ERROR
    //
    // Como la evaluación es de izquierda a derecha, "a" + 1 + 2 da "a12"
    // y 1 + 2 + "a" da "3a" (igual que Java).
    // ------------------------------------------------------------------
    public static Type resultadoSuma(Type izq, Type der) {
        if (izq == Type.ERROR || der == Type.ERROR) return Type.ERROR;
        if (izq == Type.CADENA || der == Type.CADENA) {
            return (esConcatenable(izq) && esConcatenable(der)) ? Type.CADENA : Type.ERROR;
        }
        if (!esNumericoOCaracter(izq) || !esNumericoOCaracter(der)) return Type.ERROR;
        return resultado(izq, der);
    }

    /** Resultado de - * / % : solo números (y caracteres), nunca cadenas ni booleanos. */
    public static Type resultadoAritmetico(Type izq, Type der) {
        if (izq == Type.ERROR || der == Type.ERROR) return Type.ERROR;
        if (!esNumericoOCaracter(izq) || !esNumericoOCaracter(der)) return Type.ERROR;
        return resultado(izq, der);
    }

    private static boolean esConcatenable(Type t) {
        return t == Type.CADENA || t == Type.ENTERO || t == Type.FLOTANTE
                || t == Type.CARACTER || t == Type.BOOL;
    }

    private static boolean esNumericoOCaracter(Type t) {
        return t == Type.ENTERO || t == Type.FLOTANTE || t == Type.CARACTER;
    }
}