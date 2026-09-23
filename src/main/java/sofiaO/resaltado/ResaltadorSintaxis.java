package sofiaO.resaltado;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Coloreado propio, escrito a mano (sin librerías de resaltado): recorre el texto una sola
 * vez y devuelve los tramos que hay que pintar. Es rápido y no depende de la interfaz, así
 * que se puede llamar en cada cambio del editor.
 */
public final class ResaltadorSintaxis {

    private ResaltadorSintaxis() {}

    private static final String[] OPERADORES_DOBLES = {
            "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "->", ">>", "<<"};
    private static final String OPERADORES_SIMPLES = "+-*/%<>=!?:";
    private static final Pattern TEMPORAL = Pattern.compile("t\\d+");
    private static final Pattern ETIQUETA = Pattern.compile("L\\d+");

    public static List<Segmento> resaltar(String texto, ModoResaltado modo) {
        List<Segmento> salida = new ArrayList<>();
        if (texto == null || texto.isEmpty() || modo == ModoResaltado.NINGUNO) return salida;

        ModoResaltado.Reglas reglas = modo.reglas;
        int n = texto.length();
        int i = 0;

        while (i < n) {
            char c = texto.charAt(i);

            if (Character.isWhitespace(c)) { i++; continue; }

            // ---- comentarios ----
            if (reglas.comentarioInicio() != null && texto.startsWith(reglas.comentarioInicio(), i)) {
                int cierre = texto.indexOf(reglas.comentarioFin(), i + reglas.comentarioInicio().length());
                int fin = cierre < 0 ? n : cierre + reglas.comentarioFin().length();
                salida.add(new Segmento(i, fin, Estilos.COMENTARIO));
                i = fin;
                continue;
            }
            if (reglas.comentarioDeLinea() != null && texto.startsWith(reglas.comentarioDeLinea(), i)) {
                int fin = finDeLinea(texto, i);
                salida.add(new Segmento(i, fin, Estilos.COMENTARIO));
                i = fin;
                continue;
            }
            if (reglas.preprocesador() && c == '#') {
                int fin = finDeLinea(texto, i);
                salida.add(new Segmento(i, fin, Estilos.SECCION));
                i = fin;
                continue;
            }

            // ---- cadenas y caracteres (si no cierran, se pintan hasta el fin de línea) ----
            if (c == '"' || c == '\'') {
                int fin = finDeLiteral(texto, i, c);
                salida.add(new Segmento(i, fin, Estilos.CADENA));
                i = fin;
                continue;
            }

            // ---- marcadores de sección: %estructuras, VARIABILES>, MAIOR> ... ----
            String marcador = marcadorEn(texto, i, reglas);
            if (marcador != null) {
                salida.add(new Segmento(i, i + marcador.length(), Estilos.SECCION));
                i += marcador.length();
                continue;
            }

            // ---- números ----
            if (Character.isDigit(c)) {
                int fin = i;
                while (fin < n && Character.isDigit(texto.charAt(fin))) fin++;
                if (fin + 1 < n && texto.charAt(fin) == '.' && Character.isDigit(texto.charAt(fin + 1))) {
                    fin++;
                    while (fin < n && Character.isDigit(texto.charAt(fin))) fin++;
                }
                salida.add(new Segmento(i, fin, Estilos.NUMERO));
                i = fin;
                continue;
            }

            // ---- identificadores y palabras reservadas ----
            if (Character.isLetter(c) || c == '_') {
                int fin = i;
                while (fin < n && (Character.isLetterOrDigit(texto.charAt(fin)) || texto.charAt(fin) == '_')) fin++;
                String palabra = texto.substring(i, fin);
                String estilo = clasificar(palabra, texto, fin, modo, reglas);
                if (estilo != null) salida.add(new Segmento(i, fin, estilo));
                i = fin;
                continue;
            }

            // ---- operadores ----
            String operador = operadorEn(texto, i);
            if (operador != null) {
                salida.add(new Segmento(i, i + operador.length(), Estilos.OPERADOR));
                i += operador.length();
                continue;
            }

            i++;
        }
        return salida;
    }

    private static String clasificar(String palabra, String texto, int fin, ModoResaltado modo,
                                     ModoResaltado.Reglas reglas) {
        if (reglas.palabrasDeSeccion().contains(palabra)) return Estilos.SECCION;
        if (reglas.palabrasClave().contains(palabra)) return Estilos.PALABRA_CLAVE;
        if (reglas.tipos().contains(palabra)) return Estilos.TIPO;
        if (reglas.literales().contains(palabra)) return Estilos.LITERAL;
        if (modo == ModoResaltado.C3D) {
            if (TEMPORAL.matcher(palabra).matches()) return Estilos.TEMPORAL;
            if (ETIQUETA.matcher(palabra).matches()) return Estilos.ETIQUETA;
            return null;
        }
        if (siguienteCaracterUtil(texto, fin) == '(') return Estilos.FUNCION;
        if (reglas.clasesEnMayuscula() && Character.isUpperCase(palabra.charAt(0))) return Estilos.CLASE;
        return null;
    }

    private static char siguienteCaracterUtil(String texto, int desde) {
        for (int j = desde; j < texto.length(); j++) {
            char c = texto.charAt(j);
            if (c == ' ' || c == '\t') continue;
            return c;
        }
        return '\0';
    }

    private static String marcadorEn(String texto, int i, ModoResaltado.Reglas reglas) {
        for (String m : reglas.marcadoresDeSeccion()) {
            if (texto.startsWith(m, i)) return m;
        }
        return null;
    }

    private static String operadorEn(String texto, int i) {
        for (String op : OPERADORES_DOBLES) {
            if (texto.startsWith(op, i)) return op;
        }
        char c = texto.charAt(i);
        return OPERADORES_SIMPLES.indexOf(c) >= 0 ? String.valueOf(c) : null;
    }

    private static int finDeLinea(String texto, int desde) {
        int fin = texto.indexOf('\n', desde);
        if (fin < 0) return texto.length();
        return fin > desde && texto.charAt(fin - 1) == '\r' ? fin - 1 : fin;
    }

    private static int finDeLiteral(String texto, int inicio, char comilla) {
        int n = texto.length();
        int j = inicio + 1;
        while (j < n) {
            char c = texto.charAt(j);
            if (c == '\\') { j += 2; continue; }
            if (c == comilla) return j + 1;
            if (c == '\n' || c == '\r') return j;
            j++;
        }
        return n;
    }
}
