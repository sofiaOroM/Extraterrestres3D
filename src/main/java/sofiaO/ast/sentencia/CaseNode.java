package sofiaO.ast.sentencia;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Statement;

import java.util.List;

/** caso 1: .../caso 2: ... (Y?) - case 1: .../default: ... (Zetariano) */
public class CaseNode extends BaseNode {
    public String valor;      // null si esDefault == true
    public boolean esDefault; // "siempre" (Y?) / "default" (Zetariano)
    public List<Statement> cuerpo;

    public CaseNode(String valor, boolean esDefault, List<Statement> cuerpo, int line, int column) {
        super(line, column);
        this.valor = valor;
        this.esDefault = esDefault;
        this.cuerpo = cuerpo;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
