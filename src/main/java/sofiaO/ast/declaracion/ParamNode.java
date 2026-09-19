package sofiaO.ast.declaracion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;

/** Un parámetro de función/método/constructor */
public class ParamNode extends BaseNode {

    public enum ModoPaso { VALOR, REFERENCIA_ARREGLO, REFERENCIA_ESTRUCTURA }

    public String tipo;
    public String nombre;
    public ModoPaso modo;

    public ParamNode(String tipo, String nombre, ModoPaso modo, int line, int column) {
        super(line, column);
        this.tipo = tipo;
        this.nombre = nombre;
        this.modo = modo;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
