package sofiaO.servicio;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.nio.file.Path;
import java.util.List;

/** Guarda los errores de ANTLR en una lista en lugar de imprimirlos por consola. */
final class ColectorErroresAntlr extends BaseErrorListener {

    private final List<ErrorCompilador> destino;
    private final Path archivo;
    private final ErrorCompilador.Tipo tipo;

    ColectorErroresAntlr(List<ErrorCompilador> destino, Path archivo, ErrorCompilador.Tipo tipo) {
        this.destino = destino;
        this.archivo = archivo;
        this.tipo = tipo;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object simboloProblematico,
                            int linea, int columna, String mensaje, RecognitionException e) {
        int longitud = 1;
        if (simboloProblematico instanceof Token t && t.getType() != Token.EOF && t.getText() != null) {
            String texto = t.getText();
            // Los saltos de línea (y la sangría que los sigue) no se subrayan completos.
            longitud = texto.isBlank() ? 1 : Math.max(1, texto.length());
        }

        // "Falta ';'": ANTLR lo reporta en el token siguiente (a veces en otra línea).
        // Para el usuario el error está justo después del último token que sí llegó.
        if (mensaje.startsWith("missing ") && recognizer instanceof Parser parser
                && simboloProblematico instanceof Token t && t.getTokenIndex() > 0) {
            TokenStream tokens = parser.getInputStream();
            Token anterior = tokens.get(t.getTokenIndex() - 1);
            String textoAnterior = anterior.getText();
            if (textoAnterior != null && !textoAnterior.isBlank() && !textoAnterior.contains("\n")) {
                linea = anterior.getLine();
                columna = anterior.getCharPositionInLine() + textoAnterior.length();
                longitud = 1;
            }
        }

        destino.add(new ErrorCompilador(tipo, TraductorMensajes.traducir(mensaje), archivo, linea, columna, longitud));
    }
}
