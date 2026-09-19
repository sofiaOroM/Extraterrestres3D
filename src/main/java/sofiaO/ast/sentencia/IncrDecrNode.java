package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Statement;

/** contador++; / intentos--; */
public class IncrDecrNode extends BaseNode implements Statement {
    public String nombreVariable;
    public String operador; // "++" o "--"

    public IncrDecrNode(String nombreVariable, String operador, int line, int column) {
        super(line, column);
        this.nombreVariable = nombreVariable;
        this.operador = operador;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
