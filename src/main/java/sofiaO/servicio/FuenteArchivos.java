package sofiaO.servicio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * De dónde sale el texto de cada archivo al compilar. La interfaz gráfica entrega aquí
 * lo que hay en los editores (aunque no esté guardado); si el archivo no está abierto,
 * se lee de disco.
 */
@FunctionalInterface
public interface FuenteArchivos {

    String leer(Path archivo) throws IOException;

    static FuenteArchivos deDisco() {
        return archivo -> Files.readString(archivo, StandardCharsets.UTF_8);
    }
}
