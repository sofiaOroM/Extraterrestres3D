package sofiaO.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import sofiaO.resaltado.ModoResaltado;
import sofiaO.resaltado.ResaltadorSintaxis;
import sofiaO.resaltado.Segmento;
import sofiaO.servicio.Lenguaje;
import sofiaO.servicio.OperacionesArchivo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Muestra el contenido del archivo seleccionado en el árbol, con su coloreado, sin abrirlo para editar. */
final class VistaPrevia extends VBox {

    private static final int MAXIMO_CARACTERES = 200_000;

    private final Label nombre = new Label();
    private final CodeArea area = new CodeArea();

    VistaPrevia(IntegerProperty tamanoFuente) {
        getStyleClass().add("vista-previa");

        Label titulo = new Label("Vista previa");
        titulo.getStyleClass().add("titulo-panel");
        nombre.getStyleClass().add("nombre-previa");
        HBox encabezado = new HBox(8, titulo, nombre);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        area.getStyleClass().add("editor");
        area.setEditable(false);
        area.styleProperty().bind(Bindings.concat("-fx-font-size: ", tamanoFuente.subtract(1).asString(), "px;"));

        VirtualizedScrollPane<CodeArea> desplazamiento = new VirtualizedScrollPane<>(area);
        VBox.setVgrow(desplazamiento, Priority.ALWAYS);

        getChildren().addAll(encabezado, desplazamiento);
        vaciar("Selecciona un archivo del árbol para ver su contenido.");
    }

    void mostrar(Path ruta) {
        nombre.setText(ruta.getFileName().toString());
        if (Files.isDirectory(ruta)) {
            try (Stream<Path> hijos = Files.list(ruta)) {
                long cantidad = hijos.count();
                poner("Carpeta con " + cantidad + (cantidad == 1 ? " elemento." : " elementos."), ModoResaltado.NINGUNO);
            } catch (IOException e) {
                poner("No se pudo leer la carpeta.", ModoResaltado.NINGUNO);
            }
            return;
        }
        try {
            String texto = OperacionesArchivo.leerTexto(ruta);
            if (texto.length() > MAXIMO_CARACTERES) {
                texto = texto.substring(0, MAXIMO_CARACTERES) + "\n… (la vista previa muestra solo el inicio del archivo)";
            }
            ModoResaltado modo = Lenguaje.de(ruta).map(ModoResaltado::de).orElse(ModoResaltado.NINGUNO);
            poner(texto, modo);
        } catch (IOException | RuntimeException e) {
            poner("Este archivo no se puede mostrar como texto.", ModoResaltado.NINGUNO);
        }
    }

    void vaciar(String mensaje) {
        nombre.setText("");
        poner(mensaje, ModoResaltado.NINGUNO);
    }

    private void poner(String texto, ModoResaltado modo) {
        area.replaceText(texto);
        if (!texto.isEmpty()) {
            List<Segmento> segmentos = ResaltadorSintaxis.resaltar(texto, modo);
            area.setStyleSpans(0, EstilosRichText.construir(texto.length(), segmentos, List.of()));
        }
        area.moveTo(0);
        area.requestFollowCaret();
    }
}
