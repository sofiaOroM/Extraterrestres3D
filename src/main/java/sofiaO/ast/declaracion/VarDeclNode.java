package sofiaO.ast.declaracion;

import sofiaO.ast.ASTNode;
import sofiaO.ast.ASTVisitor;

public class VarDeclNode implements ASTNode {
    public int line;
    public int column;
    public String tipo;          // "entero", "int", "numerus" -> normalizado a un Type
    public String nombre;
    public ASTNode inicializador; // puede ser null (sin valor inicial)

    public VarDeclNode(int line, int column, String tipo, String nombre, ASTNode inicializador) {
        this.line = line;
        this.column = column;
        this.tipo = tipo;
        this.nombre = nombre;
        this.inicializador = inicializador;
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
