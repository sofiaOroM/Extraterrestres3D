package sofiaO.resaltado;

import java.util.List;

/** Un tramo consecutivo del texto con todas sus clases de estilo (puede no tener ninguna). */
public record Tramo(int longitud, List<String> estilos) {
}
