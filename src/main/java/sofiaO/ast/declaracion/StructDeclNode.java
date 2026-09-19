package sofiaO.ast.declaracion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Declaration;

import java.util.List;

/** estructura Persona: ... (Y?) */
public class StructDeclNode extends BaseNode implements Declaration {
    public String nombre;
    public List<FieldNode> campos;

    public StructDeclNode(String nombre, List<FieldNode> campos, int line, int column) {
        super(line, column);
        this.nombre = nombre;
        this.campos = campos;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
