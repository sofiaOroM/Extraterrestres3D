package sofiaO.servicio;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Todo lo que la interfaz necesita mostrar después de compilar. */
public final class ResultadoCompilacion {

    public final List<ErrorCompilador> errores = new ArrayList<>();
    public final List<FilaCuarteta> cuartetas = new ArrayList<>();
    /** Las cuartetas como texto, una instrucción por línea. */
    public String cuartetasTexto = "";
    public String codigoC = "";
    /** Archivos que participaron (el principal y los importados), en el orden en que se procesaron. */
    public final List<Path> archivos = new ArrayList<>();
    /** Estructuras, clases y funciones encontradas. */
    public final List<String> resumen = new ArrayList<>();

    public boolean exitoso() { return errores.isEmpty(); }

    public boolean tieneCodigo() { return !codigoC.isEmpty(); }

    public long contar(ErrorCompilador.Tipo tipo) {
        return errores.stream().filter(e -> e.tipo == tipo).count();
    }
}
