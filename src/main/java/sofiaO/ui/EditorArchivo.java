package sofiaO.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import sofiaO.resaltado.MarcasDeError;
import sofiaO.resaltado.ModoResaltado;
import sofiaO.resaltado.ResaltadorSintaxis;
import sofiaO.resaltado.Segmento;
import sofiaO.servicio.ErrorCompilador;
import sofiaO.servicio.Lenguaje;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Una pestaña con el editor de un archivo: coloreado en tiempo real, números de línea y errores subrayados. */
class EditorArchivo extends Tab {

    private static final Duration ESPERA_RESALTADO = Duration.ofMillis(60);

    private final CodeArea area = new CodeArea();
    private Path ruta;
    private boolean modificado;
    private boolean cargando;

    private List<ErrorCompilador> errores = List.of();
    private boolean erroresVigentes;

    private Runnable alCambiar = () -> {};
    private Runnable alMoverCursor = () -> {};

    EditorArchivo(Path ruta, String texto, IntegerProperty tamanoFuente) {
        this.ruta = ruta;

        area.getStyleClass().add("editor");
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        area.styleProperty().bind(Bindings.concat("-fx-font-size: ", tamanoFuente.asString(), "px;"));
        setContent(new VirtualizedScrollPane<>(area));

        cargando = true;
        area.replaceText(texto);
        area.getUndoManager().forgetHistory();
        cargando = false;

        aplicarResaltado();

        // El coloreado se recalcula al terminar una ráfaga de cambios (60 ms): se siente inmediato.
        area.multiPlainChanges().successionEnds(ESPERA_RESALTADO).subscribe(cambios -> aplicarResaltado());
        area.plainTextChanges().subscribe(cambio -> {
            if (cargando) return;
            modificado = true;
            erroresVigentes = false; // las posiciones de los errores ya no son confiables
            actualizarTitulo();
            alCambiar.run();
        });
        area.caretPositionProperty().addListener((obs, anterior, nuevo) -> alMoverCursor.run());

        // Enter conserva la sangría de la línea anterior; Tab inserta 4 espacios.
        // Importa sobre todo en Y?, donde la sangría define la estructura del código.
        Nodes.addInputMap(area, InputMap.consume(EventPattern.keyPressed(KeyCode.ENTER), e -> saltoConSangria()));
        Nodes.addInputMap(area, InputMap.consume(EventPattern.keyPressed(KeyCode.TAB), e -> area.replaceSelection("    ")));

        actualizarTitulo();
    }

    // ---------------- estado ----------------

    Path getRuta() { return ruta; }

    String getTexto() { return area.getText(); }

    boolean estaModificado() { return modificado; }

    CodeArea getArea() { return area; }

    void setAlCambiar(Runnable accion) { this.alCambiar = accion; }

    void setAlMoverCursor(Runnable accion) { this.alMoverCursor = accion; }

    void marcarGuardado() {
        modificado = false;
        actualizarTitulo();
    }

    void setRuta(Path nueva) {
        this.ruta = nueva;
        actualizarTitulo();
        aplicarResaltado(); // la extensión pudo cambiar el lenguaje
    }

    String posicionDelCursor() {
        return "Ln " + (area.getCurrentParagraph() + 1) + ", Col " + (area.getCaretColumn() + 1);
    }

    String nombreDelLenguaje() {
        return Lenguaje.de(ruta).map(l -> l.nombre).orElse("Texto");
    }

    void enfocar() {
        Platform.runLater(area::requestFocus);
    }

    // ---------------- errores ----------------

    /** Subraya los errores de este archivo hasta que el usuario vuelva a editar el texto. */
    void mostrarErrores(List<ErrorCompilador> delArchivo) {
        this.errores = delArchivo;
        this.erroresVigentes = !delArchivo.isEmpty();
        aplicarResaltado();
    }

    /** Lleva el cursor a la posición del error y lo selecciona. */
    void irA(int linea, int columna, int longitud) {
        int parrafo = Math.max(0, Math.min(linea - 1, area.getParagraphs().size() - 1));
        int largo = area.getParagraphLength(parrafo);
        int inicio = Math.max(0, Math.min(columna, largo));
        int fin = longitud > 0 ? Math.min(inicio + longitud, largo) : largo;

        area.moveTo(parrafo, inicio);
        area.selectRange(parrafo, inicio, parrafo, Math.max(inicio, fin));
        area.requestFollowCaret();
        area.requestFocus();
    }

    // ---------------- internos ----------------

    private void aplicarResaltado() {
        String texto = area.getText();
        if (texto.isEmpty()) return;

        ModoResaltado modo = Lenguaje.de(ruta).map(ModoResaltado::de).orElse(ModoResaltado.NINGUNO);
        List<Segmento> sintaxis = ResaltadorSintaxis.resaltar(texto, modo);
        List<Segmento> marcas = erroresVigentes ? MarcasDeError.rangos(texto, errores) : List.of();
        area.setStyleSpans(0, EstilosRichText.construir(texto.length(), sintaxis, marcas));
    }

    private void actualizarTitulo() {
        setText(ruta.getFileName().toString() + (modificado ? "  ●" : ""));
        setTooltip(new Tooltip(ruta.toString()));
    }

    private void saltoConSangria() {
        String linea = area.getParagraph(area.getCurrentParagraph()).getText();
        String antesDelCursor = linea.substring(0, Math.min(area.getCaretColumn(), linea.length()));

        StringBuilder sangria = new StringBuilder();
        for (char c : antesDelCursor.toCharArray()) {
            if (c == ' ' || c == '\t') sangria.append(c);
            else break;
        }
        String sinEspaciosFinales = antesDelCursor.stripTrailing();
        if (sinEspaciosFinales.endsWith(":") || sinEspaciosFinales.endsWith("{")) sangria.append("    ");

        area.replaceSelection("\n" + sangria);
    }
}
