package sofiaO.servicio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/** Operaciones sobre el árbol de trabajo: crear, renombrar, eliminar y copiar archivos y carpetas. */
public final class OperacionesArchivo {

    private OperacionesArchivo() {}

    public static String leerTexto(Path archivo) throws IOException {
        return Files.readString(archivo, StandardCharsets.UTF_8);
    }

    public static void escribirTexto(Path archivo, String texto) throws IOException {
        Files.writeString(archivo, texto, StandardCharsets.UTF_8);
    }

    /** Crea un archivo nuevo con una plantilla mínima válida según su extensión. */
    public static Path crearArchivo(Path carpeta, String nombre) throws IOException {
        Path destino = carpeta.resolve(nombre);
        if (Files.exists(destino)) throw new FileAlreadyExistsException(destino.toString());
        Files.writeString(destino, Plantillas.para(destino), StandardCharsets.UTF_8);
        return destino;
    }

    public static Path crearCarpeta(Path carpeta, String nombre) throws IOException {
        Path destino = carpeta.resolve(nombre);
        if (Files.exists(destino)) throw new FileAlreadyExistsException(destino.toString());
        return Files.createDirectory(destino);
    }

    public static Path renombrar(Path origen, String nuevoNombre) throws IOException {
        Path destino = origen.resolveSibling(nuevoNombre);
        if (Files.exists(destino)) throw new FileAlreadyExistsException(destino.toString());
        return Files.move(origen, destino);
    }

    public static void eliminar(Path ruta) throws IOException {
        if (!Files.isDirectory(ruta)) {
            Files.deleteIfExists(ruta);
            return;
        }
        try (Stream<Path> recorrido = Files.walk(ruta)) {
            recorrido.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Copia un archivo o una carpeta completa dentro de {@code carpetaDestino}
     * (es lo que hace la opción "Descargar"). Devuelve la ruta de la copia.
     */
    public static Path copiarA(Path origen, Path carpetaDestino) throws IOException {
        Path destino = carpetaDestino.resolve(origen.getFileName().toString());
        if (destino.toAbsolutePath().normalize().startsWith(origen.toAbsolutePath().normalize())
                && Files.isDirectory(origen)) {
            throw new IOException("No se puede copiar una carpeta dentro de sí misma.");
        }
        if (!Files.isDirectory(origen)) {
            Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
            return destino;
        }
        try (Stream<Path> recorrido = Files.walk(origen)) {
            recorrido.forEach(p -> {
                try {
                    Path objetivo = destino.resolve(origen.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(objetivo);
                    else Files.copy(p, objetivo, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return destino;
    }
}
