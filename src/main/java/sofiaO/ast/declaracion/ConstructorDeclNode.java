package sofiaO.ast.declaracion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Declaration;
import sofiaO.ast.Statement;

import java.util.List;

/** public Persona(String nombreParametro, int edadParametro) { ... } (Zetariano) */
public class ConstructorDeclNode extends BaseNode implements Declaration {
    public String claseDuena;
    public List<ParamNode> parametros;
    public List<Statement> cuerpo;

    public ConstructorDeclNode(String claseDuena, List<ParamNode> parametros, List<Statement> cuerpo,
                               int line, int column) {
        super(line, column);
        this.claseDuena = claseDuena;
        this.parametros = parametros;
        this.cuerpo = cuerpo;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
