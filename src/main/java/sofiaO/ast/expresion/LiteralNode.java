package sofiaO.ast.expresion;

import sofiaO.ast.ASTNode;
import sofiaO.ast.ASTVisitor;

public class LiteralNode implements ASTNode {
    public int line;
    public int column;
    public String valor;   // "25", "36.6", "\"texto\""

    public LiteralNode(int line, int column, String valor) {
        this.line = line;
        this.column = column;
        this.valor = valor;
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
