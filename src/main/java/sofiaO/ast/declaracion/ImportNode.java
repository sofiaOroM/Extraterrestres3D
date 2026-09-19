package sofiaO.ast.declaracion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Declaration;

/** import carpeta.Objeto1.z / import carpeta.Funciones.y  (Pig Latin) */
public class ImportNode extends BaseNode implements Declaration {

    public enum TipoImport { ESTRUCTURAS_Y, CLASE_Z }

    public String ruta;
    public TipoImport tipo;

    public ImportNode(String ruta, TipoImport tipo, int line, int column) {
        super(line, column);
        this.ruta = ruta;
        this.tipo = tipo;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
