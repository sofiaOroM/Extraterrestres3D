package sofiaO.resaltado;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Junta el coloreado del lenguaje con el subrayado de errores en una sola lista de tramos
 * que cubre el texto completo, sin huecos ni solapes. Es la forma que necesita el editor.
 */
public final class Mezclador {

    private Mezclador() {}

    /**
     * @param longitud largo total del texto
     * @param sintaxis tramos coloreados, ordenados y sin solaparse (lo que devuelve el resaltador)
     * @param marcas   tramos de error; pueden solaparse entre sí
     */
    public static List<Tramo> particionar(int longitud, List<Segmento> sintaxis, List<Segmento> marcas) {
        TreeSet<Integer> cortes = new TreeSet<>();
        cortes.add(0);
        cortes.add(longitud);
        for (Segmento s : sintaxis) { cortes.add(acotar(s.inicio(), longitud)); cortes.add(acotar(s.fin(), longitud)); }
        for (Segmento s : marcas) { cortes.add(acotar(s.inicio(), longitud)); cortes.add(acotar(s.fin(), longitud)); }

        // Cobertura de errores con una tabla de diferencias: O(n + marcas).
        int[] diferencia = new int[longitud + 1];
        for (Segmento s : marcas) {
            int a = acotar(s.inicio(), longitud);
            int b = acotar(s.fin(), longitud);
            if (b > a) { diferencia[a]++; diferencia[b]--; }
        }

        List<Tramo> tramos = new ArrayList<>();
        Integer[] puntos = cortes.toArray(new Integer[0]);
        int puntero = 0;
        int cobertura = 0;
        int ultimoRecorrido = 0;

        for (int k = 0; k + 1 < puntos.length; k++) {
            int a = puntos[k];
            int b = puntos[k + 1];
            if (b <= a) continue;

            for (int j = ultimoRecorrido; j <= a; j++) cobertura += diferencia[j];
            ultimoRecorrido = a + 1;

            while (puntero < sintaxis.size() && sintaxis.get(puntero).fin() <= a) puntero++;
            String estiloSintaxis = null;
            if (puntero < sintaxis.size() && sintaxis.get(puntero).inicio() <= a) {
                estiloSintaxis = sintaxis.get(puntero).estilo();
            }

            List<String> estilos = new ArrayList<>(2);
            if (estiloSintaxis != null) estilos.add(estiloSintaxis);
            if (cobertura > 0) estilos.add(Estilos.ERROR);
            tramos.add(new Tramo(b - a, estilos));
        }
        return tramos;
    }

    private static int acotar(int valor, int maximo) {
        return Math.max(0, Math.min(valor, maximo));
    }
}
