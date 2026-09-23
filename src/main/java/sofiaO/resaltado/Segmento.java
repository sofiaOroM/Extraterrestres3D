package sofiaO.resaltado;

/** Un tramo de texto [inicio, fin) que debe pintarse con una clase de estilo (ver {@link Estilos}). */
public record Segmento(int inicio, int fin, String estilo) {
}
