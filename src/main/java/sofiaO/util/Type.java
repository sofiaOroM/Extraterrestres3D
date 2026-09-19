package sofiaO.util;

public enum Type {
    ENTERO, FLOTANTE, CADENA, CARACTER, BOOL, VOID,
    ESTRUCTURA,  // el tipo REAL (nombre) se guarda aparte, en Symbol.tipoUsuario / BaseNode.tipoUsuarioResuelto
    CLASE,       // idem
    NULO,        // tipo del literal 'null' (Zetariano)
    ERROR;

    public String aC() {
        return switch (this) {
            case ENTERO -> "int";
            case FLOTANTE -> "double";
            case CADENA -> "char*";
            case CARACTER -> "char";
            case BOOL -> "int";
            case VOID -> "void";
            case ESTRUCTURA, CLASE -> "void*";
            case NULO -> "void*";
            case ERROR -> "double";
        };
    }

    public String formatoPrintf() {
        return switch (this) {
            case ENTERO, BOOL -> "%d";
            case FLOTANTE -> "%g";
            case CADENA -> "%s";
            case CARACTER -> "%c";
            case ESTRUCTURA, CLASE, NULO -> "%p";
            default -> "%g";
        };
    }
}
