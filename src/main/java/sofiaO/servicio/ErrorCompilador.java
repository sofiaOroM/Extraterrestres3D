package sofiaO.servicio;

import java.nio.file.Path;

/** Un error léxico, sintáctico o semántico, con su ubicación exacta dentro de un archivo. */
public final class ErrorCompilador {

    public enum Tipo {
        LEXICO("Léxico"),
        SINTACTICO("Sintáctico"),
        SEMANTICO("Semántico"),
        INTERNO("Interno");

        public final String etiqueta;
        Tipo(String etiqueta) { this.etiqueta = etiqueta; }
    }

    public final Tipo tipo;
    public final String descripcion;
    public final Path archivo;
    /** Línea, empezando en 1. */
    public final int linea;
    /** Columna, empezando en 0. */
    public final int columna;
    /** Cantidad de caracteres a subrayar en el editor; 0 significa "hasta el final de la línea". */
    public final int longitud;

    public ErrorCompilador(Tipo tipo, String descripcion, Path archivo, int linea, int columna, int longitud) {
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.archivo = archivo;
        this.linea = Math.max(1, linea);
        this.columna = Math.max(0, columna);
        this.longitud = Math.max(0, longitud);
    }

    public String nombreArchivo() {
        return archivo == null ? "" : archivo.getFileName().toString();
    }

    @Override
    public String toString() {
        return tipo.etiqueta + " en " + nombreArchivo() + " [" + linea + ":" + (columna + 1) + "]: " + descripcion;
    }
}
