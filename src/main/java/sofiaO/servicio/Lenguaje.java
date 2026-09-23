package sofiaO.servicio;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Los tres lenguajes de entrada del proyecto, identificados por la extensión del archivo. */
public enum Lenguaje {
    Y("Y?", "y"),
    ZETARIANO("Zetariano", "z"),
    PIG("Pig Latin", "pig");

    public final String nombre;
    public final String extension;

    Lenguaje(String nombre, String extension) {
        this.nombre = nombre;
        this.extension = extension;
    }

    public static Optional<Lenguaje> de(Path archivo) {
        if (archivo == null || archivo.getFileName() == null) return Optional.empty();
        String nombre = archivo.getFileName().toString().toLowerCase(Locale.ROOT);
        int punto = nombre.lastIndexOf('.');
        if (punto < 0) return Optional.empty();
        String ext = nombre.substring(punto + 1);
        for (Lenguaje l : values()) {
            if (l.extension.equals(ext)) return Optional.of(l);
        }
        return Optional.empty();
    }

    /** Nombre del archivo sin extensión (Persona.z -> Persona). */
    public static String nombreBase(Path archivo) {
        String nombre = archivo.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? nombre : nombre.substring(0, punto);
    }
}
