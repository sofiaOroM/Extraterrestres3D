package sofiaO.ast.declaracion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Declaration;
import sofiaO.ast.Statement;

import java.util.List;

/** definir f(...): ... (Y?) / método dentro de una clase (Zetariano) / MAIOR> (Pig Latin) */
public class FunctionDeclNode extends BaseNode implements Declaration {
    public String nombre;
    public List<ParamNode> parametros;
    public String tipoRetorno;   // null si no retorna nada
    public List<Statement> cuerpo;
    public boolean esMetodo;
    public String claseDuena;    // nombre de la clase si esMetodo == true; null si no

    public FunctionDeclNode(String nombre, List<ParamNode> parametros, String tipoRetorno,
                            List<Statement> cuerpo, boolean esMetodo, String claseDuena,
                            int line, int column) {
        super(line, column);
        this.nombre = nombre;
        this.parametros = parametros;
        this.tipoRetorno = tipoRetorno;
        this.cuerpo = cuerpo;
        this.esMetodo = esMetodo;
        this.claseDuena = claseDuena;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
