package sofiaO.Traductor;

import sofiaO.ast.ASTNode;
import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.sentencia.*;
import sofiaO.ast.expresion.*;
import sofiaO.util.Type;
import sofiaO.util.TypeMapper;

import java.util.*;

public class QuadrupleGenerator implements ASTVisitor<String> {

    public final List<Quadruple> cuartetas = new ArrayList<>();
    public final Map<String, String> tiposUsuarioDeLugares = new LinkedHashMap<>();

    private int contadorTemp = 0;
    private int contadorLabel = 0;

    public final Map<String, Type> tiposDeLugares = new LinkedHashMap<>();
    private final Map<String, List<Integer>> dimensionesArreglos = new HashMap<>();

    private Set<String> atributosDeClaseActual = Collections.emptySet();
    private Set<String> localesDelMetodoActual = new HashSet<>();
    private String claseActual = null;
    private Set<String> metodosDeClaseActual = Collections.emptySet();

    private static class EtiquetasCiclo {
        final String continuar;
        final String romper;
        EtiquetasCiclo(String continuar, String romper) {
            this.continuar = continuar;
            this.romper = romper;
        }
    }
    private final Deque<EtiquetasCiclo> pilaCiclos = new ArrayDeque<>();

    private String nuevoTemp(Type tipo) {
        String t = "t" + (contadorTemp++);
        tiposDeLugares.put(t, tipo == null ? Type.ERROR : tipo);
        emit("declare", null, null, t, tipo);
        return t;
    }

    private String nuevaEtiqueta() { return "L" + (contadorLabel++); }

    private void emit(String op, String arg1, String arg2, String result, Type tipo) {
        cuartetas.add(new Quadruple(op, arg1, arg2, result, tipo));
    }

    private void emitVariadica(String op, String arg1, String result, Type tipo,
                               List<String> extra, List<Type> tiposExtra) {
        cuartetas.add(new Quadruple(op, arg1, null, result, tipo, extra, tiposExtra));
    }

    private void emitEtiqueta(String etiqueta) {
        cuartetas.add(new Quadruple("label", null, null, etiqueta, Type.VOID));
    }

    private Type tipoDe(ASTNode n) {
        if (n instanceof BaseNode b) return b.tipoResuelto;
        return Type.ERROR;
    }

    private Type tipoDeLugar(String lugar) {
        return tiposDeLugares.getOrDefault(lugar, Type.ERROR);
    }

    public void imprimir() {
        for (Quadruple q : cuartetas) System.out.println(q);
    }

    @Override
    public String visit(ProgramNode n) {
        for (var d : n.declaraciones) ((ASTNode) d).accept(this);
        emit("halt", null, null, null, Type.VOID);
        return null;
    }

    @Override public String visit(ImportNode n) { return null; }

    @Override
    public String visit(StructDeclNode n) {
        TypeMapper.registrarTipoUsuario(n.nombre, Type.ESTRUCTURA);
        return null;
    }

    @Override public String visit(FieldNode n) { return null; }

    @Override
    public String visit(ParamNode n) {
        Type tipo = TypeMapper.deTexto(n.tipo);
        if (tipo == Type.ERROR) {
            tipo = TypeMapper.deUsuario(n.tipo);
            if (tipo == Type.ESTRUCTURA || tipo == Type.CLASE) {
                tiposUsuarioDeLugares.put(n.nombre, n.tipo);
            }
        }
        tiposDeLugares.put(n.nombre, tipo);
        emit("param", n.nombre, null, null, tipo);
        return null;
    }

    @Override
    public String visit(ClassDeclNode n) {
        TypeMapper.registrarTipoUsuario(n.nombre, Type.CLASE);
        Set<String> nombresAtributos = new HashSet<>();
        for (var atributo : n.atributos) nombresAtributos.add(atributo.nombre);
        atributosDeClaseActual = nombresAtributos;

        for (var c : n.constructores) c.accept(this);
        for (var m : n.metodos) m.accept(this);

        atributosDeClaseActual = Collections.emptySet();
        return null;
    }

    @Override
    public String visit(FunctionDeclNode n) {
        String nombreCompleto = n.esMetodo ? n.claseDuena + "_" + n.nombre : n.nombre;
        emit("func", null, null, nombreCompleto, Type.VOID);

        localesDelMetodoActual = new HashSet<>();
        if (n.esMetodo) {
            emit("param", "this", null, null, Type.CLASE);
            tiposDeLugares.put("this", Type.CLASE);
            tiposUsuarioDeLugares.put("this", n.claseDuena);
        }
        for (ParamNode p : n.parametros) localesDelMetodoActual.add(p.nombre);

        for (ParamNode p : n.parametros) p.accept(this);
        for (Statement s : n.cuerpo) s.accept(this);
        emit("endfunc", null, null, nombreCompleto, Type.VOID);
        return null;
    }

    @Override
    public String visit(ConstructorDeclNode n) {
        String nombreCompleto = n.claseDuena + "_init";
        emit("func", null, null, nombreCompleto, Type.VOID);
        emit("param", "this", null, null, Type.CLASE);
        tiposDeLugares.put("this", Type.CLASE);
        tiposUsuarioDeLugares.put("this", n.claseDuena);

        localesDelMetodoActual = new HashSet<>();
        for (ParamNode p : n.parametros) localesDelMetodoActual.add(p.nombre);

        for (ParamNode p : n.parametros) p.accept(this);
        for (Statement s : n.cuerpo) s.accept(this);
        emit("endfunc", null, null, nombreCompleto, Type.VOID);
        return null;
    }

    @Override
    public String visit(VarDeclNode n) {
        Type tipo = TypeMapper.deTexto(n.tipo);

        if (tipo == Type.ERROR) {
            tipo = TypeMapper.deUsuario(n.tipo);
            if (tipo == Type.ESTRUCTURA || tipo == Type.CLASE) {
                tiposUsuarioDeLugares.put(n.nombre, n.tipo);
            }
        }

        tiposDeLugares.put(n.nombre, tipo);

        if (!n.dimensionesArreglo.isEmpty()) {
            dimensionesArreglos.put(n.nombre, n.dimensionesArreglo);
            int total = 1;
            for (int d : n.dimensionesArreglo) total *= d;
            emit("declare", String.valueOf(total), null, n.nombre, tipo);
        } else {
            emit("declare", null, null, n.nombre, tipo);
        }

        if (n.inicializador != null) {
            String valor = ((ASTNode) n.inicializador).accept(this);
            emit("=", valor, null, n.nombre, tipo);
        }

        return null;
    }

    private String aplanarIndices(String nombreArreglo, List<String> indices) {
        if (indices.size() == 1) return indices.get(0);

        List<Integer> dims = dimensionesArreglos.get(nombreArreglo);
        if (dims == null || dims.size() < indices.size()) {
            return indices.get(0);
        }

        String acumulado = indices.get(0);
        for (int i = 1; i < indices.size(); i++) {
            String tempMultiplicacion = nuevoTemp(Type.ENTERO);
            emit("*", acumulado, String.valueOf(dims.get(i)), tempMultiplicacion, Type.ENTERO);

            String tempSuma = nuevoTemp(Type.ENTERO);
            emit("+", tempMultiplicacion, indices.get(i), tempSuma, Type.ENTERO);

            acumulado = tempSuma;
        }
        return acumulado;
    }

    private int contarIndicesConsecutivos(List<Suffix> sufijos, int desde) {
        int cantidad = 0;
        while (desde + cantidad < sufijos.size() && sufijos.get(desde + cantidad) instanceof IndexSuffix) {
            cantidad++;
        }
        return cantidad;
    }

    @Override
    public String visit(AssignNode n) {
        String valor = n.valor.accept(this);

        if (!n.operador.equals("=")) {
            String operadorBase = n.operador.substring(0, 1);
            String valorActual = n.destino.accept(this);
            Type tipoOperacion = tipoDe(n.destino);
            String temp = nuevoTemp(tipoOperacion);
            emit(operadorBase, valorActual, valor, temp, tipoOperacion);
            valor = temp;
        }

        escribirEnDestino(n.destino, valor);
        return null;
    }

    private void escribirEnDestino(Expression destino, String valor) {
        if (destino instanceof IdentifierNode id) {
            String lugar = (atributosDeClaseActual.contains(id.nombre) && !localesDelMetodoActual.contains(id.nombre))
                    ? "this->" + id.nombre
                    : id.nombre;
            emit("=", valor, null, lugar, tipoDeLugar(id.nombre));
            return;
        }

        if (destino instanceof PostfixExprNode postfix) {
            String nombreBase = (postfix.base instanceof IdentifierNode idb) ? idb.nombre : null;
            String direccionBase = postfix.base.accept(this);
            List<Suffix> sufijos = postfix.sufijos;

            int i = 0;
            while (i < sufijos.size()) {
                int indicesSeguidos = contarIndicesConsecutivos(sufijos, i);

                if (indicesSeguidos > 0) {
                    List<String> indices = new ArrayList<>();
                    for (int k = 0; k < indicesSeguidos; k++) {
                        indices.add(((IndexSuffix) sufijos.get(i + k)).indice.accept(this));
                    }
                    String indiceFinal = aplanarIndices(nombreBase, indices);

                    if (i + indicesSeguidos >= sufijos.size()) {
                        emit("setindex", direccionBase, indiceFinal, valor, tipoDeLugar(direccionBase));
                        return;
                    }
                    String temp = nuevoTemp(tipoDeLugar(direccionBase));
                    emit("getindex", direccionBase, indiceFinal, temp, tipoDeLugar(direccionBase));
                    direccionBase = temp;
                    nombreBase = null;
                    i += indicesSeguidos;
                    continue;
                }

                Suffix sufijo = sufijos.get(i);
                boolean esUltimo = (i == sufijos.size() - 1);

                if (sufijo instanceof FieldSuffix fs) {
                    if (esUltimo) {
                        emit("setfield", direccionBase, fs.nombreCampo, valor, Type.ERROR);
                        return;
                    }
                    String temp = nuevoTemp(Type.ERROR);
                    emit("getfield", direccionBase, fs.nombreCampo, temp, Type.ERROR);
                    direccionBase = temp;
                } else {
                    throw new IllegalStateException("No se puede asignar al resultado de una llamada");
                }
                i++;
            }
        }
    }

    @Override
    public String visit(IncrDecrNode n) {
        String lugar = (atributosDeClaseActual.contains(n.nombreVariable)
                && !localesDelMetodoActual.contains(n.nombreVariable))
                ? "this->" + n.nombreVariable
                : n.nombreVariable;

        String operador = n.operador.equals("++") ? "+" : "-";
        Type tipo = tipoDeLugar(n.nombreVariable);
        String temp = nuevoTemp(tipo);
        emit(operador, n.nombreVariable, "1", temp, tipo);
        emit("=", temp, null, n.nombreVariable, tipo);
        return null;
    }

    @Override
    public String visit(IfNode n) {
        String etiquetaFin = nuevaEtiqueta();
        for (IfNode.Branch rama : n.ramas) {
            String cond = rama.condicion.accept(this);
            String etiquetaSiguiente = nuevaEtiqueta();
            emit("if_false", cond, null, etiquetaSiguiente, Type.VOID);
            for (Statement s : rama.cuerpo) s.accept(this);
            emit("goto", null, null, etiquetaFin, Type.VOID);
            emitEtiqueta(etiquetaSiguiente);
        }
        if (n.ramaContrario != null) {
            for (Statement s : n.ramaContrario) s.accept(this);
        }
        emitEtiqueta(etiquetaFin);
        return null;
    }

    @Override
    public String visit(SwitchNode n) {
        String selector = n.selector.accept(this);
        String etiquetaFin = nuevaEtiqueta();

        List<String> etiquetas = new ArrayList<>();
        String etiquetaDefault = null;
        for (CaseNode c : n.casos) {
            String e = nuevaEtiqueta();
            etiquetas.add(e);
            if (c.esDefault) etiquetaDefault = e;
        }

        pilaCiclos.push(new EtiquetasCiclo(etiquetaFin, etiquetaFin));

        for (int i = 0; i < n.casos.size(); i++) {
            CaseNode c = n.casos.get(i);
            if (c.esDefault) continue;
            String temp = nuevoTemp(Type.BOOL);
            emit("==", selector, c.valor, temp, Type.BOOL);
            emit("if_true", temp, null, etiquetas.get(i), Type.VOID);
        }
        emit("goto", null, null, etiquetaDefault != null ? etiquetaDefault : etiquetaFin, Type.VOID);

        for (int i = 0; i < n.casos.size(); i++) {
            emitEtiqueta(etiquetas.get(i));
            for (Statement s : n.casos.get(i).cuerpo) s.accept(this);
        }

        emitEtiqueta(etiquetaFin);
        pilaCiclos.pop();
        return null;
    }

    @Override
    public String visit(CaseNode n) {
        for (Statement s : n.cuerpo) s.accept(this);
        return null;
    }

    @Override
    public String visit(WhileNode n) {
        String etiquetaInicio = nuevaEtiqueta();
        String etiquetaFin = nuevaEtiqueta();
        pilaCiclos.push(new EtiquetasCiclo(etiquetaInicio, etiquetaFin));

        emitEtiqueta(etiquetaInicio);
        String cond = n.condicion.accept(this);
        emit("if_false", cond, null, etiquetaFin, Type.VOID);
        for (Statement s : n.cuerpo) s.accept(this);
        emit("goto", null, null, etiquetaInicio, Type.VOID);
        emitEtiqueta(etiquetaFin);

        pilaCiclos.pop();
        return null;
    }

    @Override
    public String visit(DoWhileNode n) {
        String etiquetaInicio = nuevaEtiqueta();
        String etiquetaCond = nuevaEtiqueta();
        String etiquetaFin = nuevaEtiqueta();
        pilaCiclos.push(new EtiquetasCiclo(etiquetaCond, etiquetaFin));

        emitEtiqueta(etiquetaInicio);
        for (Statement s : n.cuerpo) s.accept(this);
        emitEtiqueta(etiquetaCond);
        String cond = n.condicion.accept(this);
        emit("if_true", cond, null, etiquetaInicio, Type.VOID);
        emitEtiqueta(etiquetaFin);

        pilaCiclos.pop();
        return null;
    }

    @Override
    public String visit(ForNode n) {
        if (n.inicializacion != null) n.inicializacion.accept(this);

        String etiquetaInicio = nuevaEtiqueta();
        String etiquetaActualizacion = nuevaEtiqueta();
        String etiquetaFin = nuevaEtiqueta();
        pilaCiclos.push(new EtiquetasCiclo(etiquetaActualizacion, etiquetaFin));

        emitEtiqueta(etiquetaInicio);
        if (n.condicion != null) {
            String cond = n.condicion.accept(this);
            emit("if_false", cond, null, etiquetaFin, Type.VOID);
        }
        for (Statement s : n.cuerpo) s.accept(this);
        emitEtiqueta(etiquetaActualizacion);
        if (n.actualizacion != null) n.actualizacion.accept(this);
        emit("goto", null, null, etiquetaInicio, Type.VOID);
        emitEtiqueta(etiquetaFin);

        pilaCiclos.pop();
        return null;
    }

    @Override
    public String visit(BreakNode n) {
        emit("goto", null, null, pilaCiclos.peek().romper, Type.VOID);
        return null;
    }

    @Override
    public String visit(ContinueNode n) {
        emit("goto", null, null, pilaCiclos.peek().continuar, Type.VOID);
        return null;
    }

    @Override
    public String visit(ReturnNode n) {
        String valor = n.valor == null ? null : n.valor.accept(this);
        emit("return", valor, null, null, n.valor == null ? Type.VOID : tipoDe(n.valor));
        return null;
    }

    @Override
    public String visit(PrintNode n) {
        List<String> direcciones = new ArrayList<>();
        List<Type> tipos = new ArrayList<>();
        for (Expression arg : n.argumentos) {
            String lugar = arg.accept(this);
            direcciones.add(lugar);
            Type t = tipoDe(arg);
            if (t == Type.ERROR) t = tipoDeLugar(lugar);
            tipos.add(t);
        }
        emitVariadica("print", null, null, Type.VOID, direcciones, tipos);
        return null;
    }

    @Override
    public String visit(ReadNode n) {
        Type tipo = n.tipoResuelto == Type.ERROR ? Type.CADENA : n.tipoResuelto;
        String temp = nuevoTemp(tipo);
        emit("read", null, null, temp, tipo);
        if (n.variableDestino != null) {
            emit("=", temp, null, n.variableDestino, tipoDeLugar(n.variableDestino));
            return n.variableDestino;
        }
        return temp;
    }

    @Override
    public String visit(ExprStatementNode n) {
        n.expresion.accept(this);
        return null;
    }

    @Override
    public String visit(BinaryExprNode n) {
        if (n.operador.equals("&&") || n.operador.equals("||")) {
            return generarCortoCircuito(n);
        }
        String izq = n.izquierda.accept(this);
        String der = n.derecha.accept(this);
        Type tipo = n.tipoResuelto;
        String temp = nuevoTemp(tipo);
        emit(n.operador, izq, der, temp, tipo);
        return temp;
    }

    private String generarCortoCircuito(BinaryExprNode n) {
        String resultado = nuevoTemp(Type.BOOL);
        String etiquetaCorto = nuevaEtiqueta();
        String etiquetaFin = nuevaEtiqueta();
        boolean esAnd = n.operador.equals("&&");

        String izq = n.izquierda.accept(this);
        emit(esAnd ? "if_false" : "if_true", izq, null, etiquetaCorto, Type.VOID);
        String der = n.derecha.accept(this);
        emit(esAnd ? "if_false" : "if_true", der, null, etiquetaCorto, Type.VOID);

        emit("=", esAnd ? "1" : "0", null, resultado, Type.BOOL);
        emit("goto", null, null, etiquetaFin, Type.VOID);
        emitEtiqueta(etiquetaCorto);
        emit("=", esAnd ? "0" : "1", null, resultado, Type.BOOL);
        emitEtiqueta(etiquetaFin);

        return resultado;
    }

    @Override
    public String visit(UnaryExprNode n) {
        String operando = n.operando.accept(this);
        Type tipo = n.tipoResuelto;
        String temp = nuevoTemp(tipo);
        emit(n.operador, operando, null, temp, tipo);
        return temp;
    }

    @Override
    public String visit(TernaryExprNode n) {
        Type tipo = n.tipoResuelto;
        String resultado = nuevoTemp(tipo);
        String etiquetaFalso = nuevaEtiqueta();
        String etiquetaFin = nuevaEtiqueta();

        String cond = n.condicion.accept(this);
        emit("if_false", cond, null, etiquetaFalso, Type.VOID);
        String valorV = n.siVerdadero.accept(this);
        emit("=", valorV, null, resultado, tipo);
        emit("goto", null, null, etiquetaFin, Type.VOID);
        emitEtiqueta(etiquetaFalso);
        String valorF = n.siFalso.accept(this);
        emit("=", valorF, null, resultado, tipo);
        emitEtiqueta(etiquetaFin);

        return resultado;
    }

    @Override
    public String visit(LiteralNode n) {
        String v = n.valorCrudo;
        if (v.equals("verdadero") || v.equals("true") || v.equals("verum")) return "1";
        if (v.equals("falso") || v.equals("false") || v.equals("falsus")) return "0";
        return v;
    }

    @Override
    public String visit(IdentifierNode n) {
        if (atributosDeClaseActual.contains(n.nombre) && !localesDelMetodoActual.contains(n.nombre)) {
            return "this->" + n.nombre;
        }
        return n.nombre;
    }

    @Override
    public String visit(PostfixExprNode n) {
        String nombreBase = (n.base instanceof IdentifierNode idb) ? idb.nombre : null;
        String direccion = n.base.accept(this);

        int i = 0;
        while (i < n.sufijos.size()) {
            int indicesSeguidos = contarIndicesConsecutivos(n.sufijos, i);

            if (indicesSeguidos > 0) {
                List<String> indices = new ArrayList<>();
                for (int k = 0; k < indicesSeguidos; k++) {
                    indices.add(((IndexSuffix) n.sufijos.get(i + k)).indice.accept(this));
                }
                String indiceFinal = aplanarIndices(nombreBase, indices);
                Type tipoElemento = tipoDeLugar(direccion);
                String temp = nuevoTemp(tipoElemento);
                emit("getindex", direccion, indiceFinal, temp, tipoElemento);
                direccion = temp;
                nombreBase = null;
                i += indicesSeguidos;
                continue;
            }

            Suffix s = n.sufijos.get(i);

            // 1. PATRÓN MÉTODO: FieldSuffix + CallSuffix (ej. miObjeto.calcularAnioNacimiento(anioActual))
            if (s instanceof FieldSuffix fs && (i + 1 < n.sufijos.size()) && (n.sufijos.get(i + 1) instanceof CallSuffix cs)) {
                i += 2; // Consumimos ambos sufijos juntos

                String nombreMetodo = fs.nombreCampo;
                String nombreClase = tiposUsuarioDeLugares.get(direccion);
                String nombreFuncionLlamada = (nombreClase != null) ? nombreClase + "_" + nombreMetodo : nombreMetodo;

                List<String> args = new ArrayList<>();
                List<Type> tipos = new ArrayList<>();

                // Si es un objeto de clase, agregamos 'this' (direccion) como primer argumento
                if (nombreClase != null) {
                    args.add(direccion);
                    tipos.add(Type.CLASE);
                }

                for (Expression arg : cs.argumentos) {
                    String lugar = arg.accept(this);
                    args.add(lugar);
                    tipos.add(tipoDe(arg));
                }

                Type tipoRetorno = n.tipoResuelto != Type.ERROR ? n.tipoResuelto : Type.ENTERO;
                String temp = nuevoTemp(tipoRetorno);

                emitVariadica("call", nombreFuncionLlamada, temp, tipoRetorno, args, tipos);
                direccion = temp;
                continue;
            }

            // 2. CAMPO NORMAL (ej. p1.x)
            if (s instanceof FieldSuffix fs) {
                Type tipoCampo = n.tipoResuelto != Type.ERROR ? n.tipoResuelto : Type.ENTERO;
                String temp = nuevoTemp(tipoCampo);
                emit("getfield", direccion, fs.nombreCampo, temp, tipoCampo);
                direccion = temp;
                i++;
                continue;
            }

            // 3. LLAMADA A FUNCIÓN LIBRE (ej. funcionLibre(a, b))
            if (s instanceof CallSuffix cs) {
                List<String> args = new ArrayList<>();
                List<Type> tipos = new ArrayList<>();

                for (Expression arg : cs.argumentos) {
                    String lugar = arg.accept(this);
                    args.add(lugar);
                    tipos.add(tipoDe(arg));
                }

                Type tipoRetorno = n.tipoResuelto != Type.ERROR ? n.tipoResuelto : Type.ENTERO;
                String temp = nuevoTemp(tipoRetorno);

                emitVariadica("call", direccion, temp, tipoRetorno, args, tipos);
                direccion = temp;
                i++;
                continue;
            }

            i++;
        }
        return direccion;
    }

    @Override
    public String visit(NewObjectNode n) {
        List<String> args = new ArrayList<>();
        List<Type> tipos = new ArrayList<>();

        Type tipo = Type.CLASE;
        String temp = nuevoTemp(tipo);
        tiposUsuarioDeLugares.put(temp, n.nombreClase);

        args.add(temp);
        tipos.add(Type.CLASE);

        for (Expression arg : n.argumentos) {
            String lugar = arg.accept(this);
            args.add(lugar);
            tipos.add(tipoDe(arg));
        }

        emitVariadica("new", n.nombreClase, temp, tipo, args, tipos);

        String initFunc = n.nombreClase + "_init";
        String tempCall = nuevoTemp(Type.VOID);
        emitVariadica("call", initFunc, tempCall, Type.VOID, args, tipos);

        return temp;
    }

    @Override
    public String visit(ArrayLiteralNode n) {
        Type tipo = n.tipoResuelto;
        String temp = nuevoTemp(tipo);
        emit("newarray", String.valueOf(n.valores.size()), null, temp, tipo);
        for (int i = 0; i < n.valores.size(); i++) {
            String valor = n.valores.get(i).accept(this);
            emit("setindex", temp, String.valueOf(i), valor, tipo);
        }
        return temp;
    }
}