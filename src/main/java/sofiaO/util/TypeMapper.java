package sofiaO.util;

public class TypeMapper {
    public static Type deTexto(String palabraDeTipo) {
        return switch (palabraDeTipo) {
            case "entero", "int", "numerus" -> Type.ENTERO;
            case "flotante", "double", "decimalis" -> Type.FLOTANTE;
            case "cadena", "String", "textum" -> Type.CADENA;
            case "caracter", "char", "littera" -> Type.CARACTER;
            case "bool", "boolean" -> Type.BOOL;
            default -> Type.ERROR; // tipo de usuario (struct/clase) -> se resuelve aparte
        };
    }
}
