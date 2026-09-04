package sofiaO.semantico;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.expresion.*;
import sofiaO.util.Type;
import sofiaO.util.TypeMapper;

public class SemanticAnalyzer implements ASTVisitor<Type> {
    SymbolTable tabla;

    // Constructor sin argumentos (opcional)
    public SemanticAnalyzer() {
        this.tabla = new SymbolTable();
    }

    // Constructor que recibe la SymbolTable activa (el que usa tu Main)
    public SemanticAnalyzer(SymbolTable tabla) {
        this.tabla = tabla;
    }

    public SymbolTable getTabla() {
        return tabla;
    }

    public void setTabla(SymbolTable tabla) {
        this.tabla = tabla;
    }

    public Type visit(VarDeclNode n) {
        Type tipoDeclarado = TypeMapper.deTexto(n.tipo);
        if (tabla.existeEnScopeActual(n.nombre))
            throw new SemanticException("Variable ya declarada: " + n.nombre);

        if (n.inicializador != null) {
            Type tipoInit = n.inicializador.accept(this);
            if (!TypeCoercionTable.compatibles(tipoDeclarado, tipoInit))
                throw new SemanticException("No se puede asignar " + tipoInit + " a " + tipoDeclarado);
        }
        tabla.declarar(n.nombre, tipoDeclarado, n.line,n.column);
        return Type.ERROR; // las sentencias no tienen "tipo" util, se ignora
    }

    public Type visit(BinaryExprNode n) {
        Type izq = n.izquierda.accept(this);
        Type der = n.derecha.accept(this);
        return TypeCoercionTable.resultado(izq, der); // ver tabla de compatibilidad
    }

    public Type visit(IdentifierNode n) {
        Symbol s = tabla.resolver(n.nombre);
        if (s == null) throw new SemanticException("Variable no declarada: " + n.nombre);
        return s.tipo;
    }

    public Type visit(LiteralNode n) {
        if (n.valor.matches("\\d+")) return Type.ENTERO;
        if (n.valor.matches("\\d+\\.\\d+")) return Type.FLOTANTE;
        if (n.valor.startsWith("\"")) return Type.CADENA;
        if (n.valor.startsWith("'")) return Type.CARACTER;
        return Type.BOOL; // verdadero/falso, true/false, verum/falsus
    }
}
