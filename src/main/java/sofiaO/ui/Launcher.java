package sofiaO.ui;

import javafx.application.Application;

/**
 * Punto de entrada del .jar. Existe aparte de {@link MainApp} porque un jar ejecutable
 * cuya clase principal extiende Application falla con "faltan componentes de JavaFX runtime".
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
