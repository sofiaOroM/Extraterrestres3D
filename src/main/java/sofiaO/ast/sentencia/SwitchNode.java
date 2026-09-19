package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;

import java.util.List;

/** elegir(opcion): ... (Y?) - switch(opcion){...} (Zetariano). No existe en Pig Latin. */
public class SwitchNode extends BaseNode implements Statement {
    public Expression selector;
    public List<CaseNode> casos;

    public SwitchNode(Expression selector, List<CaseNode> casos, int line, int column) {
        super(line, column);
        this.selector = selector;
        this.casos = casos;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
