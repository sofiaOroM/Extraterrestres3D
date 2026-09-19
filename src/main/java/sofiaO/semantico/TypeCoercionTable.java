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
}
