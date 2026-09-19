package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

import java.util.List;

/** si/sino/contrario (Y?) - if/else if/else (Zetariano) - si/aliter/finis (Pig Latin) */
public class IfNode extends BaseNode implements Statement {

    /** Una rama de condición + cuerpo (cubre tanto el "si" inicial como cada "sino") */
    public static class Branch {
        public Expression condicion;
        public List<Statement> cuerpo;
        public Branch(Expression condicion, List<Statement> cuerpo) {
            this.condicion = condicion;
            this.cuerpo = cuerpo;
        }
    }

    public List<Branch> ramas;            // la primera es el "si"; el resto son "sino(cond)"
    public List<Statement> ramaContrario; // "contrario"/"else"/"aliter" final sin condición; null si no existe

    public IfNode(List<Branch> ramas, List<Statement> ramaContrario, int line, int column) {
        super(line, column);
        this.ramas = ramas;
        this.ramaContrario = ramaContrario;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
