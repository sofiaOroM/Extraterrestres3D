package sofiaO.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import sofiaO.resaltado.ModoResaltado;
import sofiaO.resaltado.ResaltadorSintaxis;
import sofiaO.resaltado.Segmento;
import sofiaO.servicio.ErrorCompilador;
import sofiaO.servicio.FilaCuarteta;
import sofiaO.servicio.ResultadoCompilacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Panel inferior: errores, cuartetas y código C generado. */
final class PanelResultados extends TabPane {

    /** Lo que el panel le pide a la ventana principal para guardar un resultado en disco. */
    interface Guardador {
        void guardar(String nombreSugerido, String descripcion, String extension, String contenido);
    }

    /** Lo que el panel le pide a la ventana principal para compilar y ejecutar el código C generado. */
    interface Ejecutor {
        void ejecutar(String codigoC, String nombreBase);
    }

    private final Guardador guardador;
    private final Ejecutor ejecutor;
    private Consumer<ErrorCompilador> alElegirError = e -> {};

    private final Tab pestanaErrores = new Tab("Errores");
    private final Tab pestanaCuartetas = new Tab("Cuartetas");
    private final Tab pestanaC = new Tab("Código C");

    private final Label resumenErrores = new Label();
    private final TableView<ErrorCompilador> tablaErrores = new TableView<>();

    private final TableView<FilaCuarteta> tablaCuartetas = new TableView<>();
    private final CodeArea textoCuartetas = new CodeArea();
    private final VirtualizedScrollPane<CodeArea> desplazamientoTexto = new VirtualizedScrollPane<>(textoCuartetas);
    private final ToggleButton verTabla = new ToggleButton("Tabla");
    private final ToggleButton verTexto = new ToggleButton("Texto");
    private final SimpleBooleanProperty hayCuartetas = new SimpleBooleanProperty(false);

    private final CodeArea areaC = new CodeArea();
    private final SimpleBooleanProperty hayCodigo = new SimpleBooleanProperty(false);

    private String cuartetasTexto = "";
    private String codigoC = "";
    private String nombreBase = "programa";

    PanelResultados(IntegerProperty tamanoFuente, Guardador guardador, Ejecutor ejecutor) {
        this.guardador = guardador;
        this.ejecutor = ejecutor;
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getStyleClass().add("panel-resultados");

        configurarTablaErrores();
        configurarCuartetas(tamanoFuente);
        configurarCodigoC(tamanoFuente);

        pestanaErrores.setContent(crearContenidoErrores());
        pestanaCuartetas.setContent(crearContenidoCuartetas());
        pestanaC.setContent(crearContenidoC());
        getTabs().addAll(pestanaErrores, pestanaCuartetas, pestanaC);

        limpiar();
    }

    void setAlElegirError(Consumer<ErrorCompilador> accion) { this.alElegirError = accion; }

    void seleccionarErrores() { getSelectionModel().select(pestanaErrores); }

    void seleccionarCuartetas() { getSelectionModel().select(pestanaCuartetas); }

    void seleccionarCodigoC() { getSelectionModel().select(pestanaC); }

    boolean estaEnErrores() { return getSelectionModel().getSelectedItem() == pestanaErrores; }

    // =====================================================================
    // Mostrar resultados
    // =====================================================================

    /** Deja los tres paneles como al inicio (por ejemplo, antes de la primera compilación). */
    void limpiar() {
        tablaErrores.getItems().clear();
        tablaCuartetas.getItems().clear();
        cuartetasTexto = "";
        codigoC = "";
        hayCuartetas.set(false);
        hayCodigo.set(false);
        poner(textoCuartetas, "", ModoResaltado.C3D);
        poner(areaC, "", ModoResaltado.C);
        pestanaErrores.setText("Errores");
        pestanaCuartetas.setText("Cuartetas");
        pestanaC.setText("Código C");
        resumenErrores.setText("Compila un archivo para ver aquí el resultado.");
        resumenErrores.getStyleClass().removeAll("resumen-ok", "resumen-error");
    }

    void mostrar(ResultadoCompilacion r, String nombreDelPrograma) {
        this.nombreBase = nombreDelPrograma;

        // errores
        tablaErrores.getItems().setAll(r.errores);
        resumenErrores.getStyleClass().removeAll("resumen-ok", "resumen-error");
        if (r.errores.isEmpty()) {
            pestanaErrores.setText("Errores");
            resumenErrores.setText("Sin errores.");
            resumenErrores.getStyleClass().add("resumen-ok");
        } else {
            pestanaErrores.setText("Errores (" + r.errores.size() + ")");
            resumenErrores.setText(describirErrores(r));
            resumenErrores.getStyleClass().add("resumen-error");
        }

        // cuartetas
        tablaCuartetas.getItems().setAll(r.cuartetas);
        cuartetasTexto = r.cuartetasTexto;
        hayCuartetas.set(!r.cuartetas.isEmpty());
        poner(textoCuartetas, r.cuartetasTexto, ModoResaltado.C3D);
        pestanaCuartetas.setText(r.cuartetas.isEmpty() ? "Cuartetas" : "Cuartetas (" + r.cuartetas.size() + ")");

        // código C
        codigoC = r.codigoC;
        hayCodigo.set(r.tieneCodigo());
        poner(areaC, r.codigoC, ModoResaltado.C);
        pestanaC.setText("Código C");
    }

    private static String describirErrores(ResultadoCompilacion r) {
        long lexicos = r.contar(ErrorCompilador.Tipo.LEXICO);
        long sintacticos = r.contar(ErrorCompilador.Tipo.SINTACTICO);
        long semanticos = r.contar(ErrorCompilador.Tipo.SEMANTICO);
        long internos = r.contar(ErrorCompilador.Tipo.INTERNO);
        StringBuilder texto = new StringBuilder(r.errores.size() == 1 ? "1 error" : r.errores.size() + " errores");
        List<String> partes = new ArrayList<>();
        if (lexicos > 0) partes.add(lexicos + (lexicos == 1 ? " léxico" : " léxicos"));
        if (sintacticos > 0) partes.add(sintacticos + (sintacticos == 1 ? " sintáctico" : " sintácticos"));
        if (semanticos > 0) partes.add(semanticos + (semanticos == 1 ? " semántico" : " semánticos"));
        if (internos > 0) partes.add(internos + (internos == 1 ? " interno" : " internos"));
        if (!partes.isEmpty()) texto.append(": ").append(String.join(", ", partes));
        return texto.append(". Doble clic en una fila para ir al lugar del error.").toString();
    }

    private static void poner(CodeArea area, String texto, ModoResaltado modo) {
        area.replaceText(texto);
        if (!texto.isEmpty()) {
            List<Segmento> segmentos = ResaltadorSintaxis.resaltar(texto, modo);
            area.setStyleSpans(0, EstilosRichText.construir(texto.length(), segmentos, List.of()));
        }
        area.moveTo(0);
        area.requestFollowCaret();
    }

    // =====================================================================
    // Errores
    // =====================================================================

    private void configurarTablaErrores() {
        TableColumn<ErrorCompilador, String> tipo = new TableColumn<>("Tipo");
        tipo.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().tipo.etiqueta));
        tipo.setPrefWidth(110);
        tipo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacia) {
                super.updateItem(texto, vacia);
                getStyleClass().removeAll("tipo-lexico", "tipo-sintactico", "tipo-semantico", "tipo-interno");
                setText(vacia ? null : texto);
                if (!vacia && getTableRow() != null && getTableRow().getItem() != null) {
                    getStyleClass().add("tipo-" + getTableRow().getItem().tipo.name().toLowerCase(Locale.ROOT));
                }
            }
        });

        TableColumn<ErrorCompilador, String> descripcion = new TableColumn<>("Descripción");
        descripcion.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().descripcion));
        descripcion.setPrefWidth(640);
        descripcion.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacia) {
                super.updateItem(texto, vacia);
                setText(vacia ? null : texto);
                setTooltip(vacia || texto == null ? null : new Tooltip(texto));
            }
        });

        TableColumn<ErrorCompilador, String> archivo = new TableColumn<>("Archivo");
        archivo.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().nombreArchivo()));
        archivo.setPrefWidth(170);

        TableColumn<ErrorCompilador, Integer> linea = new TableColumn<>("Línea");
        linea.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().linea));
        linea.setPrefWidth(70);

        TableColumn<ErrorCompilador, Integer> columna = new TableColumn<>("Columna");
        columna.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().columna + 1));
        columna.setPrefWidth(85);

        tablaErrores.getColumns().add(tipo);
        tablaErrores.getColumns().add(descripcion);
        tablaErrores.getColumns().add(archivo);
        tablaErrores.getColumns().add(linea);
        tablaErrores.getColumns().add(columna);
        tablaErrores.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablaErrores.setPlaceholder(new Label("Aquí aparecerán los errores léxicos, sintácticos y semánticos."));

        tablaErrores.setRowFactory(tabla -> {
            TableRow<ErrorCompilador> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) alElegirError.accept(fila.getItem());
            });
            return fila;
        });
        tablaErrores.setOnKeyPressed(e -> {
            ErrorCompilador elegido = tablaErrores.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && elegido != null) alElegirError.accept(elegido);
        });
    }

    private Region crearContenidoErrores() {
        resumenErrores.getStyleClass().add("resumen");
        BorderPane contenido = new BorderPane(tablaErrores);
        contenido.setTop(resumenErrores);
        BorderPane.setMargin(resumenErrores, new Insets(8, 12, 8, 12));
        return contenido;
    }

    // =====================================================================
    // Cuartetas
    // =====================================================================

    private void configurarCuartetas(IntegerProperty tamanoFuente) {
        tablaCuartetas.getStyleClass().add("tabla-mono");
        tablaCuartetas.getColumns().add(columnaCuarteta("#", 60, f -> String.valueOf(f.indice() + 1)));
        tablaCuartetas.getColumns().add(columnaCuarteta("Operador", 110, FilaCuarteta::operador));
        tablaCuartetas.getColumns().add(columnaCuarteta("Arg 1", 200, FilaCuarteta::arg1));
        tablaCuartetas.getColumns().add(columnaCuarteta("Arg 2", 200, FilaCuarteta::arg2));
        tablaCuartetas.getColumns().add(columnaCuarteta("Resultado", 200, FilaCuarteta::resultado));
        tablaCuartetas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablaCuartetas.setPlaceholder(new Label("Las cuartetas aparecen cuando el programa compila sin errores."));

        textoCuartetas.getStyleClass().add("editor");
        textoCuartetas.setEditable(false);
        textoCuartetas.styleProperty().bind(Bindings.concat("-fx-font-size: ", tamanoFuente.asString(), "px;"));
    }

    private static TableColumn<FilaCuarteta, String> columnaCuarteta(String titulo, double ancho,
                                                                     Function<FilaCuarteta, String> valor) {
        TableColumn<FilaCuarteta, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        columna.setPrefWidth(ancho);
        return columna;
    }

    private Region crearContenidoCuartetas() {
        ToggleGroup grupo = new ToggleGroup();
        verTabla.setToggleGroup(grupo);
        verTexto.setToggleGroup(grupo);
        verTabla.setSelected(true);
        // Siempre debe haber una vista elegida.
        grupo.selectedToggleProperty().addListener((obs, anterior, actual) -> {
            if (actual == null && anterior != null) anterior.setSelected(true);
        });

        Button copiar = new Button("Copiar");
        copiar.setOnAction(e -> copiar(cuartetasTexto));
        copiar.disableProperty().bind(hayCuartetas.not());
        Button guardar = new Button("Guardar…");
        guardar.setOnAction(e -> guardador.guardar(nombreBase + "_cuartetas", "Cuartetas", "txt", cuartetasTexto));
        guardar.disableProperty().bind(hayCuartetas.not());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(6, verTabla, verTexto, espacio, copiar, guardar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("barra-panel");

        desplazamientoTexto.visibleProperty().bind(verTexto.selectedProperty());
        tablaCuartetas.visibleProperty().bind(verTexto.selectedProperty().not());
        StackPane vistas = new StackPane(tablaCuartetas, desplazamientoTexto);

        BorderPane contenido = new BorderPane(vistas);
        contenido.setTop(barra);
        return contenido;
    }

    // =====================================================================
    // Código C
    // =====================================================================

    private void configurarCodigoC(IntegerProperty tamanoFuente) {
        areaC.getStyleClass().add("editor");
        areaC.setEditable(false);
        areaC.styleProperty().bind(Bindings.concat("-fx-font-size: ", tamanoFuente.asString(), "px;"));
    }

    private Region crearContenidoC() {
        Button ejecutar = new Button("▶ Ejecutar");
        ejecutar.getStyleClass().add("boton-primario");
        ejecutar.setOnAction(e -> ejecutor.ejecutar(codigoC, nombreBase));
        ejecutar.disableProperty().bind(hayCodigo.not());

        Button copiar = new Button("Copiar");
        copiar.setOnAction(e -> copiar(codigoC));
        copiar.disableProperty().bind(hayCodigo.not());
        Button guardar = new Button("Guardar como .c…");
        guardar.setOnAction(e -> guardador.guardar(nombreBase, "Código C", "c", codigoC));
        guardar.disableProperty().bind(hayCodigo.not());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(6, ejecutar, espacio, copiar, guardar);
        barra.setAlignment(Pos.CENTER_RIGHT);
        barra.getStyleClass().add("barra-panel");

        Label vacio = new Label("El código C aparece cuando el programa compila sin errores.");
        vacio.getStyleClass().add("vacio");
        vacio.visibleProperty().bind(hayCodigo.not());

        StackPane vistas = new StackPane(new VirtualizedScrollPane<>(areaC), vacio);
        BorderPane contenido = new BorderPane(vistas);
        contenido.setTop(barra);
        return contenido;
    }

    private static void copiar(String texto) {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(texto);
        Clipboard.getSystemClipboard().setContent(contenido);
    }
}
