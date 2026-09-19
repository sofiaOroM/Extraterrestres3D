package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Statement;

/** continuar (Y?) / continue; (Zetariano) / perge; (Pig Latin) */
public class ContinueNode extends BaseNode implements Statement {
    public ContinueNode(int line, int column) { super(line, column); }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
