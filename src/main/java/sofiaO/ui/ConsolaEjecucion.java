package sofiaO.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import sofiaO.servicio.EjecutorC;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Ventana de consola para ejecutar el .exe compilado del código C generado.
 * Permite ver la salida del programa y escribirle entrada (los "leer" del lenguaje
 * se traducen a scanf, así que el programa puede pedir datos por teclado).
 */
final class ConsolaEjecucion extends Stage {

    private final TextArea salida = new TextArea();
    private final TextField entrada = new TextField();
    private final Button enviar = new Button("Enviar");
    private final Button detener = new Button("Detener");
    private final Label estado = new Label("Ejecutando…");

    private Process proceso;
    private Path carpetaTemporal;
    private volatile boolean detenidoPorUsuario = false;

    ConsolaEjecucion(Window dueno, String css, String titulo) {
        initOwner(dueno);
        setTitle("Ejecutar — " + titulo);

        salida.setEditable(false);
        salida.setWrapText(true);
        salida.getStyleClass().add("consola-salida");

        entrada.setPromptText("Escribe la entrada del programa y presiona Enter…");
        entrada.setOnAction(e -> enviarLinea());
        enviar.setOnAction(e -> enviarLinea());
        detener.setOnAction(e -> detenerProceso());
        entrada.setDisable(true);
        enviar.setDisable(true);

        HBox filaEntrada = new HBox(6, entrada, enviar);
        HBox.setHgrow(entrada, Priority.ALWAYS);
        filaEntrada.getStyleClass().add("barra-panel");

        HBox barraSuperior = new HBox(estado);
        barraSuperior.getStyleClass().add("barra-panel");
        HBox.setHgrow(estado, Priority.ALWAYS);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        barraSuperior.getChildren().addAll(espacio, detener);

        BorderPane raiz = new BorderPane();
        raiz.setTop(barraSuperior);
        raiz.setCenter(salida);
        raiz.setBottom(filaEntrada);
        BorderPane.setMargin(salida, new Insets(0));

        Scene escena = new Scene(raiz, 720, 460);
        if (css != null) escena.getStylesheets().add(css);
        setScene(escena);

        setOnCloseRequest(e -> detenerProceso());
    }

    /** Compila y arranca el programa; debe llamarse fuera del hilo de la interfaz. */
    void compilarYEjecutar(String codigoC, String nombreBase) {
        try {
            EjecutorC.ResultadoCompilacionC compilacion = EjecutorC.compilar(codigoC, nombreBase);
            if (!compilacion.exito) {
                Platform.runLater(() -> {
                    estado.setText("No se pudo compilar.");
                    agregarSalida(compilacion.mensaje);
                });
                return;
            }
            this.carpetaTemporal = compilacion.carpetaTemporal;
            if (!compilacion.mensaje.isBlank()) {
                Platform.runLater(() -> agregarSalida(compilacion.mensaje));
            }
            proceso = EjecutorC.ejecutar(compilacion.ejecutable);
            Platform.runLater(() -> {
                estado.setText("Ejecutando…");
                entrada.setDisable(false);
                enviar.setDisable(false);
                entrada.requestFocus();
            });
            leerSalidaDelProceso();
            int codigo = proceso.waitFor();
            Platform.runLater(() -> {
                estado.setText(detenidoPorUsuario ? "Detenido por el usuario." : "Terminó con código " + codigo + ".");
                entrada.setDisable(true);
                enviar.setDisable(true);
                detener.setDisable(true);
            });
        } catch (IOException e) {
            Platform.runLater(() -> {
                estado.setText("Error al ejecutar.");
                agregarSalida("No se pudo ejecutar el programa: " + e.getMessage());
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            EjecutorC.borrarCarpeta(carpetaTemporal);
        }
    }

    private void leerSalidaDelProceso() {
        try (var lector = proceso.getInputStream()) {
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = lector.read(buffer)) != -1) {
                String texto = new String(buffer, 0, leidos, StandardCharsets.UTF_8);
                Platform.runLater(() -> agregarSalida(texto, false));
            }
        } catch (IOException e) {
            // El proceso terminó y cerró sus flujos; no es un error.
        }
    }

    private void enviarLinea() {
        if (proceso == null || !proceso.isAlive()) return;
        String texto = entrada.getText();
        agregarSalida(texto + "\n");
        entrada.clear();
        try {
            OutputStream flujo = proceso.getOutputStream();
            PrintWriter escritor = new PrintWriter(flujo, true, StandardCharsets.UTF_8);
            escritor.println(texto);
            escritor.flush();
        } catch (UncheckedIOException e) {
            agregarSalida("\n[No se pudo enviar la entrada al programa]\n");
        }
    }

    private void detenerProceso() {
        detenidoPorUsuario = true;
        if (proceso != null && proceso.isAlive()) proceso.destroyForcibly();
    }

    private void agregarSalida(String texto) {
        agregarSalida(texto, true);
    }

    private void agregarSalida(String texto, boolean conSaltoFinal) {
        salida.appendText(conSaltoFinal && !texto.endsWith("\n") ? texto + "\n" : texto);
    }
}
