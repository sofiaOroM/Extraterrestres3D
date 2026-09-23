package sofiaO.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import sofiaO.servicio.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/**
 * Ventana principal: árbol de trabajo, editores con pestañas, panel de resultados
 * (errores, cuartetas, código C) y las acciones que los conectan.
 */
final class VentanaPrincipal implements PanelArbol.Acciones {

    private static final ExecutorService EJECUTOR = Executors.newSingleThreadExecutor(tarea -> {
        Thread hilo = new Thread(tarea, "compilador");
        hilo.setDaemon(true);
        return hilo;
    });

    static void detenerTrabajosEnSegundoPlano() {
        EJECUTOR.shutdownNow();
    }

    private static final int FUENTE_INICIAL = 14;
    private static final Pattern IDENTIFICADOR = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Stage escenario;
    private final BorderPane raiz = new BorderPane();
    private final IntegerProperty tamanoFuente = new SimpleIntegerProperty(FUENTE_INICIAL);
    private final Preferences preferencias = Preferences.userNodeForPackage(VentanaPrincipal.class);

    private final TabPane pestanas = new TabPane();
    private final PanelArbol arbol = new PanelArbol(this);
    private final VistaPrevia vistaPrevia = new VistaPrevia(tamanoFuente);
    private final PanelResultados resultados = new PanelResultados(tamanoFuente, this::guardarResultado);

    private final ServicioCompilacion servicio = new ServicioCompilacion();
    private final BooleanProperty compilando = new SimpleBooleanProperty(false);
    private ResultadoCompilacion ultimoResultado;

    private final Label estado = new Label("Listo");
    private final Label lenguaje = new Label();
    private final Label posicion = new Label();

    VentanaPrincipal(Stage escenario) {
        this.escenario = escenario;

        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        pestanas.getSelectionModel().selectedItemProperty().addListener((obs, anterior, actual) -> {
            actualizarPosicion();
            actualizarTituloVentana();
        });

        arbol.setAlSeleccionar(vistaPrevia::mostrar);
        resultados.setAlElegirError(this::irAlError);

        raiz.setTop(new VBox(crearMenu(), crearBarraHerramientas()));
        raiz.setCenter(crearZonaCentral());
        raiz.setBottom(crearBarraEstado());

        escenario.setOnCloseRequest(e -> {
            if (!confirmarCierreDeTodo()) e.consume();
        });
    }

    Parent getRaiz() {
        return raiz;
    }

    /** Se llama cuando la ventana ya está visible: reabre la última carpeta de trabajo. */
    void alMostrarse() {
        String guardada = preferencias.get("carpeta", null);
        Path candidata = guardada == null ? null : Path.of(guardada);
        if (candidata == null || !Files.isDirectory(candidata)) {
            Path ejemplos = Path.of("ejemplos").toAbsolutePath();
            candidata = Files.isDirectory(ejemplos) ? ejemplos : null;
        }
        if (candidata != null) cambiarRaiz(candidata);
    }

    // =====================================================================
    // Construcción de la interfaz
    // =====================================================================

    private Node crearZonaCentral() {
        SplitPane izquierda = new SplitPane(arbol, vistaPrevia);
        izquierda.setOrientation(javafx.geometry.Orientation.VERTICAL);
        izquierda.setDividerPositions(0.62);

        Label invitacion = new Label("Abre un archivo con doble clic en el árbol,\no crea uno nuevo con clic derecho.");
        invitacion.getStyleClass().add("vacio");
        invitacion.setAlignment(Pos.CENTER);
        invitacion.visibleProperty().bind(Bindings.isEmpty(pestanas.getTabs()));
        StackPane zonaEditores = new StackPane(invitacion, pestanas);
        pestanas.visibleProperty().bind(Bindings.isNotEmpty(pestanas.getTabs()));

        SplitPane superior = new SplitPane(izquierda, zonaEditores);
        superior.setDividerPositions(0.22);
        SplitPane.setResizableWithParent(izquierda, false);

        SplitPane principal = new SplitPane(superior, resultados);
        principal.setOrientation(javafx.geometry.Orientation.VERTICAL);
        principal.setDividerPositions(0.66);
        return principal;
    }

    private MenuBar crearMenu() {
        Menu archivo = new Menu("Archivo");
        archivo.getItems().addAll(
                item("Abrir archivo…", combinacion(KeyCode.O, false), this::abrirArchivoConDialogo),
                item("Abrir carpeta…", combinacion(KeyCode.O, true), this::abrirCarpeta),
                item("Nuevo archivo…", combinacion(KeyCode.N, false), () -> nuevoArchivo(arbol.carpetaActiva())),
                new SeparatorMenuItem(),
                item("Guardar", combinacion(KeyCode.S, false), this::guardarActual),
                item("Guardar como…", null, this::guardarActualComo),
                item("Guardar todo", combinacion(KeyCode.S, true), this::guardarTodo),
                new SeparatorMenuItem(),
                item("Descargar selección…", null, this::descargarSeleccion),
                new SeparatorMenuItem(),
                item("Cerrar pestaña", combinacion(KeyCode.W, false), this::cerrarPestanaActual),
                item("Salir", null, () -> {
                    if (confirmarCierreDeTodo()) escenario.close();
                }));

        MenuItem compilar = item("Compilar archivo actual", new KeyCodeCombination(KeyCode.F5), this::compilar);
        compilar.disableProperty().bind(compilando);
        Menu menuCompilar = new Menu("Compilar");
        menuCompilar.getItems().add(compilar);

        Menu ver = new Menu("Ver");
        ver.getItems().addAll(
                item("Errores", null, resultados::seleccionarErrores),
                item("Cuartetas", null, resultados::seleccionarCuartetas),
                item("Código C", null, resultados::seleccionarCodigoC),
                new SeparatorMenuItem(),
                item("Aumentar letra", combinacion(KeyCode.EQUALS, false), () -> cambiarFuente(1)),
                item("Reducir letra", combinacion(KeyCode.MINUS, false), () -> cambiarFuente(-1)),
                item("Tamaño de letra original", combinacion(KeyCode.DIGIT0, false), () -> tamanoFuente.set(FUENTE_INICIAL)));

        return new MenuBar(archivo, menuCompilar, ver);
    }

    private static KeyCombination combinacion(KeyCode tecla, boolean conMayuscula) {
        return conMayuscula
                ? new KeyCodeCombination(tecla, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
                : new KeyCodeCombination(tecla, KeyCombination.SHORTCUT_DOWN);
    }

    private static MenuItem item(String texto, KeyCombination atajo, Runnable accion) {
        MenuItem elemento = new MenuItem(texto);
        if (atajo != null) elemento.setAccelerator(atajo);
        elemento.setOnAction(e -> accion.run());
        return elemento;
    }

    private Node crearBarraHerramientas() {
        Button abrirCarpeta = boton("Abrir carpeta", "Abrir una carpeta de trabajo", this::abrirCarpeta);
        Button abrirArchivo = boton("Abrir archivo", "Abrir uno o varios archivos", this::abrirArchivoConDialogo);
        Button guardar = boton("Guardar", "Guardar el archivo actual (Ctrl+S)", this::guardarActual);
        Button compilar = boton("Compilar", "Compilar el archivo actual (F5)", this::compilar);
        compilar.getStyleClass().add("boton-primario");
        compilar.disableProperty().bind(compilando);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(8, abrirCarpeta, abrirArchivo, guardar, espacio, compilar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("barra-herramientas");
        return barra;
    }

    private static Button boton(String texto, String ayuda, Runnable accion) {
        Button boton = new Button(texto);
        boton.setTooltip(new Tooltip(ayuda));
        boton.setOnAction(e -> accion.run());
        return boton;
    }

    private Node crearBarraEstado() {
        estado.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(estado, Priority.ALWAYS);
        HBox barra = new HBox(18, estado, lenguaje, posicion);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("barra-estado");
        return barra;
    }

    // =====================================================================
    // Estado de la ventana
    // =====================================================================

    private void estado(String mensaje) {
        estado.setText(mensaje);
    }

    private void actualizarPosicion() {
        EditorArchivo editor = editorActivo();
        posicion.setText(editor == null ? "" : editor.posicionDelCursor());
        lenguaje.setText(editor == null ? "" : editor.nombreDelLenguaje());
    }

    private void actualizarTituloVentana() {
        StringBuilder titulo = new StringBuilder();
        EditorArchivo editor = editorActivo();
        if (editor != null) {
            titulo.append(editor.getRuta().getFileName()).append(editor.estaModificado() ? " ●" : "").append(" — ");
        }
        Path carpeta = arbol.getRaiz();
        if (carpeta != null && carpeta.getFileName() != null) titulo.append(carpeta.getFileName()).append(" — ");
        titulo.append("Extraterrestre3D");
        escenario.setTitle(titulo.toString());
    }

    private void cambiarFuente(int delta) {
        tamanoFuente.set(Math.max(9, Math.min(32, tamanoFuente.get() + delta)));
    }

    // =====================================================================
    // Editores abiertos
    // =====================================================================

    private static Path clave(Path ruta) {
        return ruta.toAbsolutePath().normalize();
    }

    private List<EditorArchivo> editores() {
        List<EditorArchivo> lista = new ArrayList<>();
        for (Tab pestana : pestanas.getTabs()) {
            if (pestana instanceof EditorArchivo editor) lista.add(editor);
        }
        return lista;
    }

    private EditorArchivo editorActivo() {
        Tab seleccionada = pestanas.getSelectionModel().getSelectedItem();
        return seleccionada instanceof EditorArchivo editor ? editor : null;
    }

    private EditorArchivo editorDe(Path ruta) {
        Path buscada = clave(ruta);
        for (EditorArchivo editor : editores()) {
            if (clave(editor.getRuta()).equals(buscada)) return editor;
        }
        return null;
    }

    /** Abre el archivo en una pestaña (o la enfoca si ya estaba abierto). Devuelve null si no se pudo abrir. */
    private EditorArchivo abrirArchivo(Path ruta) {
        Path normal = clave(ruta);
        EditorArchivo existente = editorDe(normal);
        if (existente != null) {
            pestanas.getSelectionModel().select(existente);
            existente.enfocar();
            return existente;
        }
        if (!Files.isRegularFile(normal)) {
            Dialogos.error(escenario, "No se pudo abrir", "«" + normal.getFileName() + "» ya no existe en el disco.");
            return null;
        }

        String texto;
        try {
            texto = OperacionesArchivo.leerTexto(normal);
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo abrir «" + normal.getFileName() + "»",
                    "Solo se pueden abrir archivos de texto en UTF-8.");
            return null;
        }

        EditorArchivo editor = new EditorArchivo(normal, texto, tamanoFuente);
        editor.setOnCloseRequest(evento -> {
            if (!confirmarCierre(editor)) evento.consume();
        });
        editor.setAlMoverCursor(() -> {
            if (editor == editorActivo()) actualizarPosicion();
        });
        editor.setAlCambiar(this::actualizarTituloVentana);
        List<ErrorCompilador> pendientes = erroresDe(normal);
        if (!pendientes.isEmpty()) editor.mostrarErrores(pendientes);

        pestanas.getTabs().add(editor);
        pestanas.getSelectionModel().select(editor);
        editor.enfocar();
        return editor;
    }

    private List<ErrorCompilador> erroresDe(Path ruta) {
        if (ultimoResultado == null) return List.of();
        Path buscada = clave(ruta);
        List<ErrorCompilador> delArchivo = new ArrayList<>();
        for (ErrorCompilador e : ultimoResultado.errores) {
            if (e.archivo != null && clave(e.archivo).equals(buscada)) delArchivo.add(e);
        }
        return delArchivo;
    }

    private boolean confirmarCierre(EditorArchivo editor) {
        if (!editor.estaModificado()) return true;
        return switch (Dialogos.preguntarGuardar(escenario, editor.getRuta().getFileName().toString())) {
            case GUARDAR -> guardar(editor);
            case DESCARTAR -> true;
            case CANCELAR -> false;
        };
    }

    private boolean confirmarCierreDeTodo() {
        for (EditorArchivo editor : editores()) {
            pestanas.getSelectionModel().select(editor);
            if (!confirmarCierre(editor)) return false;
        }
        return true;
    }

    private void cerrarPestanaActual() {
        EditorArchivo editor = editorActivo();
        if (editor != null && confirmarCierre(editor)) pestanas.getTabs().remove(editor);
    }

    // =====================================================================
    // Abrir y guardar
    // =====================================================================

    private File carpetaInicial() {
        Path base = arbol.getRaiz();
        return base != null && Files.isDirectory(base) ? base.toFile() : null;
    }

    private void abrirArchivoConDialogo() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Abrir archivos");
        selector.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivos del proyecto (*.y, *.z, *.pig)", "*.y", "*.z", "*.pig"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File inicial = carpetaInicial();
        if (inicial != null) selector.setInitialDirectory(inicial);

        List<File> elegidos = selector.showOpenMultipleDialog(escenario);
        if (elegidos != null) elegidos.forEach(f -> abrirArchivo(f.toPath()));
    }

    @Override
    public void abrirCarpeta() {
        DirectoryChooser selector = new DirectoryChooser();
        selector.setTitle("Abrir carpeta de trabajo");
        File inicial = carpetaInicial();
        if (inicial != null) selector.setInitialDirectory(inicial);

        File elegida = selector.showDialog(escenario);
        if (elegida != null) cambiarRaiz(elegida.toPath());
    }

    private void cambiarRaiz(Path carpeta) {
        Path absoluta = carpeta.toAbsolutePath().normalize();
        arbol.establecerRaiz(absoluta);
        preferencias.put("carpeta", absoluta.toString());
        vistaPrevia.vaciar("Selecciona un archivo del árbol para ver su contenido.");
        actualizarTituloVentana();
        estado("Carpeta de trabajo: " + absoluta);
    }

    private boolean guardar(EditorArchivo editor) {
        try {
            OperacionesArchivo.escribirTexto(editor.getRuta(), editor.getTexto());
            editor.marcarGuardado();
            actualizarTituloVentana();
            estado("Guardado «" + editor.getRuta().getFileName() + "»");
            arbol.seleccion().filter(p -> clave(p).equals(clave(editor.getRuta()))).ifPresent(vistaPrevia::mostrar);
            return true;
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo guardar",
                    "No se pudo escribir «" + editor.getRuta().getFileName() + "»: " + e.getMessage());
            return false;
        }
    }

    private void guardarActual() {
        EditorArchivo editor = editorActivo();
        if (editor != null) guardar(editor);
    }

    private void guardarTodo() {
        for (EditorArchivo editor : editores()) {
            if (editor.estaModificado()) guardar(editor);
        }
    }

    private void guardarActualComo() {
        EditorArchivo editor = editorActivo();
        if (editor == null) return;

        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar como");
        selector.setInitialFileName(editor.getRuta().getFileName().toString());
        File inicial = editor.getRuta().getParent() == null ? null : editor.getRuta().getParent().toFile();
        if (inicial != null && inicial.isDirectory()) selector.setInitialDirectory(inicial);

        File destino = selector.showSaveDialog(escenario);
        if (destino == null) return;

        Path nueva = clave(destino.toPath());
        EditorArchivo otro = editorDe(nueva);
        if (otro != null && otro != editor) {
            Dialogos.error(escenario, "No se pudo guardar",
                    "«" + nueva.getFileName() + "» está abierto en otra pestaña. Ciérralo o elige otro nombre.");
            return;
        }
        try {
            OperacionesArchivo.escribirTexto(nueva, editor.getTexto());
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo guardar", e.getMessage());
            return;
        }
        editor.setRuta(nueva);
        editor.marcarGuardado();
        arbol.actualizar();
        actualizarTituloVentana();
        actualizarPosicion();
        estado("Guardado «" + nueva.getFileName() + "»");
    }

    /** Guarda las cuartetas o el código C generados, elegidos desde el panel de resultados. */
    private void guardarResultado(String nombreSugerido, String descripcion, String extension, String contenido) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar " + descripcion.toLowerCase(Locale.ROOT));
        selector.setInitialFileName(nombreSugerido + "." + extension);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(descripcion + " (*." + extension + ")", "*." + extension));
        File inicial = carpetaInicial();
        if (inicial != null) selector.setInitialDirectory(inicial);

        File destino = selector.showSaveDialog(escenario);
        if (destino == null) return;
        try {
            OperacionesArchivo.escribirTexto(destino.toPath(), contenido);
            arbol.actualizar();
            estado(descripcion + " guardado en " + destino);
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo guardar", e.getMessage());
        }
    }

    // =====================================================================
    // Acciones del árbol de trabajo
    // =====================================================================

    @Override
    public void abrir(Path archivo) {
        abrirArchivo(archivo);
    }

    @Override
    public void nuevoArchivo(Path carpeta) {
        if (carpeta == null) {
            Dialogos.info(escenario, "Abre una carpeta primero", "Los archivos nuevos se crean dentro de la carpeta de trabajo.");
            return;
        }
        Optional<String> nombre = Dialogos.pedirTexto(escenario, "Nuevo archivo",
                "Nombre con extensión (.y, .z o .pig):", "");
        if (nombre.isEmpty()) return;

        String texto = nombre.get();
        if (!nombreValido(texto)) {
            Dialogos.error(escenario, "Nombre no válido", "El nombre no puede incluir «/» ni «\\».");
            return;
        }
        if (Lenguaje.de(Path.of(texto)).isEmpty()) {
            Dialogos.error(escenario, "Falta la extensión",
                    "Termina el nombre en .y (estructuras y funciones), .z (clase) o .pig (programa principal).");
            return;
        }
        if (texto.toLowerCase(Locale.ROOT).endsWith(".z") && !IDENTIFICADOR.matcher(Lenguaje.nombreBase(Path.of(texto))).matches()) {
            Dialogos.error(escenario, "Nombre de clase no válido",
                    "El archivo .z debe llamarse como su clase, así que el nombre solo puede tener letras, números y «_», sin empezar con número.");
            return;
        }

        try {
            Path creado = OperacionesArchivo.crearArchivo(carpeta, texto);
            arbol.actualizar();
            arbol.seleccionar(creado);
            abrirArchivo(creado);
        } catch (FileAlreadyExistsException e) {
            Dialogos.error(escenario, "Ya existe", "Ya hay un archivo llamado «" + texto + "» en esa carpeta.");
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo crear el archivo", e.getMessage());
        }
    }

    @Override
    public void nuevaCarpeta(Path carpeta) {
        if (carpeta == null) return;
        Optional<String> nombre = Dialogos.pedirTexto(escenario, "Nueva carpeta", "Nombre de la carpeta:", "");
        if (nombre.isEmpty()) return;
        if (!nombreValido(nombre.get())) {
            Dialogos.error(escenario, "Nombre no válido", "El nombre no puede incluir «/» ni «\\».");
            return;
        }
        try {
            Path creada = OperacionesArchivo.crearCarpeta(carpeta, nombre.get());
            arbol.actualizar();
            arbol.seleccionar(creada);
        } catch (FileAlreadyExistsException e) {
            Dialogos.error(escenario, "Ya existe", "Ya hay un elemento llamado «" + nombre.get() + "» en esa carpeta.");
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo crear la carpeta", e.getMessage());
        }
    }

    @Override
    public void renombrar(Path ruta) {
        Optional<String> nombre = Dialogos.pedirTexto(escenario, "Renombrar", "Nuevo nombre:", ruta.getFileName().toString());
        if (nombre.isEmpty() || nombre.get().equals(ruta.getFileName().toString())) return;
        if (!nombreValido(nombre.get())) {
            Dialogos.error(escenario, "Nombre no válido", "El nombre no puede incluir «/» ni «\\».");
            return;
        }
        try {
            Path anterior = clave(ruta);
            Path nueva = clave(OperacionesArchivo.renombrar(ruta, nombre.get()));
            for (EditorArchivo editor : editores()) {
                Path actual = clave(editor.getRuta());
                if (actual.startsWith(anterior)) editor.setRuta(nueva.resolve(anterior.relativize(actual).toString()));
            }
            arbol.actualizar();
            arbol.seleccionar(nueva);
            actualizarTituloVentana();
            actualizarPosicion();
        } catch (FileAlreadyExistsException e) {
            Dialogos.error(escenario, "Ya existe", "Ya hay un elemento llamado «" + nombre.get() + "» en esa carpeta.");
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo renombrar", e.getMessage());
        }
    }

    @Override
    public void eliminar(Path ruta) {
        boolean esCarpeta = Files.isDirectory(ruta);
        String mensaje = esCarpeta
                ? "Se eliminará la carpeta «" + ruta.getFileName() + "» con todo su contenido. Esta acción no se puede deshacer."
                : "Se eliminará «" + ruta.getFileName() + "». Esta acción no se puede deshacer.";
        if (!Dialogos.confirmar(escenario, "Eliminar", mensaje, "Eliminar")) return;

        Path objetivo = clave(ruta);
        try {
            OperacionesArchivo.eliminar(ruta);
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo eliminar", e.getMessage());
            return;
        }
        for (EditorArchivo editor : editores()) {
            if (clave(editor.getRuta()).startsWith(objetivo)) pestanas.getTabs().remove(editor);
        }
        arbol.actualizar();
        vistaPrevia.vaciar("Selecciona un archivo del árbol para ver su contenido.");
        actualizarTituloVentana();
    }

    @Override
    public void descargar(Path ruta) {
        Path origen = clave(ruta);
        // Si hay cambios sin guardar dentro de lo que se va a copiar, se guardan antes.
        for (EditorArchivo editor : editores()) {
            if (editor.estaModificado() && clave(editor.getRuta()).startsWith(origen)) guardar(editor);
        }

        DirectoryChooser selector = new DirectoryChooser();
        selector.setTitle("Elige la carpeta donde guardar la copia de «" + ruta.getFileName() + "»");
        File destino = selector.showDialog(escenario);
        if (destino == null) return;

        try {
            Path copia = OperacionesArchivo.copiarA(ruta, destino.toPath());
            estado("Copia creada en " + copia);
            Dialogos.info(escenario, "Descarga lista", "Se copió «" + ruta.getFileName() + "» en:\n" + copia);
        } catch (IOException e) {
            Dialogos.error(escenario, "No se pudo copiar", e.getMessage());
        }
    }

    private void descargarSeleccion() {
        Optional<Path> elegido = arbol.seleccion();
        if (elegido.isPresent()) {
            descargar(elegido.get());
            return;
        }
        EditorArchivo editor = editorActivo();
        if (editor != null) descargar(editor.getRuta());
        else Dialogos.info(escenario, "Nada que descargar", "Selecciona un archivo o carpeta en el árbol, o abre un archivo.");
    }

    private static boolean nombreValido(String nombre) {
        return !nombre.contains("/") && !nombre.contains("\\") && !nombre.equals(".") && !nombre.equals("..");
    }

    // =====================================================================
    // Compilación
    // =====================================================================

    private void compilar() {
        EditorArchivo activo = editorActivo();
        if (activo == null) {
            Dialogos.info(escenario, "No hay archivo abierto", "Abre un archivo .pig para compilarlo, o un .y / .z para validarlo.");
            return;
        }
        if (compilando.get()) return;

        Path archivo = activo.getRuta();
        Optional<Lenguaje> lenguajeDelArchivo = Lenguaje.de(archivo);
        if (lenguajeDelArchivo.isEmpty()) {
            Dialogos.info(escenario, "Extensión no reconocida",
                    "Solo se pueden compilar archivos .pig, y validar archivos .y y .z.");
            return;
        }
        boolean esPrograma = lenguajeDelArchivo.get() == Lenguaje.PIG;

        Path carpetaBase = arbol.getRaiz() != null && clave(archivo).startsWith(clave(arbol.getRaiz()))
                ? arbol.getRaiz()
                : clave(archivo).getParent();

        // Se compila lo que hay en los editores, guardado o no. La copia se toma aquí, en el hilo de la
        // interfaz, porque los editores no se pueden leer desde otro hilo.
        Map<Path, String> instantanea = new HashMap<>();
        for (EditorArchivo editor : editores()) instantanea.put(clave(editor.getRuta()), editor.getTexto());
        FuenteArchivos fuente = ruta -> {
            String texto = instantanea.get(clave(ruta));
            return texto != null ? texto : OperacionesArchivo.leerTexto(ruta);
        };

        compilando.set(true);
        estado(esPrograma ? "Compilando «" + archivo.getFileName() + "»…" : "Validando «" + archivo.getFileName() + "»…");

        Task<ResultadoCompilacion> tarea = new Task<>() {
            @Override
            protected ResultadoCompilacion call() {
                return esPrograma
                        ? servicio.compilarPrograma(archivo, carpetaBase, fuente)
                        : servicio.validarArchivo(archivo, carpetaBase, fuente);
            }
        };
        tarea.setOnSucceeded(e -> {
            compilando.set(false);
            mostrarResultado(tarea.getValue(), archivo, esPrograma);
        });
        tarea.setOnFailed(e -> {
            compilando.set(false);
            Throwable causa = tarea.getException();
            estado("La compilación se detuvo por un error inesperado.");
            Dialogos.error(escenario, "Error inesperado",
                    causa == null || causa.getMessage() == null ? "Ocurrió un error interno." : causa.getMessage());
        });
        EJECUTOR.execute(tarea);
    }

    private void mostrarResultado(ResultadoCompilacion r, Path archivo, boolean esPrograma) {
        ultimoResultado = r;
        String nombre = archivo.getFileName().toString();

        for (EditorArchivo editor : editores()) editor.mostrarErrores(erroresDe(editor.getRuta()));
        resultados.mostrar(r, Lenguaje.nombreBase(archivo));

        if (r.exitoso()) {
            if (resultados.estaEnErrores()) resultados.seleccionarCuartetas();
            estado(esPrograma
                    ? "Compilación correcta: " + r.archivos.size() + (r.archivos.size() == 1 ? " archivo, " : " archivos, ")
                    + r.cuartetas.size() + " cuartetas."
                    : "«" + nombre + "» es válido: sin errores léxicos, sintácticos ni semánticos.");
        } else {
            resultados.seleccionarErrores();
            estado((r.errores.size() == 1 ? "Se encontró 1 error" : "Se encontraron " + r.errores.size() + " errores")
                    + " al " + (esPrograma ? "compilar" : "validar") + " «" + nombre + "».");
        }
    }

    private void irAlError(ErrorCompilador error) {
        if (error.archivo == null) return;
        EditorArchivo editor = abrirArchivo(error.archivo);
        if (editor != null) editor.irA(error.linea, error.columna, error.longitud);
    }
}
