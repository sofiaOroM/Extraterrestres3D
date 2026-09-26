package sofiaO.Traductor.emitters;

import sofiaO.semantico.ResolvedType;
import sofiaO.util.Type;

import java.util.Map;

/**
 * Todo lo que un emisor necesita para traducir una cuarteta a una línea de C.
 */
public interface EmissionContext {

    /** Tipo (ENTERO, CADENA, CLASE...) con el que se declaró ese lugar/temporal. */
    Type tipoDe(String lugar);

    /** Nombre de la función/método que se está emitiendo ahora mismo (ej. "Pila_apilar"). */
    String funcionActual();

    /** "this->campo" si lugar es this dentro de un método; si no, el nombre tal cual. */
    String resolverAtributo(String lugar);

    /** Nombre del tipo de usuario (Nodo, Persona...) asociado a un lugar, o null si es primitivo. */
    String tipoUsuarioDe(String lugar);

    /**
     * Los campos (y su tipo) de una clase o estructura declarada por el usuario.
     */
    Map<String, ResolvedType> camposDe(String nombreTipoUsuario);
}