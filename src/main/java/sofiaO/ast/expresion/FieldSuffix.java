package sofiaO.ast.expresion;

/** .campo  (ej. p1.promedio, miObjeto.nombre) */
public class FieldSuffix implements Suffix {
    public String nombreCampo;
    public FieldSuffix(String nombreCampo) { this.nombreCampo = nombreCampo; }
}
