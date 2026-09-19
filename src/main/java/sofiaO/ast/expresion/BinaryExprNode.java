package sofiaO.ast.expresion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;

/** a + b, a * b, a && b, a == b, etc. (los 3 lenguajes) */
public class BinaryExprNode extends BaseNode implements Expression {
    public String operador; // +, -, *, /, %, ==, !=, <, >, <=, >=, &&, ||
    public Expression izquierda;
    public Expression derecha;

    public BinaryExprNode(String operador, Expression izquierda, Expression derecha, int line, int column) {
        super(line, column);
        this.operador = operador;
        this.izquierda = izquierda;
        this.derecha = derecha;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}