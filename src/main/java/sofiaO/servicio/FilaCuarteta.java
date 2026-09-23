package sofiaO.servicio;

/** Una cuarteta lista para mostrarse en una tabla: (operador, arg1, arg2, resultado). */
public record FilaCuarteta(int indice, String operador, String arg1, String arg2,
                           String resultado, String instruccion) {
}
