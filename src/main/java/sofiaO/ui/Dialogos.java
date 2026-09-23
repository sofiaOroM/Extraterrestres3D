package sofiaO.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import java.util.Optional;

/** Cuadros de diálogo con el mismo tema que la ventana principal. */
final class Dialogos {

    private static String css;

    private Dialogos() {}

    static void configurar(String hojaDeEstilos) {
        css = hojaDeEstilos;
    }

    enum Decision { GUARDAR, DESCARTAR, CANCELAR }

    static void info(Window dueno, String titulo, String mensaje) {
        mostrar(Alert.AlertType.INFORMATION, dueno, titulo, mensaje);
    }

    static void error(Window dueno, String titulo, String mensaje) {
        mostrar(Alert.AlertType.ERROR, dueno, titulo, mensaje);
    }

    private static void mostrar(Alert.AlertType tipo, Window dueno, String titulo, String mensaje) {
        Alert alerta = crear(tipo, dueno, titulo, mensaje);
        alerta.showAndWait();
    }

    static boolean confirmar(Window dueno, String titulo, String mensaje, String textoAceptar) {
        ButtonType aceptar = new ButtonType(textoAceptar, ButtonBar.ButtonData.OK_DONE);
        Alert alerta = crear(Alert.AlertType.CONFIRMATION, dueno, titulo, mensaje);
        alerta.getButtonTypes().setAll(aceptar, ButtonType.CANCEL);
        Optional<ButtonType> respuesta = alerta.showAndWait();
        return respuesta.isPresent() && respuesta.get() == aceptar;
    }

    static Decision preguntarGuardar(Window dueno, String nombreArchivo) {
        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.YES);
        ButtonType descartar = new ButtonType("No guardar", ButtonBar.ButtonData.NO);
        Alert alerta = crear(Alert.AlertType.CONFIRMATION, dueno, "Cambios sin guardar",
                "«" + nombreArchivo + "» tiene cambios sin guardar. ¿Quieres guardarlos antes de cerrar?");
        alerta.getButtonTypes().setAll(guardar, descartar, ButtonType.CANCEL);
        Optional<ButtonType> respuesta = alerta.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() == ButtonType.CANCEL) return Decision.CANCELAR;
        return respuesta.get() == guardar ? Decision.GUARDAR : Decision.DESCARTAR;
    }

    static Optional<String> pedirTexto(Window dueno, String titulo, String pregunta, String valorInicial) {
        TextInputDialog dialogo = new TextInputDialog(valorInicial);
        if (dueno != null) dialogo.initOwner(dueno);
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(null);
        dialogo.setContentText(pregunta);
        if (css != null) dialogo.getDialogPane().getStylesheets().add(css);
        return dialogo.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private static Alert crear(Alert.AlertType tipo, Window dueno, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        if (dueno != null) alerta.initOwner(dueno);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        if (css != null) alerta.getDialogPane().getStylesheets().add(css);
        return alerta;
    }
}
