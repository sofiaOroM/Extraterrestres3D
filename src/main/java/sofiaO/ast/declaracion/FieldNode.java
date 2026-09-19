package sofiaO.ast.declaracion;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;

import java.util.Collections;
import java.util.List;

/** Un atributo dentro de una estructura (Y?) o una clase (Zetariano) */
public class FieldNode extends BaseNode {
    public String tipo;
    public String nombre;
    public List<Integer> dimensionesArreglo; // vacío si no es arreglo (ej. miArray[10] -> [10])

    public FieldNode(String tipo, String nombre, List<Integer> dimensionesArreglo, int line, int column) {
        super(line, column);
        this.tipo = tipo;
        this.nombre = nombre;
        this.dimensionesArreglo = dimensionesArreglo == null ? Collections.emptyList() : dimensionesArreglo;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
