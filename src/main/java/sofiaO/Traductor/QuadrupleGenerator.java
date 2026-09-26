package sofiaO.Traductor;

import sofiaO.Traductor.generators.DeclarationGenerator;
import sofiaO.Traductor.generators.ExpressionGenerator;
import sofiaO.Traductor.generators.GeneratorContext;
import sofiaO.Traductor.generators.LoopLabels;
import sofiaO.Traductor.generators.StatementGenerator;
import sofiaO.ast.ASTNode;
import sofiaO.ast.ASTVisitor;
import sofiaO.ast.BaseNode;
import sofiaO.ast.Expression;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.sentencia.*;
import sofiaO.ast.expresion.*;
import sofiaO.semantico.ProgramContext;
import sofiaO.semantico.ResolvedType;
import sofiaO.util.Type;
import sofiaO.util.TypeMapper;

import java.util.*;

/**
 * A través de GeneratorContext, el único estado real (temporales, etiquetas,
 * tipos, contexto de clase/método actual). Así, agregar o revisar la generación
 * de un tipo de nodo es tocar un solo archivo pequeño, no este.
 */
public class QuadrupleGenerator implements ASTVisitor<String>, GeneratorContext {

    public final List<Quadruple> cuartetas = new ArrayList<>();
    public final Map<String, String> tiposUsuarioDeLugares = new LinkedHashMap<>();
    public final Map<String, Type> tiposDeLugares = new LinkedHashMap<>();

    public ProgramContext contexto;

    private int contadorTemp = 0;
    private int contadorLabel = 0;
    private final Map<String, List<Integer>> dimensionesArreglos = new HashMap<>();

    private Set<String> atributosDeClaseActual = Collections.emptySet();
    private Set<String> localesDelMetodoActual = new HashSet<>();
    private String claseActual = null;
    private Set<String> metodosDeClaseActual = Collections.emptySet();
    private final Deque<LoopLabels> pilaCiclos = new ArrayDeque<>();

    private final DeclarationGenerator declarations = new DeclarationGenerator(this);
    private final StatementGenerator statements = new StatementGenerator(this);
    private final ExpressionGenerator expressions = new ExpressionGenerator(this);

    public void imprimir() {
        for (Quadruple q : cuartetas) System.out.println(q);
    }

    //  ASTVisitor<String>: cada visit() delega en su colaborador.

    @Override public String visit(ProgramNode n)        { return declarations.visit(n); }
    @Override public String visit(ImportNode n)         { return declarations.visit(n); }
    @Override public String visit(StructDeclNode n)     { return declarations.visit(n); }
    @Override public String visit(ClassDeclNode n)      { return declarations.visit(n); }
    @Override public String visit(FunctionDeclNode n)   { return declarations.visit(n); }
    @Override public String visit(ConstructorDeclNode n){ return declarations.visit(n); }
    @Override public String visit(FieldNode n)          { return declarations.visit(n); }
    @Override public String visit(ParamNode n)          { return declarations.visit(n); }
    @Override public String visit(VarDeclNode n)        { return declarations.visit(n); }

    @Override public String visit(AssignNode n)         { return statements.visit(n); }
    @Override public String visit(IncrDecrNode n)       { return statements.visit(n); }
    @Override public String visit(IfNode n)             { return statements.visit(n); }
    @Override public String visit(SwitchNode n)         { return statements.visit(n); }
    @Override public String visit(CaseNode n)           { return statements.visit(n); }
    @Override public String visit(WhileNode n)          { return statements.visit(n); }
    @Override public String visit(DoWhileNode n)        { return statements.visit(n); }
    @Override public String visit(ForNode n)            { return statements.visit(n); }
    @Override public String visit(BreakNode n)          { return statements.visit(n); }
    @Override public String visit(ContinueNode n)       { return statements.visit(n); }
    @Override public String visit(ReturnNode n)         { return statements.visit(n); }
    @Override public String visit(PrintNode n)          { return statements.visit(n); }
    @Override public String visit(ReadNode n)           { return statements.visit(n); }
    @Override public String visit(ExprStatementNode n)  { return statements.visit(n); }

    @Override public String visit(BinaryExprNode n)     { return expressions.visit(n); }
    @Override public String visit(UnaryExprNode n)      { return expressions.visit(n); }
    @Override public String visit(TernaryExprNode n)    { return expressions.visit(n); }
    @Override public String visit(LiteralNode n)        { return expressions.visit(n); }
    @Override public String visit(IdentifierNode n)     { return expressions.visit(n); }
    @Override public String visit(PostfixExprNode n)    { return expressions.visit(n); }
    @Override public String visit(NewObjectNode n)      { return expressions.visit(n); }
    @Override public String visit(ArrayLiteralNode n)   { return expressions.visit(n); }

    //  GeneratorContext: el estado real vive aquí; los colaboradores solo lo piden.

    @Override
    public void emit(String op, String arg1, String arg2, String result, Type tipo) {
        cuartetas.add(new Quadruple(op, arg1, arg2, result, tipo));
    }

    @Override
    public void emitVariadic(String op, String arg1, String result, Type tipo,
                             List<String> extra, List<Type> tiposExtra) {
        cuartetas.add(new Quadruple(op, arg1, null, result, tipo, extra, tiposExtra));
    }

    @Override
    public void emitLabel(String etiqueta) {
        cuartetas.add(new Quadruple("label", null, null, etiqueta, Type.VOID));
    }

    @Override
    public String newTemp(Type tipo) {
        String t = "t" + (contadorTemp++);
        tiposDeLugares.put(t, tipo == null ? Type.ERROR : tipo);
        emit("declare", null, null, t, tipo);
        return t;
    }

    @Override
    public String newTempOf(Type tipo, String tipoUsr) {
        String t = newTemp(tipo);
        if (tipoUsr != null) tiposUsuarioDeLugares.put(t, tipoUsr);
        return t;
    }

    @Override
    public String newLabel() { return "L" + (contadorLabel++); }

    @Override
    public String generate(ASTNode n) { return n.accept(this); }

    @Override
    public Type typeOf(ASTNode n) {
        return (n instanceof BaseNode b) ? b.tipoResuelto : Type.ERROR;
    }

    @Override
    public Type typeOfPlace(String lugar) {
        return tiposDeLugares.getOrDefault(lugar, Type.ERROR);
    }

    @Override
    public Type typeOfVariable(String nombre) {
        boolean esAtributo = atributosDeClaseActual.contains(nombre) && !localesDelMetodoActual.contains(nombre);
        if (esAtributo && contexto != null && claseActual != null) {
            var layout = contexto.layoutsClases.get(claseActual);
            if (layout != null && layout.containsKey(nombre)) return layout.get(nombre).tipo();
        }
        return typeOfPlace(nombre);
    }

    @Override
    public String userTypeOfBase(Expression base, String lugar) {
        if (base instanceof BaseNode b && b.tipoUsuarioResuelto != null) return b.tipoUsuarioResuelto;
        return tiposUsuarioDeLugares.get(lugar);
    }

    @Override
    public ResolvedType typeOfField(String tipoUsr, String campo) {
        if (contexto == null || tipoUsr == null) return null;
        var layout = contexto.layoutsClases.get(tipoUsr);
        if (layout == null) layout = contexto.layoutsEstructuras.get(tipoUsr);
        return layout == null ? null : layout.get(campo);
    }

    @Override
    public ResolvedType resolveTypeText(String texto) {
        if (texto == null || texto.equals("void")) return ResolvedType.primitivo(Type.VOID);
        Type t = TypeMapper.deTexto(texto);
        if (t != Type.ERROR) return ResolvedType.primitivo(t);
        if (contexto != null) {
            if (contexto.classes.containsKey(texto)) return new ResolvedType(Type.CLASE, texto);
            if (contexto.structs.containsKey(texto)) return new ResolvedType(Type.ESTRUCTURA, texto);
        }
        Type u = TypeMapper.deUsuario(texto);
        return u == Type.ERROR ? ResolvedType.ERROR : new ResolvedType(u, texto);
    }

    @Override
    public FunctionDeclNode findMethod(String clase, String nombre) {
        if (contexto == null || clase == null) return null;
        var metodos = contexto.metodosPorClase.get(clase);
        return metodos == null ? null : metodos.get(nombre);
    }

    @Override
    public Map<String, List<Integer>> arrayDimensions() { return dimensionesArreglos; }

    @Override
    public int countConsecutiveIndices(List<Suffix> sufijos, int desde) {
        int cantidad = 0;
        while (desde + cantidad < sufijos.size() && sufijos.get(desde + cantidad) instanceof IndexSuffix) {
            cantidad++;
        }
        return cantidad;
    }

    @Override
    public String flattenIndices(String nombreArreglo, List<String> indices) {
        if (indices.size() == 1) return indices.get(0);

        List<Integer> dims = dimensionesArreglos.get(nombreArreglo);
        if (dims == null || dims.size() < indices.size()) {
            return indices.get(0);
        }

        String acumulado = indices.get(0);
        for (int i = 1; i < indices.size(); i++) {
            String tempMultiplicacion = newTemp(Type.ENTERO);
            emit("*", acumulado, String.valueOf(dims.get(i)), tempMultiplicacion, Type.ENTERO);

            String tempSuma = newTemp(Type.ENTERO);
            emit("+", tempMultiplicacion, indices.get(i), tempSuma, Type.ENTERO);

            acumulado = tempSuma;
        }
        return acumulado;
    }

    @Override
    public Type typeOfOperand(Expression e, String lugar) {
        Type t = typeOf(e);
        if (t == Type.ERROR) t = typeOfPlace(lugar);
        return t;
    }

    @Override
    public String asString(Type tipo, String lugar) {
        String op = switch (tipo) {
            case CADENA -> null; // ya es cadena
            case ENTERO -> "int_to_str";
            case FLOTANTE -> "double_to_str";
            case CARACTER -> "char_to_str";
            case BOOL -> "bool_to_str";
            default -> throw new IllegalStateException("No se puede convertir " + tipo + " a cadena");
        };
        if (op == null) return lugar;
        String temp = newTemp(Type.CADENA);
        emit(op, lugar, null, temp, Type.CADENA);
        return temp;
    }

    @Override
    public void startLoop(String continuar, String romper) {
        pilaCiclos.push(new LoopLabels(continuar, romper));
    }

    @Override
    public void endLoop() { pilaCiclos.pop(); }

    @Override
    public LoopLabels currentLoop() { return pilaCiclos.peek(); }

    @Override
    public ProgramContext context() { return contexto; }

    @Override
    public String currentClass() { return claseActual; }

    @Override
    public Set<String> currentClassMethods() { return metodosDeClaseActual; }

    @Override
    public Set<String> currentClassFields() { return atributosDeClaseActual; }

    @Override
    public Set<String> currentMethodLocals() { return localesDelMetodoActual; }

    @Override
    public void enterClass(String nombre, Set<String> atributos, Set<String> metodos) {
        this.claseActual = nombre;
        this.atributosDeClaseActual = atributos;
        this.metodosDeClaseActual = metodos;
    }

    @Override
    public void exitClass() {
        this.claseActual = null;
        this.atributosDeClaseActual = Collections.emptySet();
        this.metodosDeClaseActual = Collections.emptySet();
    }

    @Override
    public void startLocals(Set<String> nombresParametros) {
        this.localesDelMetodoActual = new HashSet<>(nombresParametros);
    }

    @Override
    public Map<String, Type> placeTypes() { return tiposDeLugares; }

    @Override
    public Map<String, String> placeUserTypes() { return tiposUsuarioDeLugares; }
}