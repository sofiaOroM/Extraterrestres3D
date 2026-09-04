package sofiaO.ast.expresion;

import sofiaO.ast.ASTNode;
import sofiaO.ast.ASTVisitor;

public class IdentifierNode implements ASTNode {
    public int line;
    public int column;
    public String nombre;
    public IdentifierNode(int line, int column, String nombre) {
        this.line = line;
        this.column = column;
        this.nombre = nombre;
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