package sofiaO.Traductor.generators;

import sofiaO.ast.ASTNode;
import sofiaO.ast.Expression;
import sofiaO.ast.declaracion.FunctionDeclNode;
import sofiaO.ast.expresion.*;
import sofiaO.semantico.ResolvedType;
import sofiaO.util.Type;

import java.util.ArrayList;
import java.util.List;

/** Genera cuartetas para expresiones: operadores, literales, accesos, llamadas, objetos nuevos. */
public class ExpressionGenerator {

    private final GeneratorContext ctx;

    public ExpressionGenerator(GeneratorContext ctx) {
        this.ctx = ctx;
    }

    private record Step(String place, String userType) {}

    // ------------------------------------------------------------------
    // Concatenación: '+' cuyo tipo resultante es CADENA.
    // Cada operando que no sea cadena se convierte primero a cadena y luego
    // se emite un 'concat'. Ej:  "Edad: " + edad  (edad es entero)
    //     t1 = int_to_str edad
    //     t2 = concat "Edad: ", t1
    // ------------------------------------------------------------------
    private String generarConcatenacion(Expression izquierda, Expression derecha) {
        String izq = ctx.generate(izquierda);
        izq = ctx.asString(ctx.typeOfOperand(izquierda, izq), izq);
        String der = ctx.generate(derecha);
        der = ctx.asString(ctx.typeOfOperand(derecha, der), der);
        String temp = ctx.newTemp(Type.CADENA);
        ctx.emit("concat", izq, der, temp, Type.CADENA);
        return temp;
    }

    public String visit(BinaryExprNode n) {
        if (n.operador.equals("&&") || n.operador.equals("||")) {
            return generarCortoCircuito(n);
        }
        if (n.operador.equals("+") && n.tipoResuelto == Type.CADENA) {
            return generarConcatenacion(n.izquierda, n.derecha);
        }
        String izq = ctx.generate(n.izquierda);
        String der = ctx.generate(n.derecha);
        Type tipo = n.tipoResuelto;
        String temp = ctx.newTemp(tipo);
        ctx.emit(n.operador, izq, der, temp, tipo);
        return temp;
    }

    private String generarCortoCircuito(BinaryExprNode n) {
        String resultado = ctx.newTemp(Type.BOOL);
        String etiquetaCorto = ctx.newLabel();
        String etiquetaFin = ctx.newLabel();
        boolean esAnd = n.operador.equals("&&");

        String izq = ctx.generate(n.izquierda);
        ctx.emit(esAnd ? "if_false" : "if_true", izq, null, etiquetaCorto, Type.VOID);
        String der = ctx.generate(n.derecha);
        ctx.emit(esAnd ? "if_false" : "if_true", der, null, etiquetaCorto, Type.VOID);

        ctx.emit("=", esAnd ? "1" : "0", null, resultado, Type.BOOL);
        ctx.emit("goto", null, null, etiquetaFin, Type.VOID);
        ctx.emitLabel(etiquetaCorto);
        ctx.emit("=", esAnd ? "0" : "1", null, resultado, Type.BOOL);
        ctx.emitLabel(etiquetaFin);

        return resultado;
    }

    public String visit(UnaryExprNode n) {
        String operando = ctx.generate(n.operando);
        Type tipo = n.tipoResuelto;
        String temp = ctx.newTemp(tipo);
        ctx.emit(n.operador, operando, null, temp, tipo);
        return temp;
    }

    public String visit(TernaryExprNode n) {
        Type tipo = n.tipoResuelto;
        String resultado = ctx.newTemp(tipo);
        String etiquetaFalso = ctx.newLabel();
        String etiquetaFin = ctx.newLabel();

        String cond = ctx.generate(n.condicion);
        ctx.emit("if_false", cond, null, etiquetaFalso, Type.VOID);
        String valorV = ctx.generate(n.siVerdadero);
        ctx.emit("=", valorV, null, resultado, tipo);
        ctx.emit("goto", null, null, etiquetaFin, Type.VOID);
        ctx.emitLabel(etiquetaFalso);
        String valorF = ctx.generate(n.siFalso);
        ctx.emit("=", valorF, null, resultado, tipo);
        ctx.emitLabel(etiquetaFin);

        return resultado;
    }

    public String visit(LiteralNode n) {
        String v = n.valorCrudo;
        if (v.equals("verdadero") || v.equals("true") || v.equals("verum")) return "1";
        if (v.equals("falso") || v.equals("false") || v.equals("falsus")) return "0";
        if (v.equals("null")) return "NULL"; // en C no existe 'null'
        return v;
    }

    public String visit(IdentifierNode n) {
        boolean esAtributo = ctx.currentClassFields().contains(n.nombre)
                && !ctx.currentMethodLocals().contains(n.nombre);
        return esAtributo ? "this->" + n.nombre : n.nombre;
    }

    /** Emite la llamada a un método de 'clase' sobre 'receptor' (o a una función suelta si clase == null). */
    private Step emitirLlamadaMetodo(String clase, String metodo, String receptor,
                                     CallSuffix cs, Type tipoFallback) {
        List<String> args = new ArrayList<>();
        List<Type> tipos = new ArrayList<>();
        if (clase != null) {
            args.add(receptor);          // 'this' del método
            tipos.add(Type.CLASE);
        }
        for (Expression arg : cs.argumentos) {
            args.add(ctx.generate(arg));
            tipos.add(ctx.typeOf((ASTNode) arg));
        }

        Type tipoRetorno = tipoFallback;
        String tipoUsrRetorno = null;
        FunctionDeclNode m = ctx.findMethod(clase, metodo);
        if (m != null) {
            ResolvedType rt = ctx.resolveTypeText(m.tipoRetorno);
            tipoRetorno = rt.tipo();
            tipoUsrRetorno = rt.tipoUsuario();
        }

        String temp = ctx.newTempOf(tipoRetorno, tipoUsrRetorno);
        String nombreFuncion = clase != null ? clase + "_" + metodo : metodo;
        ctx.emitVariadic("call", nombreFuncion, temp, tipoRetorno, args, tipos);
        return new Step(temp, tipoUsrRetorno);
    }

    public String visit(PostfixExprNode n) {
        String nombreBase = (n.base instanceof IdentifierNode idb) ? idb.nombre : null;
        Type tipoFinal = n.tipoResuelto != Type.ERROR ? n.tipoResuelto : Type.ENTERO;

        // Llamada a un método de la propia clase sin 'this.':  estaVacia()  ==>  Pila_estaVacia(this)
        boolean llamadaImplicita = n.base instanceof IdentifierNode idm
                && !n.sufijos.isEmpty() && n.sufijos.get(0) instanceof CallSuffix
                && ctx.currentClass() != null && ctx.currentClassMethods().contains(idm.nombre)
                && !(ctx.context() != null && ctx.context().funciones.containsKey(idm.nombre));

        String direccion;
        String tipoUsr;
        int i = 0;
        if (llamadaImplicita) {
            Step p = emitirLlamadaMetodo(ctx.currentClass(), ((IdentifierNode) n.base).nombre, "this",
                    (CallSuffix) n.sufijos.get(0), tipoFinal);
            direccion = p.place();
            tipoUsr = p.userType();
            nombreBase = null;
            i = 1;
        } else {
            direccion = ctx.generate(n.base);
            tipoUsr = ctx.userTypeOfBase(n.base, direccion);
        }

        while (i < n.sufijos.size()) {
            int indicesSeguidos = ctx.countConsecutiveIndices(n.sufijos, i);

            if (indicesSeguidos > 0) {
                List<String> indices = new ArrayList<>();
                for (int k = 0; k < indicesSeguidos; k++) {
                    indices.add(ctx.generate(((IndexSuffix) n.sufijos.get(i + k)).indice));
                }
                String indiceFinal = ctx.flattenIndices(nombreBase, indices);
                Type tipoElemento = ctx.typeOfPlace(direccion);
                String temp = ctx.newTempOf(tipoElemento, tipoUsr);
                ctx.emit("getindex", direccion, indiceFinal, temp, tipoElemento);
                direccion = temp;
                nombreBase = null;
                i += indicesSeguidos;
                continue;
            }

            Suffix s = n.sufijos.get(i);

            // 1. MÉTODO: FieldSuffix + CallSuffix (ej. cima.getDato())
            if (s instanceof FieldSuffix fs && (i + 1 < n.sufijos.size()) && (n.sufijos.get(i + 1) instanceof CallSuffix cs)) {
                i += 2;
                String clase = tipoUsr != null ? tipoUsr : ctx.placeUserTypes().get(direccion);
                Step p = emitirLlamadaMetodo(clase, fs.nombreCampo, direccion, cs, tipoFinal);
                direccion = p.place();
                tipoUsr = p.userType();
                continue;
            }

            // 2. CAMPO NORMAL (ej. p1.x)
            if (s instanceof FieldSuffix fs) {
                ResolvedType campo = ctx.typeOfField(tipoUsr, fs.nombreCampo);
                Type tipoCampo = campo != null ? campo.tipo() : tipoFinal;
                tipoUsr = campo != null ? campo.tipoUsuario() : null;
                String temp = ctx.newTempOf(tipoCampo, tipoUsr);
                ctx.emit("getfield", direccion, fs.nombreCampo, temp, tipoCampo);
                direccion = temp;
                i++;
                continue;
            }

            // 3. LLAMADA A FUNCIÓN LIBRE (ej. funcionLibre(a, b))
            if (s instanceof CallSuffix cs) {
                Type tipoRetorno = tipoFinal;
                String tipoUsrRetorno = null;
                FunctionDeclNode f = ctx.context() != null ? ctx.context().funciones.get(direccion) : null;
                if (f != null) {
                    ResolvedType rt = ctx.resolveTypeText(f.tipoRetorno);
                    tipoRetorno = rt.tipo();
                    tipoUsrRetorno = rt.tipoUsuario();
                }
                List<String> args = new ArrayList<>();
                List<Type> tipos = new ArrayList<>();
                for (Expression arg : cs.argumentos) {
                    args.add(ctx.generate(arg));
                    tipos.add(ctx.typeOf((ASTNode) arg));
                }
                String temp = ctx.newTempOf(tipoRetorno, tipoUsrRetorno);
                ctx.emitVariadic("call", direccion, temp, tipoRetorno, args, tipos);
                direccion = temp;
                tipoUsr = tipoUsrRetorno;
                i++;
                continue;
            }

            i++;
        }
        return direccion;
    }

    public String visit(NewObjectNode n) {
        List<String> args = new ArrayList<>();
        List<Type> tipos = new ArrayList<>();

        Type tipo = Type.CLASE;
        String temp = ctx.newTemp(tipo);
        ctx.placeUserTypes().put(temp, n.nombreClase);

        args.add(temp);
        tipos.add(Type.CLASE);

        for (Expression arg : n.argumentos) {
            String lugar = ctx.generate(arg);
            args.add(lugar);
            tipos.add(ctx.typeOf((ASTNode) arg));
        }

        ctx.emitVariadic("new", n.nombreClase, temp, tipo, args, tipos);

        String initFunc = n.nombreClase + "_init";
        String tempCall = ctx.newTemp(Type.VOID);
        ctx.emitVariadic("call", initFunc, tempCall, Type.VOID, args, tipos);

        return temp;
    }

    public String visit(ArrayLiteralNode n) {
        if (n.tipoResuelto == Type.ESTRUCTURA) {
            return generarLiteralEstructura(n);
        }
        Type tipo = n.tipoResuelto;
        String temp = ctx.newTemp(tipo);
        ctx.emit("newarray", String.valueOf(n.valores.size()), null, temp, tipo);
        for (int i = 0; i < n.valores.size(); i++) {
            String valor = ctx.generate(n.valores.get(i));
            ctx.emit("setindex", temp, String.valueOf(i), valor, tipo);
        }
        return temp;
    }

    private String generarLiteralEstructura(ArrayLiteralNode n) {
        String tipoUsr = n.tipoUsuarioResuelto;
        String temp = ctx.newTempOf(Type.ESTRUCTURA, tipoUsr);
        ctx.emit("new", tipoUsr, null, temp, Type.ESTRUCTURA);

        var layout = ctx.context().layoutsEstructuras.get(tipoUsr);
        var nombresCampos = new ArrayList<>(layout.keySet());
        for (int i = 0; i < n.valores.size(); i++) {
            Expression valorExpr = n.valores.get(i);
            String valor = ctx.generate(valorExpr);
            Type tipoValor = ctx.typeOfOperand(valorExpr, valor);
            ctx.emit("setfield", temp, nombresCampos.get(i), valor, tipoValor);
        }
        return temp;
    }
}