package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

/** retornar 160 (Y?) / return anioActual - edad; (Zetariano) */
public class ReturnNode extends BaseNode implements Statement {
    public Expression valor; // null si no retorna nada

    public ReturnNode(Expression valor, int line, int column) {
        super(line, column);
        this.valor = valor;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
