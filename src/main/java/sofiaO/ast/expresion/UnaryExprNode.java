package sofiaO.ast.expresion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;

/** !condicion (Y?/Zetariano) / -x (Zetariano) */
public class UnaryExprNode extends BaseNode implements Expression {
    public String operador; // "!" o "-"
    public Expression operando;

    public UnaryExprNode(String operador, Expression operando, int line, int column) {
        super(line, column);
        this.operador = operador;
        this.operando = operando;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
