package sofiaO.Traductor;

import sofiaO.ast.ASTVisitor;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.expresion.*;

import java.util.ArrayList;
import java.util.List;

public class CodeGenVisitor implements ASTVisitor<String> {
    public List<String> codigo = new ArrayList<>();
    int contadorTemp = 0;

    String nuevoTemp() { return "t" + (contadorTemp++); }

    void emit(String linea) { codigo.add(linea); }

    @Override
    public String visit(VarDeclNode n) {
        if (n.inicializador == null) {
            // sin valor inicial: no se emite nada (o, si tu C de salida
            // requiere declarar el espacio, aquí emitirías algo como
            // "declare <tipo> <nombre>" -- lo dejamos fuera del C3D puro)
            return null;
        }
        String direccionValor = n.inicializador.accept(this); // puede disparar más líneas
        emit(n.nombre + " = " + direccionValor);
        return null;
    }

    @Override
    public String visit(BinaryExprNode n) {
        String izq = n.izquierda.accept(this);
        String der = n.derecha.accept(this);
        String temp = nuevoTemp();
        emit(temp + " = " + izq + " " + n.op + " " + der);
        return temp; // el nodo padre (VarDeclNode) usará este temporal
    }

    @Override
    public String visit(LiteralNode n) {
        return n.valor;       // una constante NO necesita temporal
    }

    @Override
    public String visit(IdentifierNode n) {
        return n.nombre;      // una variable ya declarada tampoco necesita temporal
    }
}
