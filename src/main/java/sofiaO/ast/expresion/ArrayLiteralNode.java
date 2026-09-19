package sofiaO.ast.expresion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;

import java.util.List;

/** {10,20,30,40,50} / {"Hola","Adios"} / {"Valeria",25,{"Avenida Central",500}} (anidado, structs) */
public class ArrayLiteralNode extends BaseNode implements Expression {
    public List<Expression> valores;

    public ArrayLiteralNode(List<Expression> valores, int line, int column) {
        super(line, column);
        this.valores = valores;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
