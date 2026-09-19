package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

import java.util.List;

/** mientras(...) hacer (Y?) - while(...){...} (Zetariano) - dum(...){...} finis; (Pig Latin) */
public class WhileNode extends BaseNode implements Statement {
    public Expression condicion;
    public List<Statement> cuerpo;

    public WhileNode(Expression condicion, List<Statement> cuerpo, int line, int column) {
        super(line, column);
        this.condicion = condicion;
        this.cuerpo = cuerpo;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
