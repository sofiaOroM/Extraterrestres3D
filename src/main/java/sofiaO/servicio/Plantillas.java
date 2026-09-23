package sofiaO.servicio;

import java.nio.file.Path;

/** Contenido inicial de los archivos nuevos: lo mínimo que ya compila. */
public final class Plantillas {

    private Plantillas() {}

    public static String para(Path archivo) {
        var lenguaje = Lenguaje.de(archivo);
        if (lenguaje.isEmpty()) return "";
        return switch (lenguaje.get()) {
            case Y -> """
                    %funciones
                        definir saludar():
                            imprimir("Hola")
                    """;
            case ZETARIANO -> {
                String clase = Lenguaje.nombreBase(archivo);
                yield "public class " + clase + " {\n"
                        + "    int valor;\n\n"
                        + "    public " + clase + "() {\n"
                        + "        valor = 0;\n"
                        + "    }\n"
                        + "}\n";
            }
            case PIG -> """
                    MAIOR>
                    >> "Hola comandante!" ;
                    FINIS;
                    """;
        };
    }
}
