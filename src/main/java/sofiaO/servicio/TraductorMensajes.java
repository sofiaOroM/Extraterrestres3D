package sofiaO.servicio;

import java.util.Map;
import java.util.regex.Pattern;

/** Convierte los mensajes de ANTLR (en inglés y con nombres internos de tokens) a texto entendible. */
final class TraductorMensajes {

    private TraductorMensajes() {}

    private static final Map<String, String> NOMBRES_TOKEN = Map.ofEntries(
            Map.entry("NL", "salto de línea"),
            Map.entry("INDENT", "sangría"),
            Map.entry("DEDENT", "fin de sangría"),
            Map.entry("ID", "identificador"),
            Map.entry("ENTERO", "número entero"),
            Map.entry("DECIMAL", "número decimal"),
            Map.entry("CADENA_LITERAL", "cadena de texto"),
            Map.entry("CARACTER_LITERAL", "carácter"),
            Map.entry("<EOF>", "fin del archivo"));

    private static final Pattern SALTO = Pattern.compile("'(\\\\r)?\\\\n[ \\t]*'");

    static String traducir(String mensaje) {
        String m = mensaje == null ? "" : mensaje;

        m = SALTO.matcher(m).replaceAll("un salto de línea");

        m = m.replaceFirst("^token recognition error at: (.*)$", "Carácter o símbolo no reconocido: $1");
        m = m.replaceFirst("^mismatched input (.*?) expecting (.*)$", "Se encontró $1 pero se esperaba $2");
        m = m.replaceFirst("^extraneous input (.*?) expecting (.*)$", "$1 sobra en este punto; se esperaba $2");
        m = m.replaceFirst("^missing (.*?) at (.*)$", "Falta $1 antes de $2");
        m = m.replaceFirst("^no viable alternative at input (.*)$", "No se reconoce la instrucción cerca de $1");
        m = m.replaceFirst("^mismatched character (.*?) expecting (.*)$", "Se encontró $1 pero se esperaba $2");

        for (Map.Entry<String, String> e : NOMBRES_TOKEN.entrySet()) {
            String token = e.getKey();
            String patron = token.startsWith("<")
                    ? Pattern.quote(token)
                    : "(?<![A-Za-z_'])" + token + "(?![A-Za-z_'])";
            m = m.replaceAll(patron, e.getValue());
        }
        return m;
    }
}
