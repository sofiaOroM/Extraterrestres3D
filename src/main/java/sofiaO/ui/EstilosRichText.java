package sofiaO.ui;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import sofiaO.resaltado.Mezclador;
import sofiaO.resaltado.Segmento;
import sofiaO.resaltado.Tramo;

import java.util.Collection;
import java.util.List;

/** Traduce los tramos calculados por el resaltador al formato que entiende el CodeArea. */
final class EstilosRichText {

    private EstilosRichText() {}

    /** El texto no puede estar vacío (un StyleSpans vacío no se puede crear). */
    static StyleSpans<Collection<String>> construir(int longitud, List<Segmento> sintaxis, List<Segmento> marcas) {
        StyleSpansBuilder<Collection<String>> constructor = new StyleSpansBuilder<>();
        for (Tramo tramo : Mezclador.particionar(longitud, sintaxis, marcas)) {
            constructor.add(tramo.estilos(), tramo.longitud());
        }
        return constructor.create();
    }
}
