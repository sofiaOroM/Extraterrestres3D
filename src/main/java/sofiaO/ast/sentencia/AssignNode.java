package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

/** numeros[2]=numeros[0]*3 / alumno1.nombre="Yennifer" / x+=3 (Zetariano) */
public class AssignNode extends BaseNode implements Statement {
    public Expression destino;   // normalmente IdentifierNode o PostfixExprNode
    public String operador;      // "=", "+=", "-=", "*=" (compuestos solo existen en Zetariano)
    public Expression valor;

    public AssignNode(Expression destino, String operador, Expression valor, int line, int column) {
        super(line, column);
        this.destino = destino;
        this.operador = operador;
        this.valor = valor;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
