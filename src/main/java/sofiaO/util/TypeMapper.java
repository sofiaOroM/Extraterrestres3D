package sofiaO.util;

import java.util.HashMap;
import java.util.Map;

public class TypeMapper {

    // Mapa de tipos de usuario (structs, clases)
    private static final Map<String, Type> tiposUsuario = new HashMap<>();
    private static final Map<String, String> nombresUsuario = new HashMap<>();

    public static Type deTexto(String palabraDeTipo) {
        return switch (palabraDeTipo) {
            case "entero", "int", "numerus" -> Type.ENTERO;
            case "flotante", "double", "decimalis" -> Type.FLOTANTE;
            case "cadena", "String", "textum" -> Type.CADENA;
            case "caracter", "char", "littera" -> Type.CARACTER;
            case "bool", "boolean" -> Type.BOOL;
            default -> Type.ERROR; // si no es básico, se intenta con deUsuario
        };
    }

    /** Registra un nuevo tipo de usuario (struct/clase) */
    public static void registrarTipoUsuario(String nombre, Type tipo) {
        tiposUsuario.put(nombre, tipo);
        nombresUsuario.put(nombre, nombre);
    }

    public static void reiniciar() {
        tiposUsuario.clear();
        nombresUsuario.clear();
    }

    public static String nombreReal(String nombre) {
        return nombresUsuario.getOrDefault(nombre, nombre);
    }

    /** Devuelve el tipo de usuario registrado, o ERROR si no existe */
    public static Type deUsuario(String nombre) {
        return tiposUsuario.getOrDefault(nombre, Type.ERROR);
    }
}
