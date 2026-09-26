package sofiaO.servicio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Compila el código C generado (con gcc, si está disponible) y prepara el proceso
 * para ejecutarlo. La ejecución en sí (leer su salida, mandarle entrada, etc.) la
 * maneja quien reciba el {@link Process}; aquí solo se resuelve el compilador y se
 * arma la carpeta temporal de trabajo.
 */
public final class EjecutorC {

    private EjecutorC() {}

    /** Compilador con el que se intenta compilar, en orden de preferencia. */
    private static final List<String> CANDIDATOS_COMPILADOR = List.of("gcc", "cc", "clang");

    public static final class ResultadoCompilacionC {
        public final boolean exito;
        public final String mensaje;
        public final Path ejecutable;
        public final Path carpetaTemporal;

        private ResultadoCompilacionC(boolean exito, String mensaje, Path ejecutable, Path carpetaTemporal) {
            this.exito = exito;
            this.mensaje = mensaje;
            this.ejecutable = ejecutable;
            this.carpetaTemporal = carpetaTemporal;
        }
    }

    /** Busca en el PATH el primer compilador de C disponible entre los candidatos conocidos. */
    public static String buscarCompilador() {
        String extra = System.getenv("PATH");
        for (String candidato : CANDIDATOS_COMPILADOR) {
            if (existeEnPath(candidato, extra)) return candidato;
        }
        return null;
    }

    private static boolean existeEnPath(String comando, String path) {
        if (path == null) return false;
        String nombre = esWindows() ? comando + ".exe" : comando;
        for (String carpeta : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (carpeta.isBlank()) continue;
            if (Files.isExecutable(Path.of(carpeta, nombre))) return true;
        }
        return false;
    }

    private static boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /**
     * Inserta, justo al inicio de {@code main()}, una línea que desactiva el buffer de stdout.
     * <p>
     * Es necesario porque cuando el programa no corre en una terminal real sino conectado a
     * una tubería (como aquí), la librería C usa buffer completo por defecto: los "printf" de
     * las preguntas ("Ingresa tu nombre...") se quedan guardados y no se ven hasta que el
     * programa termina o el buffer se llena, aunque el scanf ya esté esperando datos. Sin esto,
     * el usuario tendría que adivinar qué escribir antes de ver la pregunta.
     * Solo afecta a la copia que se compila para ejecutar; el código que se ve/copia/guarda
     * en la pestaña "Código C" no se toca.
     */
    private static String prepararParaEjecucion(String codigoC) {
        String marcador = "int main() {";
        int idx = codigoC.indexOf(marcador);
        String linea = "\n    setvbuf(stdout, NULL, _IONBF, 0);";
        if (idx >= 0) {
            int pos = idx + marcador.length();
            return codigoC.substring(0, pos) + linea + codigoC.substring(pos);
        }
        // Por si alguna vez cambia el formato exacto de main(): mismo efecto, sin depender del texto.
        return codigoC + "\n\n#if defined(__GNUC__) || defined(__clang__)\n__attribute__((constructor))\n#endif\n"
                + "static void __extraterrestre3d_sin_buffer(void) { setvbuf(stdout, NULL, _IONBF, 0); }\n";
    }

    /**
     * Escribe {@code codigoC} en una carpeta temporal y lo compila.
     * No lanza excepción por errores de compilación: eso queda reflejado en el resultado.
     */
    public static ResultadoCompilacionC compilar(String codigoC, String nombreBase) throws IOException {
        String compilador = buscarCompilador();
        if (compilador == null) {
            return new ResultadoCompilacionC(false,
                    "No se encontró un compilador de C (gcc, cc o clang) en el PATH del sistema.\n"
                            + "Instala uno para poder ejecutar el código generado:\n"
                            + "  • Windows: MinGW-w64 o MSYS2 (paquete mingw-w64-gcc)\n"
                            + "  • macOS: Xcode Command Line Tools (xcode-select --install)\n"
                            + "  • Linux: paquete gcc de tu distribución",
                    null, null);
        }

        Path carpetaTemporal = Files.createTempDirectory("extraterrestre3d_");
        String nombreEjecutable = esWindows() ? nombreBase + ".exe" : nombreBase;
        Path fuente = carpetaTemporal.resolve(nombreBase + ".c");
        Path ejecutable = carpetaTemporal.resolve(nombreEjecutable);
        Files.writeString(fuente, prepararParaEjecucion(codigoC), StandardCharsets.UTF_8);

        ProcessBuilder pb = new ProcessBuilder(compilador, fuente.getFileName().toString(),
                "-o", ejecutable.getFileName().toString(), "-lm");
        pb.directory(carpetaTemporal.toFile());
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        String salida;
        try (var entrada = proceso.getInputStream()) {
            salida = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean termino;
        try {
            termino = proceso.waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            borrarCarpeta(carpetaTemporal);
            throw new IOException("Se interrumpió la compilación.", e);
        }
        if (!termino) {
            proceso.destroyForcibly();
            borrarCarpeta(carpetaTemporal);
            return new ResultadoCompilacionC(false, "El compilador tardó demasiado y se canceló.", null, null);
        }
        if (proceso.exitValue() != 0 || !Files.exists(ejecutable)) {
            borrarCarpeta(carpetaTemporal);
            String mensaje = salida.isBlank() ? "El compilador terminó con errores." : salida;
            return new ResultadoCompilacionC(false, mensaje, null, null);
        }
        return new ResultadoCompilacionC(true, salida, ejecutable, carpetaTemporal);
    }

    /** Arranca el ejecutable ya compilado, con sus flujos conectados por tuberías para poder interactuar con él. */
    public static Process ejecutar(Path ejecutable) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(ejecutable.toAbsolutePath().toString());
        pb.directory(ejecutable.getParent().toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    public static void borrarCarpeta(Path carpeta) {
        if (carpeta == null) return;
        try (Stream<Path> recorrido = Files.walk(carpeta)) {
            recorrido.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            // Es una carpeta temporal; si no se pudo limpiar, no es grave.
        }
    }
}
