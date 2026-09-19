package sofiaO.ast.expresion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;

import java.util.List;

/** new Persona("Carlos", 25) (Zetariano) / novus Persona(12,"Profesor") (Pig Latin) */
public class NewObjectNode extends BaseNode implements Expression {
    public String nombreClase;
    public List<Expression> argumentos;

    public NewObjectNode(String nombreClase, List<Expression> argumentos, int line, int column) {
        super(line, column);
        this.nombreClase = nombreClase;
        this.argumentos = argumentos;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
