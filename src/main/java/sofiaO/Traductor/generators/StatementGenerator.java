package sofiaO.Traductor.generators;

import sofiaO.ast.ASTNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;
import sofiaO.ast.declaracion.VarDeclNode;
import sofiaO.ast.expresion.FieldSuffix;
import sofiaO.ast.expresion.IdentifierNode;
import sofiaO.ast.expresion.IndexSuffix;
import sofiaO.ast.expresion.PostfixExprNode;
import sofiaO.ast.expresion.Suffix;
import sofiaO.ast.sentencia.*;
import sofiaO.semantico.ResolvedType;
import sofiaO.util.Type;

import java.util.ArrayList;
import java.util.List;

/** Genera cuartetas para sentencias: asignaciones, condicionales, ciclos, print/read, return... */
public class StatementGenerator {

    private final GeneratorContext ctx;

    public StatementGenerator(GeneratorContext ctx) {
        this.ctx = ctx;
    }

    public String visit(AssignNode n) {
        String valor = ctx.generate((ASTNode) n.valor);

        if (!n.operador.equals("=")) {
            String operadorBase = n.operador.substring(0, 1);
            String valorActual = ctx.generate((ASTNode) n.destino);
            Type tipoOperacion = ctx.typeOf((ASTNode) n.destino);
            String temp = ctx.newTemp(tipoOperacion);
            if (operadorBase.equals("+") && tipoOperacion == Type.CADENA) {
                // cadena += algo   ==>   cadena = cadena + algo   (concatenación)
                String der = ctx.asString(ctx.typeOfOperand(n.valor, valor), valor);
                ctx.emit("concat", valorActual, der, temp, Type.CADENA);
            } else {
                ctx.emit(operadorBase, valorActual, valor, temp, tipoOperacion);
            }
            valor = temp;
        }

        escribirEnDestino(n.destino, valor);
        return null;
    }

    private void escribirEnDestino(Expression destino, String valor) {
        if (destino instanceof IdentifierNode id) {
            boolean esAtributo = ctx.currentClassFields().contains(id.nombre)
                    && !ctx.currentMethodLocals().contains(id.nombre);
            String lugar = esAtributo ? "this->" + id.nombre : id.nombre;
            ctx.emit("=", valor, null, lugar, ctx.typeOfPlace(id.nombre));
            return;
        }

        if (destino instanceof PostfixExprNode postfix) {
            String nombreBase = (postfix.base instanceof IdentifierNode idb) ? idb.nombre : null;
            String direccionBase = ctx.generate(postfix.base);
            String tipoUsrBase = ctx.userTypeOfBase(postfix.base, direccionBase);
            List<Suffix> sufijos = postfix.sufijos;

            int i = 0;
            while (i < sufijos.size()) {
                int indicesSeguidos = ctx.countConsecutiveIndices(sufijos, i);

                if (indicesSeguidos > 0) {
                    List<String> indices = new ArrayList<>();
                    for (int k = 0; k < indicesSeguidos; k++) {
                        indices.add(ctx.generate(((IndexSuffix) sufijos.get(i + k)).indice));
                    }
                    String indiceFinal = ctx.flattenIndices(nombreBase, indices);

                    if (i + indicesSeguidos >= sufijos.size()) {
                        ctx.emit("setindex", direccionBase, indiceFinal, valor, ctx.typeOfPlace(direccionBase));
                        return;
                    }
                    String temp = ctx.newTempOf(ctx.typeOfPlace(direccionBase), tipoUsrBase);
                    ctx.emit("getindex", direccionBase, indiceFinal, temp, ctx.typeOfPlace(direccionBase));
                    direccionBase = temp;
                    nombreBase = null;
                    i += indicesSeguidos;
                    continue;
                }

                Suffix sufijo = sufijos.get(i);
                boolean esUltimo = (i == sufijos.size() - 1);

                if (sufijo instanceof FieldSuffix fs) {
                    if (esUltimo) {
                        ctx.emit("setfield", direccionBase, fs.nombreCampo, valor, Type.ERROR);
                        return;
                    }
                    ResolvedType campo = ctx.typeOfField(tipoUsrBase, fs.nombreCampo);
                    Type tipoCampo = campo != null ? campo.tipo() : Type.ERROR;
                    tipoUsrBase = campo != null ? campo.tipoUsuario() : null;
                    String temp = ctx.newTempOf(tipoCampo, tipoUsrBase);
                    ctx.emit("getfield", direccionBase, fs.nombreCampo, temp, tipoCampo);
                    direccionBase = temp;
                } else {
                    throw new IllegalStateException("No se puede asignar al resultado de una llamada");
                }
                i++;
            }
        }
    }

    public String visit(IncrDecrNode n) {
        boolean esAtributo = ctx.currentClassFields().contains(n.nombreVariable)
                && !ctx.currentMethodLocals().contains(n.nombreVariable);
        String lugar = esAtributo ? "this->" + n.nombreVariable : n.nombreVariable;

        String operador = n.operador.equals("++") ? "+" : "-";
        Type tipo = ctx.typeOfVariable(n.nombreVariable);
        String temp = ctx.newTemp(tipo);
        ctx.emit(operador, n.nombreVariable, "1", temp, tipo);
        ctx.emit("=", temp, null, n.nombreVariable, tipo);
        return null;
    }

    public String visit(IfNode n) {
        String etiquetaFin = ctx.newLabel();
        for (IfNode.Branch rama : n.ramas) {
            String cond = ctx.generate((ASTNode) rama.condicion);
            String etiquetaSiguiente = ctx.newLabel();
            ctx.emit("if_false", cond, null, etiquetaSiguiente, Type.VOID);
            for (Statement s : rama.cuerpo) ctx.generate(s);
            ctx.emit("goto", null, null, etiquetaFin, Type.VOID);
            ctx.emitLabel(etiquetaSiguiente);
        }
        if (n.ramaContrario != null) {
            for (Statement s : n.ramaContrario) ctx.generate(s);
        }
        ctx.emitLabel(etiquetaFin);
        return null;
    }

    public String visit(SwitchNode n) {
        String selector = ctx.generate((ASTNode) n.selector);
        String etiquetaFin = ctx.newLabel();

        List<String> etiquetas = new ArrayList<>();
        String etiquetaDefault = null;
        for (CaseNode c : n.casos) {
            String e = ctx.newLabel();
            etiquetas.add(e);
            if (c.esDefault) etiquetaDefault = e;
        }

        ctx.startLoop(etiquetaFin, etiquetaFin);

        for (int i = 0; i < n.casos.size(); i++) {
            CaseNode c = n.casos.get(i);
            if (c.esDefault) continue;
            String temp = ctx.newTemp(Type.BOOL);
            ctx.emit("==", selector, c.valor, temp, Type.BOOL);
            ctx.emit("if_true", temp, null, etiquetas.get(i), Type.VOID);
        }
        ctx.emit("goto", null, null, etiquetaDefault != null ? etiquetaDefault : etiquetaFin, Type.VOID);

        for (int i = 0; i < n.casos.size(); i++) {
            ctx.emitLabel(etiquetas.get(i));
            for (Statement s : n.casos.get(i).cuerpo) ctx.generate(s);
        }

        ctx.emitLabel(etiquetaFin);
        ctx.endLoop();
        return null;
    }

    public String visit(CaseNode n) {
        for (Statement s : n.cuerpo) ctx.generate(s);
        return null;
    }

    public String visit(WhileNode n) {
        String etiquetaInicio = ctx.newLabel();
        String etiquetaFin = ctx.newLabel();
        ctx.startLoop(etiquetaInicio, etiquetaFin);

        ctx.emitLabel(etiquetaInicio);
        String cond = ctx.generate((ASTNode) n.condicion);
        ctx.emit("if_false", cond, null, etiquetaFin, Type.VOID);
        for (Statement s : n.cuerpo) ctx.generate(s);
        ctx.emit("goto", null, null, etiquetaInicio, Type.VOID);
        ctx.emitLabel(etiquetaFin);

        ctx.endLoop();
        return null;
    }

    public String visit(DoWhileNode n) {
        String etiquetaInicio = ctx.newLabel();
        String etiquetaCond = ctx.newLabel();
        String etiquetaFin = ctx.newLabel();
        ctx.startLoop(etiquetaCond, etiquetaFin);

        ctx.emitLabel(etiquetaInicio);
        for (Statement s : n.cuerpo) ctx.generate(s);
        ctx.emitLabel(etiquetaCond);
        String cond = ctx.generate((ASTNode) n.condicion);
        ctx.emit("if_true", cond, null, etiquetaInicio, Type.VOID);
        ctx.emitLabel(etiquetaFin);

        ctx.endLoop();
        return null;
    }

    public String visit(ForNode n) {
        // Si el 'per' declara su propia variable ("esto i : numerus 0; ..."), esa i debe
        // quedar aislada del resto de la función: si ya existe otra "i" (global, u otro
        // 'per' anterior con su propia "i"), sin esto se generarían dos "int i;" seguidos
        // en el mismo main()/función de C -> error de "redeclaration". Envolver el ciclo
        // completo en un bloque "{ }" de C le da a esa declaración su propio alcance, tal
        // como haría un for(...) real en C, sin tocar nada fuera del ciclo.
        boolean declaraSuPropiaVariable = n.inicializacion instanceof VarDeclNode;
        if (declaraSuPropiaVariable) ctx.emit("blockstart", null, null, null, Type.VOID);

        if (n.inicializacion != null) ctx.generate(n.inicializacion);

        String etiquetaInicio = ctx.newLabel();
        String etiquetaActualizacion = ctx.newLabel();
        String etiquetaFin = ctx.newLabel();
        ctx.startLoop(etiquetaActualizacion, etiquetaFin);

        ctx.emitLabel(etiquetaInicio);
        if (n.condicion != null) {
            String cond = ctx.generate((ASTNode) n.condicion);
            ctx.emit("if_false", cond, null, etiquetaFin, Type.VOID);
        }
        for (Statement s : n.cuerpo) ctx.generate(s);
        ctx.emitLabel(etiquetaActualizacion);
        if (n.actualizacion != null) ctx.generate(n.actualizacion);
        ctx.emit("goto", null, null, etiquetaInicio, Type.VOID);
        ctx.emitLabel(etiquetaFin);

        ctx.endLoop();
        if (declaraSuPropiaVariable) ctx.emit("blockend", null, null, null, Type.VOID);
        return null;
    }

    public String visit(BreakNode n) {
        ctx.emit("goto", null, null, ctx.currentLoop().romper(), Type.VOID);
        return null;
    }

    public String visit(ContinueNode n) {
        ctx.emit("goto", null, null, ctx.currentLoop().continuar(), Type.VOID);
        return null;
    }

    public String visit(ReturnNode n) {
        String valor = n.valor == null ? null : ctx.generate((ASTNode) n.valor);
        ctx.emit("return", valor, null, null, n.valor == null ? Type.VOID : ctx.typeOf((ASTNode) n.valor));
        return null;
    }

    public String visit(PrintNode n) {
        List<String> direcciones = new ArrayList<>();
        List<Type> tipos = new ArrayList<>();
        for (Expression arg : n.argumentos) {
            String lugar = ctx.generate(arg);
            direcciones.add(lugar);
            tipos.add(ctx.typeOfOperand(arg, lugar));
        }
        ctx.emitVariadic("print", null, null, Type.VOID, direcciones, tipos);
        return null;
    }

    public String visit(ReadNode n) {
        Type tipo = n.tipoResuelto == Type.ERROR ? Type.CADENA : n.tipoResuelto;
        String temp = ctx.newTemp(tipo);
        ctx.emit("read", null, null, temp, tipo);
        if (n.variableDestino != null) {
            ctx.emit("=", temp, null, n.variableDestino, ctx.typeOfPlace(n.variableDestino));
            return n.variableDestino;
        }
        return temp;
    }

    public String visit(ExprStatementNode n) {
        ctx.generate((ASTNode) n.expresion);
        return null;
    }
}