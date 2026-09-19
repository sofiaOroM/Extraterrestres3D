package sofiaO.ast.expresion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;

import java.util.List;

/**
 * Unifica cualquier cadena de accesos de los 3 lenguajes:
 *   p1.promedio                          -> base=p1, [FieldSuffix(promedio)]
 *   numeros[0]                           -> base=numeros, [IndexSuffix(0)]
 *   matriz[i][j]                         -> base=matriz, [IndexSuffix(i), IndexSuffix(j)]
 *   misObjetos[9].hablar(x)              -> base=misObjetos, [IndexSuffix(9), FieldSuffix(hablar), CallSuffix([x])]
 *   miObjeto.apellidos[0].getNombre()    -> base=miObjeto, [FieldSuffix(apellidos), IndexSuffix(0), FieldSuffix(getNombre), CallSuffix([])]
 */
public class PostfixExprNode extends BaseNode implements Expression {
    public Expression base;      // normalmente un IdentifierNode
    public List<Suffix> sufijos; // encadenados en orden de aparición

    public PostfixExprNode(Expression base, List<Suffix> sufijos, int line, int column) {
        super(line, column);
        this.base = base;
        this.sufijos = sufijos;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
