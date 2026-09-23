package sofiaO.ui;

import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import sofiaO.servicio.Lenguaje;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Árbol de la carpeta de trabajo: abrir, crear, renombrar, eliminar y descargar archivos y carpetas. */
final class PanelArbol extends VBox {

    /** Lo que el árbol le pide a la ventana principal cuando el usuario elige una opción. */
    interface Acciones {
        void abrir(Path archivo);
        void nuevoArchivo(Path carpeta);
        void nuevaCarpeta(Path carpeta);
        void renombrar(Path ruta);
        void eliminar(Path ruta);
        void descargar(Path ruta);
        void abrirCarpeta();
    }

    private static final int PROFUNDIDAD_MAXIMA = 12;

    private final TreeView<Path> arbol = new TreeView<>();
    private final Label encabezado = new Label("Carpeta de trabajo");
    private final Acciones acciones;
    private Path raiz;
    private Consumer<Path> alSeleccionar = ruta -> {};

    PanelArbol(Acciones acciones) {
        this.acciones = acciones;
        getStyleClass().add("panel-arbol");
        encabezado.getStyleClass().add("titulo-panel");

        arbol.setShowRoot(true);
        arbol.setCellFactory(vista -> new CeldaArchivo());
        arbol.setContextMenu(crearMenuContextual());

        // Mientras no haya carpeta abierta, en lugar del árbol se invita a abrir una.
        VBox vacio = new VBox(10, new Label("Aún no hay una carpeta abierta."), botonAbrirCarpeta());
        vacio.setAlignment(Pos.CENTER);
        vacio.getStyleClass().add("vacio");
        vacio.visibleProperty().bind(arbol.rootProperty().isNull());
        arbol.visibleProperty().bind(arbol.rootProperty().isNotNull());
        StackPane contenido = new StackPane(arbol, vacio);
        VBox.setVgrow(contenido, Priority.ALWAYS);

        arbol.getSelectionModel().selectedItemProperty().addListener((obs, anterior, actual) -> {
            if (actual != null && actual.getValue() != null) this.alSeleccionar.accept(actual.getValue());
        });
        arbol.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) seleccion().filter(Files::isRegularFile).ifPresent(acciones::abrir);
        });

        getChildren().addAll(encabezado, contenido);
    }

    void setAlSeleccionar(Consumer<Path> accion) { this.alSeleccionar = accion; }

    Path getRaiz() { return raiz; }

    Optional<Path> seleccion() {
        TreeItem<Path> item = arbol.getSelectionModel().getSelectedItem();
        return item == null ? Optional.empty() : Optional.ofNullable(item.getValue());
    }

    /** Carpeta donde crear algo nuevo: la seleccionada, la que contiene al archivo seleccionado, o la raíz. */
    Path carpetaActiva() {
        Optional<Path> elegido = seleccion();
        if (elegido.isEmpty()) return raiz;
        Path ruta = elegido.get();
        return Files.isDirectory(ruta) ? ruta : ruta.getParent();
    }

    void establecerRaiz(Path nuevaRaiz) {
        this.raiz = nuevaRaiz;
        encabezado.setText(nuevaRaiz == null ? "Carpeta de trabajo" : nuevaRaiz.getFileName() == null
                ? nuevaRaiz.toString() : nuevaRaiz.getFileName().toString());
        reconstruir(Set.of());
    }

    /** Vuelve a leer el disco sin cerrar las carpetas que estaban abiertas. */
    void actualizar() {
        Set<Path> abiertas = new HashSet<>();
        if (arbol.getRoot() != null) recolectarAbiertas(arbol.getRoot(), abiertas);
        Optional<Path> elegido = seleccion();
        reconstruir(abiertas);
        elegido.ifPresent(this::seleccionar);
    }

    void seleccionar(Path ruta) {
        TreeItem<Path> item = buscar(arbol.getRoot(), ruta);
        if (item == null) return;
        for (TreeItem<Path> padre = item.getParent(); padre != null; padre = padre.getParent()) padre.setExpanded(true);
        arbol.getSelectionModel().select(item);
        arbol.scrollTo(arbol.getRow(item));
    }

    // ---------------- construcción del árbol ----------------

    private void reconstruir(Set<Path> abiertas) {
        if (raiz == null || !Files.isDirectory(raiz)) {
            arbol.setRoot(null);
            return;
        }
        TreeItem<Path> item = construir(raiz, abiertas, 0);
        item.setExpanded(true);
        arbol.setRoot(item);
    }

    private TreeItem<Path> construir(Path carpeta, Set<Path> abiertas, int profundidad) {
        TreeItem<Path> item = new TreeItem<>(carpeta);
        item.setExpanded(abiertas.contains(carpeta));
        if (profundidad >= PROFUNDIDAD_MAXIMA || Files.isSymbolicLink(carpeta)) return item;

        List<Path> hijos = new ArrayList<>();
        try (Stream<Path> listado = Files.list(carpeta)) {
            listado.filter(p -> !p.getFileName().toString().startsWith(".")).forEach(hijos::add);
        } catch (IOException ignorada) {
            return item;
        }
        hijos.sort(Comparator
                .comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

        for (Path hijo : hijos) {
            if (Files.isDirectory(hijo)) item.getChildren().add(construir(hijo, abiertas, profundidad + 1));
            else item.getChildren().add(new TreeItem<>(hijo));
        }
        return item;
    }

    private void recolectarAbiertas(TreeItem<Path> item, Set<Path> destino) {
        if (item.isExpanded() && item.getValue() != null) destino.add(item.getValue());
        for (TreeItem<Path> hijo : item.getChildren()) recolectarAbiertas(hijo, destino);
    }

    private TreeItem<Path> buscar(TreeItem<Path> desde, Path ruta) {
        if (desde == null) return null;
        if (ruta.equals(desde.getValue())) return desde;
        for (TreeItem<Path> hijo : desde.getChildren()) {
            TreeItem<Path> encontrado = buscar(hijo, ruta);
            if (encontrado != null) return encontrado;
        }
        return null;
    }

    // ---------------- menú contextual y celdas ----------------

    private Button botonAbrirCarpeta() {
        Button boton = new Button("Abrir carpeta…");
        boton.setOnAction(e -> acciones.abrirCarpeta());
        return boton;
    }

    private ContextMenu crearMenuContextual() {
        MenuItem nuevoArchivo = new MenuItem("Nuevo archivo…");
        nuevoArchivo.setOnAction(e -> { if (raiz != null) acciones.nuevoArchivo(carpetaActiva()); });
        MenuItem nuevaCarpeta = new MenuItem("Nueva carpeta…");
        nuevaCarpeta.setOnAction(e -> { if (raiz != null) acciones.nuevaCarpeta(carpetaActiva()); });
        MenuItem renombrar = new MenuItem("Renombrar…");
        renombrar.setOnAction(e -> seleccion().ifPresent(acciones::renombrar));
        MenuItem eliminar = new MenuItem("Eliminar");
        eliminar.setOnAction(e -> seleccion().ifPresent(acciones::eliminar));
        MenuItem descargar = new MenuItem("Descargar…");
        descargar.setOnAction(e -> seleccion().ifPresent(acciones::descargar));
        MenuItem actualizar = new MenuItem("Actualizar");
        actualizar.setOnAction(e -> actualizar());

        ContextMenu menu = new ContextMenu(nuevoArchivo, nuevaCarpeta, new SeparatorMenuItem(),
                renombrar, eliminar, descargar, new SeparatorMenuItem(), actualizar);
        menu.setOnShowing(e -> {
            boolean hayElemento = seleccion().isPresent();
            boolean esRaiz = seleccion().map(p -> p.equals(raiz)).orElse(false);
            nuevoArchivo.setDisable(raiz == null);
            nuevaCarpeta.setDisable(raiz == null);
            renombrar.setDisable(!hayElemento || esRaiz);
            eliminar.setDisable(!hayElemento || esRaiz);
            descargar.setDisable(!hayElemento);
        });
        return menu;
    }

    private final class CeldaArchivo extends TreeCell<Path> {
        CeldaArchivo() {
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !isEmpty() && getItem() != null && Files.isRegularFile(getItem())) {
                    acciones.abrir(getItem());
                }
            });
        }

        @Override
        protected void updateItem(Path ruta, boolean vacia) {
            super.updateItem(ruta, vacia);
            getStyleClass().removeAll("carpeta", "arch-y", "arch-z", "arch-pig");
            if (vacia || ruta == null) {
                setText(null);
                return;
            }
            Path nombre = ruta.getFileName();
            setText(nombre == null ? ruta.toString() : nombre.toString());

            if (Files.isDirectory(ruta)) {
                getStyleClass().add("carpeta");
            } else {
                Lenguaje.de(ruta).ifPresent(l -> getStyleClass().add(switch (l) {
                    case Y -> "arch-y";
                    case ZETARIANO -> "arch-z";
                    case PIG -> "arch-pig";
                }));
            }
        }
    }
}
