package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

import java.util.List;

/** para(...;...;...): (Y?) - for(...;...;...){...} (Zetariano) - per(...;...;...){...} (Pig Latin) */
public class ForNode extends BaseNode implements Statement {
    public Statement inicializacion; // VarDeclNode o AssignNode; puede ser null (for(;;))
    public Expression condicion;     // puede ser null (ciclo infinito)
    public Statement actualizacion;  // IncrDecrNode o AssignNode; puede ser null
    public List<Statement> cuerpo;

    public ForNode(Statement inicializacion, Expression condicion, Statement actualizacion,
                    List<Statement> cuerpo, int line, int column) {
        super(line, column);
        this.inicializacion = inicializacion;
        this.condicion = condicion;
        this.actualizacion = actualizacion;
        this.cuerpo = cuerpo;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
