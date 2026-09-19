package sofiaO.ast.declaracion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Declaration;

import java.util.List;

/** Raíz del AST de un archivo completo (.y, .z o .pig ya traducido) */
public class ProgramNode extends BaseNode {
    public List<ImportNode> imports;         // vacío para .y y .z
    public List<Declaration> declaraciones;  // structs, clases, funciones, variables globales

    public ProgramNode(List<ImportNode> imports, List<Declaration> declaraciones, int line, int column) {
        super(line, column);
        this.imports = imports;
        this.declaraciones = declaraciones;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
