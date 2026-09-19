package sofiaO.ast.expresion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;

/** Referencia simple a una variable por nombre (sin campos/índices/llamadas) */
public class IdentifierNode extends BaseNode implements Expression {
    public String nombre;

    public IdentifierNode(String nombre, int line, int column) {
        super(line, column);
        this.nombre = nombre;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
