package sofiaO.resaltado;

import sofiaO.servicio.ErrorCompilador;

import java.util.ArrayList;
import java.util.List;

/** Convierte (línea, columna, longitud) de cada error en posiciones dentro del texto. */
public final class MarcasDeError {

    private MarcasDeError() {}

    public static List<Segmento> rangos(String texto, List<ErrorCompilador> errores) {
        List<Segmento> rangos = new ArrayList<>();
        if (texto.isEmpty() || errores.isEmpty()) return rangos;

        List<Integer> inicios = new ArrayList<>();
        inicios.add(0);
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') inicios.add(i + 1);
        }

        for (ErrorCompilador e : errores) {
            if (e.linea > inicios.size()) continue;
            int inicioLinea = inicios.get(e.linea - 1);
            int finLinea = e.linea < inicios.size() ? inicios.get(e.linea) - 1 : texto.length();
            if (finLinea > inicioLinea && texto.charAt(finLinea - 1) == '\r') finLinea--;
            if (finLinea <= inicioLinea) continue; // línea vacía: no hay qué subrayar

            int a = inicioLinea + e.columna;
            int b = e.longitud > 0 ? a + e.longitud : finLinea;
            if (a >= finLinea) {                       // error justo al final de la línea (p. ej. falta ';')
                a = Math.max(inicioLinea, finLinea - 1);
                b = finLinea;
            }
            b = Math.min(b, finLinea);
            if (b > a) rangos.add(new Segmento(a, b, Estilos.ERROR));
        }
        return rangos;
    }
}
