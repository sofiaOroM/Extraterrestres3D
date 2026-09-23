package sofiaO.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class MainApp extends Application {

    @Override
    public void start(Stage escenario) {
        String css = Objects.requireNonNull(getClass().getResource("/sofiaO/ui/styles.css"),
                "No se encontró tema.css en los recursos").toExternalForm();
        Dialogos.configurar(css);

        VentanaPrincipal ventana = new VentanaPrincipal(escenario);
        Scene escena = new Scene(ventana.getRaiz(), 1360, 820);
        escena.getStylesheets().add(css);

        escenario.setTitle("Extraterrestre3D");
        escenario.setScene(escena);
        escenario.setMinWidth(900);
        escenario.setMinHeight(600);
        escenario.show();
        ventana.alMostrarse();
    }

    @Override
    public void stop() {
        VentanaPrincipal.detenerTrabajosEnSegundoPlano();
    }
}
