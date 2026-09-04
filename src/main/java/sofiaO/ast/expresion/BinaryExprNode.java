package sofiaO.ast.expresion;

import sofiaO.ast.ASTNode;
import sofiaO.ast.ASTVisitor;

public class BinaryExprNode implements ASTNode {
    public int line;
    public int column;
    public String op;           // "+", "-", "*", "/"
    public ASTNode izquierda, derecha;

    public BinaryExprNode(int line, int column, String op, ASTNode izq, ASTNode der) {
        this.line = line;
        this.column = column;
        this.op = op; this.izquierda = izq; this.derecha = der;
    }

    public <T> T accept(ASTVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public int getLine() {
        return line;
    }

    @Override
    public int getColumn() {
        return column;
    }
}

