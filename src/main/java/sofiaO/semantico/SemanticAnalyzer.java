package sofiaO.semantico;

import sofiaO.ast.ASTNode;
import sofiaO.ast.ASTVisitor;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.sentencia.*;
import sofiaO.ast.expresion.*;
import sofiaO.util.Type;
import sofiaO.util.TypeMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analizador semántico único, compartido por Y?, Zetariano y Pig Latin.
 */
public class SemanticAnalyzer implements ASTVisitor<Type> {

    private SymbolTable scopeActual;
    private final ProgramContext contexto;
    private ResolvedType tipoRetornoFuncionActual = null;
    private int profundidadCiclo = 0;
    private String claseActual = null;

    private final List<SemanticException> errores = new ArrayList<>();
    private static final Pattern LINEA_EN_MENSAJE = Pattern.compile("\\(línea (\\d+)\\)");
    private static final Pattern TIPO_ERROR = Pattern.compile("(?<![A-Za-z])ERROR(?![A-Za-z])");

    public List<SemanticException> getErrores() { return errores; }

    private void analizarAislado(ASTNode nodo) {
        SymbolTable scopePrevio = scopeActual;
        int cicloPrevio = profundidadCiclo;
        ResolvedType retornoPrevio = tipoRetornoFuncionActual;
        try {
            nodo.accept(this);
        } catch (SemanticException e) {
            scopeActual = scopePrevio;
            profundidadCiclo = cicloPrevio;
            tipoRetornoFuncionActual = retornoPrevio;

            // Un tipo ERROR en el mensaje es consecuencia de un fallo anterior: no se repite.
            if (!TIPO_ERROR.matcher(e.getMessage()).find()) {
                if (!e.tieneUbicacion()) {
                    int linea = nodo.getLine();
                    int columna = nodo.getColumn();
                    Matcher m = LINEA_EN_MENSAJE.matcher(e.getMessage());
                    if (m.find()) {
                        int lineaMensaje = Integer.parseInt(m.group(1));
                        if (lineaMensaje != linea) { linea = lineaMensaje; columna = 0; }
                    }
                    e.setUbicacion(linea, columna);
                }
                errores.add(e);
            }
            if (nodo instanceof VarDeclNode v) declararTrasError(v);
        }
    }

    /** Si una declaración falla, se registra igual para no provocar errores en cascada. */
    private void declararTrasError(VarDeclNode v) {
        if (scopeActual.existeEnScopeActual(v.nombre)) return;
        try {
            ResolvedType rt = resolverTipoDeclarado(v.tipo, v.getLine());
            scopeActual.declarar(v.nombre, rt.tipo(), rt.tipoUsuario(), v.getLine(), v.getColumn());
        } catch (SemanticException ignorada) {
            scopeActual.declarar(v.nombre, Type.ERROR, null, v.getLine(), v.getColumn());
        }
    }

    public SemanticAnalyzer(SymbolTable scopeGlobal, ProgramContext contexto) {
        this.scopeActual = scopeGlobal;
        this.contexto = contexto;
    }

    public SemanticAnalyzer(SymbolTable scopeGlobal) {
        this(scopeGlobal, new ProgramContext());
    }

    public ProgramContext getContexto() { return contexto; }

    private void abrirScope() { scopeActual = new SymbolTable(scopeActual); }
    private void cerrarScope() { scopeActual = scopeActual.getScopePadre(); }

    private void visitarCuerpo(List<Statement> cuerpo) {
        abrirScope();
        for (Statement s : cuerpo) s.accept(this);
        cerrarScope();
    }

    private ResolvedType resolverTipoDeclarado(String texto, int linea) {
        if (texto == null || texto.equalsIgnoreCase("void")) {
            return ResolvedType.primitivo(Type.VOID);
        }
        Type primitivo = TypeMapper.deTexto(texto);
        if (primitivo != Type.ERROR) return ResolvedType.primitivo(primitivo);

        if (contexto.structs.containsKey(texto)) return new ResolvedType(Type.ESTRUCTURA, texto);
        if (contexto.classes.containsKey(texto)) return new ResolvedType(Type.CLASE, texto);

        throw new SemanticException("Tipo desconocido: '" + texto + "' (línea " + linea + "). "
                + "¿Falta importar el archivo .y/.z que lo define?");
    }

    private Map<String, ResolvedType> obtenerLayout(Type tipo, String tipoUsuario) {
        if (tipo == Type.ESTRUCTURA) return contexto.layoutsEstructuras.get(tipoUsuario);
        if (tipo == Type.CLASE) return contexto.layoutsClases.get(tipoUsuario);
        return null;
    }

    private String tipoUsuarioDe(ASTNode nodo) {
        return (nodo instanceof sofiaO.ast.BaseNode b) ? b.tipoUsuarioResuelto : null;
    }

    private String etiquetaTipo(Type tipo, String tipoUsuario) {
        return tipoUsuario != null ? tipoUsuario : tipo.toString();
    }

    private void precargarAtributosDeClase(String nombreClase) {
        Map<String, ResolvedType> layout = contexto.layoutsClases.get(nombreClase);
        if (layout == null) return;
        for (var entrada : layout.entrySet()) {
            ResolvedType rt = entrada.getValue();
            scopeActual.declarar(entrada.getKey(), rt.tipo(), rt.tipoUsuario(), 0, 0);
        }
    }

    private void validarParametros(List<ParamNode> parametros, List<Expression> argumentos, String nombreCallable, int linea) {
        if (parametros.size() != argumentos.size()) {
            throw new SemanticException("La función/método '" + nombreCallable + "' espera "
                    + parametros.size() + " argumento(s), pero se recibieron " + argumentos.size()
                    + " (línea " + linea + ")");
        }
        for (int i = 0; i < parametros.size(); i++) {
            ParamNode p = parametros.get(i);
            Expression arg = argumentos.get(i);
            Type tipoArg = arg.accept(this);
            String tipoUsuarioArg = tipoUsuarioDe(arg);

            ResolvedType rtParam = resolverTipoDeclarado(p.tipo, linea);
            if (!TypeCoercionTable.compatibles(rtParam.tipo(), rtParam.tipoUsuario(), tipoArg, tipoUsuarioArg)) {
                throw new SemanticException("Tipo incompatible para el parámetro '" + p.nombre
                        + "' en '" + nombreCallable + "': se esperaba " + rtParam.etiqueta()
                        + " pero se obtuvo " + etiquetaTipo(tipoArg, tipoUsuarioArg)
                        + " (línea " + linea + ")");
            }
        }
    }

    // ================= DECLARACIONES =================

    @Override
    public Type visit(ProgramNode n) {
        for (var d : n.declaraciones) {
            if (d instanceof FunctionDeclNode f && !f.esMetodo) {
                contexto.funciones.putIfAbsent(f.nombre, f);
            }
        }

        for (var imp : n.imports) imp.accept(this);

        for (var d : n.declaraciones) {
            if (d instanceof StructDeclNode || d instanceof ClassDeclNode) d.accept(this);
        }
        for (var d : n.declaraciones) {
            if (d instanceof VarDeclNode) d.accept(this);
        }
        for (var d : n.declaraciones) {
            if (d instanceof FunctionDeclNode) d.accept(this);
        }
        return Type.VOID;
    }

    /*@Override
    public Type visit(ImportNode n) {
        String[] partes = n.ruta.split("\\.");
        if (partes.length < 2) return Type.VOID;

        String posibleNombreTipo = partes[partes.length - 2];
        boolean existe = (n.tipo == ImportNode.TipoImport.CLASE_Z)
                ? contexto.classes.containsKey(posibleNombreTipo)
                : (contexto.structs.containsKey(posibleNombreTipo) || contexto.funciones.containsKey(posibleNombreTipo));

        if (!existe) {
            throw new SemanticException("No se encontró nada registrado para el import '" + n.ruta
                    + "' (se esperaba encontrar '" + posibleNombreTipo + "'). "
                    + "Verifica que ese archivo se haya procesado antes (línea " + n.getLine() + ")");
        }
        return Type.VOID;
    }*/

    @Override
    public Type visit(ImportNode n) {
        String[] partes = n.ruta.split("\\.");
        if (partes.length < 2) return Type.VOID;

        if (n.tipo != ImportNode.TipoImport.CLASE_Z) return Type.VOID;

        String posibleNombreTipo = partes[partes.length - 2];
        boolean existe = contexto.classes.containsKey(posibleNombreTipo);

        if (!existe) {
            throw new SemanticException("No se encontró nada registrado para el import '" + n.ruta
                    + "' (se esperaba encontrar '" + posibleNombreTipo + "'). "
                    + "Verifica que ese archivo se haya procesado antes (línea " + n.getLine() + ")");
        }
        return Type.VOID;
    }

    @Override
    public Type visit(StructDeclNode n) {
        contexto.structs.put(n.nombre, n);

        Map<String, ResolvedType> layout = new LinkedHashMap<>();
        Map<String, List<Integer>> dimensiones = new LinkedHashMap<>();
        for (FieldNode campo : n.campos) {
            campo.accept(this);
            layout.put(campo.nombre, new ResolvedType(campo.tipoResuelto, campo.tipoUsuarioResuelto));
            if (!campo.dimensionesArreglo.isEmpty()) dimensiones.put(campo.nombre, campo.dimensionesArreglo);
        }
        contexto.layoutsEstructuras.put(n.nombre, layout);
        if (!dimensiones.isEmpty()) contexto.dimensionesCamposEstructuras.put(n.nombre, dimensiones);
        return Type.VOID;
    }

    @Override
    public Type visit(ClassDeclNode n) {
        contexto.classes.put(n.nombre, n);

        Map<String, ResolvedType> layout = new LinkedHashMap<>();
        Map<String, List<Integer>> dimensiones = new LinkedHashMap<>();
        for (FieldNode atributo : n.atributos) {
            atributo.accept(this);
            layout.put(atributo.nombre, new ResolvedType(atributo.tipoResuelto, atributo.tipoUsuarioResuelto));
            if (!atributo.dimensionesArreglo.isEmpty()) dimensiones.put(atributo.nombre, atributo.dimensionesArreglo);
        }
        contexto.layoutsClases.put(n.nombre, layout);
        if (!dimensiones.isEmpty()) contexto.dimensionesCamposClases.put(n.nombre, dimensiones);

        Map<String, FunctionDeclNode> metodos = new LinkedHashMap<>();
        for (FunctionDeclNode m : n.metodos) metodos.put(m.nombre, m);
        contexto.metodosPorClase.put(n.nombre, metodos);
        contexto.constructoresPorClase.put(n.nombre, n.constructores);

        for (var ctor : n.constructores) ctor.accept(this);
        for (var m : n.metodos) m.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visit(FunctionDeclNode n) {
        ResolvedType retornoAnterior = tipoRetornoFuncionActual;
        String claseAnterior = claseActual;
        tipoRetornoFuncionActual = resolverTipoDeclarado(n.tipoRetorno, n.getLine());

        abrirScope();
        if (n.esMetodo && n.claseDuena != null) {
            claseActual = n.claseDuena;
            scopeActual.declarar("this", Type.CLASE, n.claseDuena, n.getLine(), n.getColumn());
            precargarAtributosDeClase(n.claseDuena);
        }
        for (ParamNode p : n.parametros) p.accept(this);
        for (Statement s : n.cuerpo) s.accept(this);
        cerrarScope();

        tipoRetornoFuncionActual = retornoAnterior;
        claseActual = claseAnterior;
        return Type.VOID;
    }

    @Override
    public Type visit(ConstructorDeclNode n) {
        String claseAnterior = claseActual;
        claseActual = n.claseDuena;

        abrirScope();
        scopeActual.declarar("this", Type.CLASE, n.claseDuena, n.getLine(), n.getColumn());
        precargarAtributosDeClase(n.claseDuena);
        for (ParamNode p : n.parametros) p.accept(this);
        for (Statement s : n.cuerpo) s.accept(this);
        cerrarScope();

        claseActual = claseAnterior;
        return Type.VOID;
    }

    @Override
    public Type visit(FieldNode n) {
        ResolvedType rt = resolverTipoDeclarado(n.tipo, n.getLine());
        n.tipoResuelto = rt.tipo();
        n.tipoUsuarioResuelto = rt.tipoUsuario();
        return rt.tipo();
    }

    @Override
    public Type visit(ParamNode n) {
        ResolvedType rt = resolverTipoDeclarado(n.tipo, n.getLine());
        n.tipoResuelto = rt.tipo();
        n.tipoUsuarioResuelto = rt.tipoUsuario();
        scopeActual.declarar(n.nombre, rt.tipo(), rt.tipoUsuario(), n.getLine(), n.getColumn());
        return rt.tipo();
    }

    @Override
    public Type visit(VarDeclNode n) {
        // 1. Resolver el tipo declarado de la variable (p. ej. "Punto" -> ESTRUCTURA)
        ResolvedType rt = resolverTipoDeclarado(n.tipo, n.getLine());
        n.tipoResuelto = rt.tipo();
        n.tipoUsuarioResuelto = rt.tipoUsuario();

        // 2. Validar que la variable no esté duplicada en el ámbito actual
        if (scopeActual.existeEnScopeActual(n.nombre)) {
            throw new SemanticException("Variable ya declarada en este ámbito: " + n.nombre
                    + " (línea " + n.getLine() + ")");
        }

        // 3. Evaluar el inicializador si existe
        if (n.inicializador != null) {
            ASTNode initNode = (ASTNode) n.inicializador;

            // Para inicializaciones de estructuras con sintaxis de llaves {10, 20}
            if (rt.tipo() == Type.ESTRUCTURA && initNode instanceof ArrayLiteralNode litStruct) {
                litStruct.tipoUsuarioResuelto = n.tipo;
                litStruct.tipoResuelto = Type.ESTRUCTURA;
            }

            Type tipoInit = initNode.accept(this);
            String tipoUsuarioInit = tipoUsuarioDe(initNode);

            // Si es una estructura asignada con llaves, forzamos la coincidencia de tipo de usuario
            if (rt.tipo() == Type.ESTRUCTURA && (initNode instanceof ArrayLiteralNode || tipoInit == Type.ESTRUCTURA)) {
                tipoInit = Type.ESTRUCTURA;
                if (tipoUsuarioInit == null) {
                    tipoUsuarioInit = n.tipo;
                }
            }

            // Validar compatibilidad entre el tipo declarado y la expresión asignada
            if (!TypeCoercionTable.compatibles(rt.tipo(), rt.tipoUsuario(), tipoInit, tipoUsuarioInit)) {
                throw new SemanticException("No se puede asignar " + etiquetaTipo(tipoInit, tipoUsuarioInit)
                        + " a " + rt.etiqueta() + " en la variable '" + n.nombre
                        + "' (línea " + n.getLine() + ")");
            }
        }

        // 4. Registrar la variable en la tabla de símbolos
        scopeActual.declarar(n.nombre, rt.tipo(), rt.tipoUsuario(), n.getLine(), n.getColumn());
        return Type.VOID;
    }

    // ================= SENTENCIAS =================

    @Override
    public Type visit(AssignNode n) {
        Type tipoDestino = n.destino.accept(this);
        String tipoUsuarioDestino = tipoUsuarioDe(n.destino);
        Type tipoValor = n.valor.accept(this);
        String tipoUsuarioValor = tipoUsuarioDe(n.valor);

        if (!n.operador.equals("=")) {
            Type tipoOperacion = n.operador.startsWith("+")
                    ? TypeCoercionTable.resultadoSuma(tipoDestino, tipoValor)
                    : TypeCoercionTable.resultadoAritmetico(tipoDestino, tipoValor);
            if (tipoOperacion == Type.ERROR) {
                throw new SemanticException("Operación inválida: " + tipoDestino + " " + n.operador
                        + " " + tipoValor + " (línea " + n.getLine() + ")");
            }
            tipoValor = tipoOperacion;
            tipoUsuarioValor = null;
        }

        if (!TypeCoercionTable.compatibles(tipoDestino, tipoUsuarioDestino, tipoValor, tipoUsuarioValor)) {
            throw new SemanticException("Asignación incompatible: " + etiquetaTipo(tipoValor, tipoUsuarioValor)
                    + " a " + etiquetaTipo(tipoDestino, tipoUsuarioDestino) + " (línea " + n.getLine() + ")");
        }
        return Type.VOID;
    }

    @Override
    public Type visit(IncrDecrNode n) {
        Symbol s = scopeActual.resolver(n.nombreVariable);
        if (s == null) throw new SemanticException("Variable no declarada: " + n.nombreVariable
                + " (línea " + n.getLine() + ")");
        if (s.tipo != Type.ENTERO && s.tipo != Type.FLOTANTE) {
            throw new SemanticException("'" + n.operador + "' requiere un tipo numérico, no " + s.tipo
                    + " (línea " + n.getLine() + ")");
        }
        return Type.VOID;
    }

    @Override
    public Type visit(IfNode n) {
        for (IfNode.Branch rama : n.ramas) {
            Type tipoCond = rama.condicion.accept(this);
            if (tipoCond != Type.BOOL) {
                throw new SemanticException("La condición del 'si' debe ser booleana, no " + tipoCond
                        + " (línea " + n.getLine() + ")");
            }
            visitarCuerpo(rama.cuerpo);
        }
        if (n.ramaContrario != null) visitarCuerpo(n.ramaContrario);
        return Type.VOID;
    }

    @Override
    public Type visit(SwitchNode n) {
        n.selector.accept(this);
        for (CaseNode c : n.casos) c.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visit(CaseNode n) {
        visitarCuerpo(n.cuerpo);
        return Type.VOID;
    }

    @Override
    public Type visit(WhileNode n) {
        Type tipoCond = n.condicion.accept(this);
        if (tipoCond != Type.BOOL) {
            throw new SemanticException("La condición del ciclo debe ser booleana, no " + tipoCond
                    + " (línea " + n.getLine() + ")");
        }
        profundidadCiclo++;
        visitarCuerpo(n.cuerpo);
        profundidadCiclo--;
        return Type.VOID;
    }

    @Override
    public Type visit(DoWhileNode n) {
        profundidadCiclo++;
        visitarCuerpo(n.cuerpo);
        profundidadCiclo--;
        Type tipoCond = n.condicion.accept(this);
        if (tipoCond != Type.BOOL) {
            throw new SemanticException("La condición del ciclo debe ser booleana, no " + tipoCond
                    + " (línea " + n.getLine() + ")");
        }
        return Type.VOID;
    }

    @Override
    public Type visit(ForNode n) {
        abrirScope();
        if (n.inicializacion != null) n.inicializacion.accept(this);
        if (n.condicion != null) {
            Type tipoCond = n.condicion.accept(this);
            if (tipoCond != Type.BOOL) {
                throw new SemanticException("La condición del 'para/for' debe ser booleana, no " + tipoCond
                        + " (línea " + n.getLine() + ")");
            }
        }
        if (n.actualizacion != null) n.actualizacion.accept(this);

        profundidadCiclo++;
        for (Statement s : n.cuerpo) s.accept(this);
        profundidadCiclo--;

        cerrarScope();
        return Type.VOID;
    }

    @Override
    public Type visit(BreakNode n) {
        if (profundidadCiclo == 0) {
            throw new SemanticException("'romper/break/interrumpe' fuera de un ciclo (línea " + n.getLine() + ")");
        }
        return Type.VOID;
    }

    @Override
    public Type visit(ContinueNode n) {
        if (profundidadCiclo == 0) {
            throw new SemanticException("'continuar/continue/perge' fuera de un ciclo (línea " + n.getLine() + ")");
        }
        return Type.VOID;
    }

    @Override
    public Type visit(ReturnNode n) {
        Type tipoValor = n.valor == null ? Type.VOID : n.valor.accept(this);
        String tipoUsuarioValor = n.valor == null ? null : tipoUsuarioDe(n.valor);

        if (tipoRetornoFuncionActual != null) {
            if (!TypeCoercionTable.compatibles(tipoRetornoFuncionActual.tipo(), tipoRetornoFuncionActual.tipoUsuario(), tipoValor, tipoUsuarioValor)) {
                throw new SemanticException("La función debe retornar " + tipoRetornoFuncionActual.etiqueta()
                        + " pero se retornó " + etiquetaTipo(tipoValor, tipoUsuarioValor) + " (línea " + n.getLine() + ")");
            }
        }
        return Type.VOID;
    }

    @Override
    public Type visit(PrintNode n) {
        for (var arg : n.argumentos) arg.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visit(ReadNode n) {
        Type tipo;
        if (n.variableDestino != null) {
            Symbol s = scopeActual.resolver(n.variableDestino);
            if (s == null) throw new SemanticException("Variable no declarada: " + n.variableDestino
                    + " (línea " + n.getLine() + ")");
            tipo = s.tipo;
        } else {
            tipo = Type.CADENA;
        }
        n.tipoResuelto = tipo;
        return tipo;
    }

    @Override
    public Type visit(ExprStatementNode n) {
        n.expresion.accept(this);
        return Type.VOID;
    }

    // ================= EXPRESIONES =================

    @Override
    public Type visit(BinaryExprNode n) {
        Type izq = n.izquierda.accept(this);
        Type der = n.derecha.accept(this);
        Type resultado;

        switch (n.operador) {
            case "&&": case "||":
                if (izq != Type.BOOL || der != Type.BOOL) {
                    throw new SemanticException("'" + n.operador + "' requiere operandos booleanos (línea "
                            + n.getLine() + ")");
                }
                resultado = Type.BOOL;
                break;

            case "==": case "!=": {
                boolean izqEsObjeto = izq == Type.ESTRUCTURA || izq == Type.CLASE;
                boolean derEsObjeto = der == Type.ESTRUCTURA || der == Type.CLASE;
                boolean izqEsNulo = izq == Type.NULO;
                boolean derEsNulo = der == Type.NULO;

                if (izqEsObjeto || derEsObjeto || izqEsNulo || derEsNulo) {
                    String tuIzq = tipoUsuarioDe(n.izquierda);
                    String tuDer = tipoUsuarioDe(n.derecha);
                    boolean valido = (izqEsNulo && derEsObjeto) || (derEsNulo && izqEsObjeto)
                            || (izqEsObjeto && derEsObjeto && izq == der && Objects.equals(tuIzq, tuDer));
                    if (!valido) {
                        throw new SemanticException("No se puede comparar " + etiquetaTipo(izq, tuIzq)
                                + " con " + etiquetaTipo(der, tuDer) + " (línea " + n.getLine() + ")");
                    }
                } else if (TypeCoercionTable.resultado(izq, der) == Type.ERROR) {
                    throw new SemanticException("No se puede comparar " + izq + " con " + der
                            + " (línea " + n.getLine() + ")");
                }
                resultado = Type.BOOL;
                break;
            }

            case "<": case ">": case "<=": case ">=":
                if (TypeCoercionTable.resultado(izq, der) == Type.ERROR) {
                    throw new SemanticException("No se puede comparar " + izq + " con " + der
                            + " (línea " + n.getLine() + ")");
                }
                resultado = Type.BOOL;
                break;

            case "+": // suma numérica o concatenación, según los tipos
                resultado = TypeCoercionTable.resultadoSuma(izq, der);
                if (resultado == Type.ERROR) {
                    throw new SemanticException("Operación inválida: " + izq + " + " + der
                            + " (línea " + n.getLine() + ")");
                }
                break;

            default: // - * / %
                resultado = TypeCoercionTable.resultadoAritmetico(izq, der);
                if (resultado == Type.ERROR) {
                    throw new SemanticException("Operación inválida: " + izq + " " + n.operador + " " + der
                            + " (línea " + n.getLine() + ")");
                }
        }

        n.tipoResuelto = resultado;
        return resultado;
    }

    @Override
    public Type visit(UnaryExprNode n) {
        Type tipoOperando = n.operando.accept(this);
        Type resultado;
        if (n.operador.equals("!")) {
            if (tipoOperando != Type.BOOL) throw new SemanticException("'!' requiere un booleano, no " + tipoOperando
                    + " (línea " + n.getLine() + ")");
            resultado = Type.BOOL;
        } else {
            if (tipoOperando != Type.ENTERO && tipoOperando != Type.FLOTANTE) {
                throw new SemanticException("'-' unario requiere un tipo numérico, no " + tipoOperando
                        + " (línea " + n.getLine() + ")");
            }
            resultado = tipoOperando;
        }
        n.tipoResuelto = resultado;
        return resultado;
    }

    @Override
    public Type visit(TernaryExprNode n) {
        Type tipoCond = n.condicion.accept(this);
        if (tipoCond != Type.BOOL) throw new SemanticException("La condición del ternario debe ser booleana"
                + " (línea " + n.getLine() + ")");
        Type tipoV = n.siVerdadero.accept(this);
        Type tipoF = n.siFalso.accept(this);
        Type resultado = TypeCoercionTable.resultado(tipoV, tipoF);
        if (resultado == Type.ERROR) {
            throw new SemanticException("Las dos ramas del ternario deben ser compatibles: "
                    + tipoV + " vs " + tipoF + " (línea " + n.getLine() + ")");
        }
        n.tipoResuelto = resultado;
        return resultado;
    }

    @Override
    public Type visit(LiteralNode n) {
        String v = n.valorCrudo;
        Type resultado;
        if (v.matches("\\d+")) resultado = Type.ENTERO;
        else if (v.matches("\\d+\\.\\d+")) resultado = Type.FLOTANTE;
        else if (v.startsWith("\"")) resultado = Type.CADENA;
        else if (v.startsWith("'")) resultado = Type.CARACTER;
        else if (v.equals("verdadero") || v.equals("true") || v.equals("verum")
                || v.equals("falso") || v.equals("false") || v.equals("falsus")) resultado = Type.BOOL;
        else if (v.equals("null")) resultado = Type.NULO;
        else resultado = Type.ERROR;

        n.tipoResuelto = resultado;
        return resultado;
    }

    @Override
    public Type visit(IdentifierNode n) {
        Symbol s = scopeActual.resolver(n.nombre);
        if (s == null) throw new SemanticException("Variable no declarada: " + n.nombre
                + " (línea " + n.getLine() + ")");
        n.tipoResuelto = s.tipo;
        n.tipoUsuarioResuelto = s.tipoUsuario;
        return s.tipo;
    }

    @Override
    public Type visit(PostfixExprNode n) {
        boolean esLlamadaAFuncionLibre = n.base instanceof IdentifierNode idBase
                && !n.sufijos.isEmpty() && n.sufijos.get(0) instanceof CallSuffix
                && contexto.funciones.containsKey(idBase.nombre);

        boolean esLlamadaAMetodoImplicito = !esLlamadaAFuncionLibre
                && n.base instanceof IdentifierNode idBase2
                && !n.sufijos.isEmpty() && n.sufijos.get(0) instanceof CallSuffix
                && claseActual != null
                && contexto.metodosPorClase.containsKey(claseActual)
                && contexto.metodosPorClase.get(claseActual).containsKey(idBase2.nombre);

        Type tipoActual;
        String tipoUsuarioActual;
        if (esLlamadaAFuncionLibre) {
            tipoActual = Type.ERROR;
            tipoUsuarioActual = null;
        } else if (esLlamadaAMetodoImplicito) {
            tipoActual = Type.CLASE;
            tipoUsuarioActual = claseActual;
        } else {
            tipoActual = n.base.accept(this);
            tipoUsuarioActual = tipoUsuarioDe(n.base);
        }

        List<Suffix> sufijos = n.sufijos;
        for (int i = 0; i < sufijos.size(); i++) {
            Suffix s = sufijos.get(i);

            if (s instanceof FieldSuffix fs) {
                boolean esNombreDeMetodo = (i + 1 < sufijos.size()) && (sufijos.get(i + 1) instanceof CallSuffix);
                if (esNombreDeMetodo) {
                    continue;
                }

                Map<String, ResolvedType> layout = obtenerLayout(tipoActual, tipoUsuarioActual);
                if (layout == null) {
                    throw new SemanticException("No se puede acceder a '." + fs.nombreCampo
                            + "' porque '" + etiquetaTipo(tipoActual, tipoUsuarioActual)
                            + "' no es una estructura ni una clase conocida (línea " + n.getLine() + ")");
                }
                ResolvedType campo = layout.get(fs.nombreCampo);
                if (campo == null) {
                    throw new SemanticException("'" + tipoUsuarioActual + "' no tiene un campo llamado '"
                            + fs.nombreCampo + "' (línea " + n.getLine() + ")");
                }
                tipoActual = campo.tipo();
                tipoUsuarioActual = campo.tipoUsuario();

            } else if (s instanceof IndexSuffix idx) {
                Type tipoIndice = idx.indice.accept(this);
                if (tipoIndice != Type.ENTERO) {
                    throw new SemanticException("El índice de un arreglo debe ser entero, no " + tipoIndice
                            + " (línea " + n.getLine() + ")");
                }

            } else if (s instanceof CallSuffix call) {
                FieldSuffix nombreMetodo = (i > 0 && sufijos.get(i - 1) instanceof FieldSuffix fsPrevio)
                        ? fsPrevio : null;

                if (nombreMetodo != null && tipoUsuarioActual != null
                        && contexto.metodosPorClase.containsKey(tipoUsuarioActual)) {

                    FunctionDeclNode metodo = contexto.metodosPorClase.get(tipoUsuarioActual).get(nombreMetodo.nombreCampo);
                    if (metodo == null) {
                        throw new SemanticException("La clase '" + tipoUsuarioActual + "' no tiene un método '"
                                + nombreMetodo.nombreCampo + "' (línea " + n.getLine() + ")");
                    }

                    validarParametros(metodo.parametros, call.argumentos, tipoUsuarioActual + "." + nombreMetodo.nombreCampo, n.getLine());
                    ResolvedType rtRetorno = resolverTipoDeclarado(metodo.tipoRetorno, n.getLine());
                    tipoActual = rtRetorno.tipo();
                    tipoUsuarioActual = rtRetorno.tipoUsuario();

                } else if (esLlamadaAFuncionLibre && i == 0) {
                    String nombreFuncion = ((IdentifierNode) n.base).nombre;
                    FunctionDeclNode f = contexto.funciones.get(nombreFuncion);

                    validarParametros(f.parametros, call.argumentos, nombreFuncion, n.getLine());
                    ResolvedType rtRetorno = resolverTipoDeclarado(f.tipoRetorno, n.getLine());
                    tipoActual = rtRetorno.tipo();
                    tipoUsuarioActual = rtRetorno.tipoUsuario();

                } else if (esLlamadaAMetodoImplicito && i == 0) {
                    String nombreMetodoImplicito = ((IdentifierNode) n.base).nombre;
                    FunctionDeclNode metodo = contexto.metodosPorClase.get(claseActual).get(nombreMetodoImplicito);

                    validarParametros(metodo.parametros, call.argumentos,
                            claseActual + "." + nombreMetodoImplicito, n.getLine());
                    ResolvedType rtRetorno = resolverTipoDeclarado(metodo.tipoRetorno, n.getLine());
                    tipoActual = rtRetorno.tipo();
                    tipoUsuarioActual = rtRetorno.tipoUsuario();

                } else {
                    throw new SemanticException("No se pudo resolver la llamada (línea " + n.getLine() + ")");
                }
            }
        }

        n.tipoResuelto = tipoActual;
        n.tipoUsuarioResuelto = tipoUsuarioActual;
        return tipoActual;
    }

    @Override
    public Type visit(NewObjectNode n) {
        List<Type> tiposArgs = new ArrayList<>();
        List<String> tiposUsuarioArgs = new ArrayList<>();
        for (var arg : n.argumentos) {
            tiposArgs.add(arg.accept(this));
            tiposUsuarioArgs.add(tipoUsuarioDe(arg));
        }

        if (!contexto.classes.containsKey(n.nombreClase)) {
            throw new SemanticException("Clase no encontrada: '" + n.nombreClase
                    + "'. ¿Falta importar el archivo .z que la define? (línea " + n.getLine() + ")");
        }

        List<ConstructorDeclNode> constructores = contexto.constructoresPorClase.getOrDefault(n.nombreClase, List.of());
        boolean coincide = constructores.isEmpty() && n.argumentos.isEmpty();
        for (ConstructorDeclNode ctor : constructores) {
            if (coincideFirma(ctor.parametros, tiposArgs, tiposUsuarioArgs, n.getLine())) {
                coincide = true;
                break;
            }
        }
        if (!coincide) {
            throw new SemanticException("Ningún constructor de '" + n.nombreClase
                    + "' coincide con los " + tiposArgs.size() + " argumento(s) dado(s) (línea " + n.getLine() + ")");
        }

        n.tipoResuelto = Type.CLASE;
        n.tipoUsuarioResuelto = n.nombreClase;
        return Type.CLASE;
    }

    private boolean coincideFirma(List<ParamNode> parametros, List<Type> tiposArgs, List<String> tiposUsuarioArgs, int linea) {
        if (parametros.size() != tiposArgs.size()) return false;
        for (int i = 0; i < parametros.size(); i++) {
            ResolvedType rtParam = resolverTipoDeclarado(parametros.get(i).tipo, linea);
            if (!TypeCoercionTable.compatibles(rtParam.tipo(), rtParam.tipoUsuario(), tiposArgs.get(i), tiposUsuarioArgs.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Type visit(ArrayLiteralNode n) {
        if (n.tipoResuelto == Type.ESTRUCTURA && n.tipoUsuarioResuelto != null) {
            return verificarLiteralEstructura(n);
        }

        Type tipoComun = null;
        for (var val : n.valores) {
            Type t = val.accept(this);
            if (tipoComun == null) tipoComun = t;
            else if (TypeCoercionTable.resultado(tipoComun, t) == Type.ERROR) {
                throw new SemanticException("Elementos de arreglo con tipos incompatibles: "
                        + tipoComun + " y " + t + " (línea " + n.getLine() + ")");
            }
        }
        Type resultado = tipoComun == null ? Type.ERROR : tipoComun;
        n.tipoResuelto = resultado;
        return resultado;
    }

    /**
     * Valida "Direccion {"Calle Real", 42}" contra el layout real de la estructura:
     * misma cantidad de valores que de campos, y cada valor compatible con el tipo
     * de su campo (en orden de declaración). Si un campo es a su vez una estructura
     * y el valor es otro '{...}', se etiqueta y valida recursivamente, lo que permite
     * anidar: Persona {"Valeria", 25, {"Avenida Central", 500}}.
     */
    private Type verificarLiteralEstructura(ArrayLiteralNode n) {
        String nombreEstructura = n.tipoUsuarioResuelto;
        Map<String, ResolvedType> layout = contexto.layoutsEstructuras.get(nombreEstructura);
        if (layout == null) {
            throw new SemanticException("Estructura no encontrada: '" + nombreEstructura
                    + "' (línea " + n.getLine() + ")");
        }

        List<Map.Entry<String, ResolvedType>> campos = new ArrayList<>(layout.entrySet());
        if (campos.size() != n.valores.size()) {
            throw new SemanticException("La estructura '" + nombreEstructura + "' tiene " + campos.size()
                    + " campo(s) pero el literal trae " + n.valores.size() + " valor(es) (línea " + n.getLine() + ")");
        }

        for (int i = 0; i < campos.size(); i++) {
            String nombreCampo = campos.get(i).getKey();
            ResolvedType tipoCampo = campos.get(i).getValue();
            Expression valor = n.valores.get(i);

            if (tipoCampo.tipo() == Type.ESTRUCTURA && valor instanceof ArrayLiteralNode anidado) {
                anidado.tipoResuelto = Type.ESTRUCTURA;
                anidado.tipoUsuarioResuelto = tipoCampo.tipoUsuario();
            }

            Type tipoValor = ((ASTNode) valor).accept(this);
            String tipoUsuarioValor = tipoUsuarioDe(valor);
            if (tipoCampo.tipo() == Type.ESTRUCTURA && (valor instanceof ArrayLiteralNode || tipoValor == Type.ESTRUCTURA)) {
                tipoValor = Type.ESTRUCTURA;
                if (tipoUsuarioValor == null) tipoUsuarioValor = tipoCampo.tipoUsuario();
            }

            if (!TypeCoercionTable.compatibles(tipoCampo.tipo(), tipoCampo.tipoUsuario(), tipoValor, tipoUsuarioValor)) {
                throw new SemanticException("El campo '" + nombreCampo + "' de '" + nombreEstructura
                        + "' espera " + etiquetaTipo(tipoCampo.tipo(), tipoCampo.tipoUsuario()) + ", se dio "
                        + etiquetaTipo(tipoValor, tipoUsuarioValor) + " (línea " + n.getLine() + ")");
            }
        }

        n.tipoResuelto = Type.ESTRUCTURA;
        return Type.ESTRUCTURA;
    }
}
