package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

/**
 * leer() (Y?) / << , var << (Pig Latin).
 * Implementa Statement Y Expression: como sentencia cuando se usa solo
 * ("leer();"), como expresión cuando su resultado se usa ("x = leer()").
 */
public class ReadNode extends BaseNode implements Statement, Expression {
    public String variableDestino; // null si no guarda el resultado ("leer()" / "<<" bare)

    public ReadNode(String variableDestino, int line, int column) {
        super(line, column);
        this.variableDestino = variableDestino;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
