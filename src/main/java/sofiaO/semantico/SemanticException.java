package sofiaO.semantico;

public class SemanticException extends RuntimeException {

    private int linea = -1;
    private int columna = -1;

    public SemanticException(String mensaje) {
        super(mensaje);
    }

    public SemanticException(String mensaje, int linea, int columna) {
        super(mensaje);
        this.linea = linea;
        this.columna = columna;
    }

    /** Línea (1-based) donde ocurrió el error, o -1 si aún no se conoce. */
    public int getLinea() { return linea; }

    /** Columna (0-based) donde ocurrió el error, o -1 si aún no se conoce. */
    public int getColumna() { return columna; }

    public boolean tieneUbicacion() { return linea > 0; }

    public void setUbicacion(int linea, int columna) {
        this.linea = linea;
        this.columna = columna;
    }
}
