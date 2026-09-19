package sofiaO.ast.declaracion;

import sofiaO.ast.ASTNode;
import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Declaration;
import sofiaO.ast.Statement;

import java.util.Collections;
import java.util.List;

/**
 * entero edadUsuario = 25          (Y?)
 * int total = fuerza * 2;          (Zetariano)
 * esto total : numerus fuerza*2;   (Pig Latin)
 *
 */
public class VarDeclNode extends BaseNode implements Statement, Declaration {
    public String tipo;
    public String nombre;
    public List<Integer> dimensionesArreglo; // vacío si no es arreglo
    public ASTNode inicializador;            // Expression o ArrayLiteralNode; null si no tiene

    public VarDeclNode(String tipo, String nombre, List<Integer> dimensionesArreglo,
                       ASTNode inicializador, int line, int column) {
        super(line, column);
        this.tipo = tipo;
        this.nombre = nombre;
        this.dimensionesArreglo = dimensionesArreglo == null ? Collections.emptyList() : dimensionesArreglo;
        this.inicializador = inicializador;
    }

    @Override
    public <T> T accept(ASTVisitor<T> v) { return v.visit(this); }
}
