package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Statement;

/** romper (Y?) / break; (Zetariano) / interrumpe; (Pig Latin) */
public class BreakNode extends BaseNode implements Statement {
    public BreakNode(int line, int column) { super(line, column); }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
