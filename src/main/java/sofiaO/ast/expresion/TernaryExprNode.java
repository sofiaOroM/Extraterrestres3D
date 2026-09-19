package sofiaO.ast.expresion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;

/** (edad>=18)?"Es mayor de edad":"Es menor de edad" (Zetariano) */
public class TernaryExprNode extends BaseNode implements Expression {
    public Expression condicion;
    public Expression siVerdadero;
    public Expression siFalso;

    public TernaryExprNode(Expression condicion, Expression siVerdadero, Expression siFalso, int line, int column) {
        super(line, column);
        this.condicion = condicion;
        this.siVerdadero = siVerdadero;
        this.siFalso = siFalso;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
