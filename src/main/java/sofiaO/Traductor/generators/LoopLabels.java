package sofiaO.Traductor.generators;

/** Las dos etiquetas que necesita un ciclo activo: adónde salta "continuar" y adónde "romper". */
public record LoopLabels(String continuar, String romper) {}