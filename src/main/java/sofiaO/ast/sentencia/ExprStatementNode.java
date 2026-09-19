package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

/** Envuelve una llamada usada como sentencia: misObjetos[9].hablar(...); */
public class ExprStatementNode extends BaseNode implements Statement {
    public Expression expresion;

    public ExprStatementNode(Expression expresion, int line, int column) {
        super(line, column);
        this.expresion = expresion;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
