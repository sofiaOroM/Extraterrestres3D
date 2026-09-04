package sofiaO.semantico;

import sofiaO.util.Type;

public class TypeCoercionTable {

    // Revisa si el tipoOrigen se puede asignar a un destino
    public static boolean compatibles(Type destino, Type origen) {
        if (destino == origen) return true;

        // Coerción implícita: un ENTERO cabe en un FLOTANTE
        if (destino == Type.FLOTANTE && origen == Type.ENTERO) {
            return true;
        }

        return false;
    }

    // Determina el tipo resultante de operaciones (+, -, *, /)
    public static Type resultado(Type izq, Type der) {
        if (izq == Type.ERROR || der == Type.ERROR) return Type.ERROR;

        // Operaciones entre mismos tipos
        if (izq == der) return izq;

        // Promoción implícita
        if ((izq == Type.ENTERO && der == Type.FLOTANTE) ||
                (izq == Type.FLOTANTE && der == Type.ENTERO)) {
            return Type.FLOTANTE;
        }

        return Type.ERROR; // Operación no válida (ej. ENTERO + CADENA)
    }
}