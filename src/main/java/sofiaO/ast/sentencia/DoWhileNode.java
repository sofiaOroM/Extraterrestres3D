package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

import java.util.List;

/** hacer: ... mientras(...) (Y?) - do{...}while(...); (Zetariano) - facere{...}dum(...); (Pig Latin) */
public class DoWhileNode extends BaseNode implements Statement {
    public List<Statement> cuerpo;
    public Expression condicion;

    public DoWhileNode(List<Statement> cuerpo, Expression condicion, int line, int column) {
        super(line, column);
        this.cuerpo = cuerpo;
        this.condicion = condicion;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
