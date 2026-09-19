package sofiaO.ast.expresion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;

/** 25, 36.6, "texto", 'a', verdadero/true/verum, falso/false/falsus, null */
public class LiteralNode extends BaseNode implements Expression {
    public String valorCrudo; // texto tal cual vino del token

    public LiteralNode(String valorCrudo, int line, int column) {
        super(line, column);
        this.valorCrudo = valorCrudo;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
