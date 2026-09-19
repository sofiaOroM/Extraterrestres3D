package sofiaO.semantico;

import sofiaO.util.Type;

/**
 * Un tipo completamente resuelto: el Type genérico, más el nombre real
 * cuando se trata de una estructura o clase de usuario.
 *
 * Por qué existe: Type.ESTRUCTURA por sí solo no alcanza para saber si
 * algo es un "Persona" o un "Punto" -- ambos serían el mismo enum. Este
 * record es lo que sí distingue "estructura, y se llama Persona" de
 * "estructura, y se llama Punto".
 */
public record ResolvedType(Type tipo, String tipoUsuario) {

    public static ResolvedType primitivo(Type t) {
        return new ResolvedType(t, null);
    }

    public static final ResolvedType ERROR = new ResolvedType(Type.ERROR, null);

    public String etiqueta() {
        return tipoUsuario != null ? tipoUsuario : tipo.toString();
    }
}
