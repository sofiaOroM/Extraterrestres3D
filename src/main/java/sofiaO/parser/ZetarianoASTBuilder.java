package sofiaO.parser;

import sofiaO.ast.ASTNode;
import sofiaO.ast.Declaration;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.sentencia.*;
import sofiaO.ast.expresion.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte el ParseTree de Zetariano al AST unificado. Un archivo .z
 * define UNA sola clase, así que visitPrograma produce un ProgramNode que
 * envuelve exactamente un ClassDeclNode.
 */
public class ZetarianoASTBuilder extends ZetarianoLanguageBaseVisitor<ASTNode> {

    // ================= RAÍZ =================

    @Override
    public ASTNode visitPrograma(ZetarianoLanguageParser.ProgramaContext ctx) {
        ClassDeclNode clase = (ClassDeclNode) visit(ctx.claseDecl());
        List<Declaration> declaraciones = new ArrayList<>();
        declaraciones.add(clase);
        return new ProgramNode(new ArrayList<>(), declaraciones,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitClaseDecl(ZetarianoLanguageParser.ClaseDeclContext ctx) {
        String nombreClase = ctx.ID().getText();
        List<FieldNode> atributos = new ArrayList<>();
        List<ConstructorDeclNode> constructores = new ArrayList<>();
        List<FunctionDeclNode> metodos = new ArrayList<>();

        for (var miembro : ctx.miembroClase()) {
            if (miembro.atributo() != null) {
                atributos.add((FieldNode) visit(miembro.atributo()));
            } else if (miembro.constructorDecl() != null) {
                constructores.add(construirConstructor(miembro.constructorDecl(), nombreClase));
            } else if (miembro.metodoDecl() != null) {
                metodos.add(construirMetodo(miembro.metodoDecl(), nombreClase));
            }
        }

        return new ClassDeclNode(nombreClase, atributos, constructores, metodos,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitAtributo(ZetarianoLanguageParser.AtributoContext ctx) {
        String textoTipo = ctx.tipo().getText();
        String tipoBase = tipoBaseSinCorchetes(textoTipo);
        List<Integer> dimensiones = dimensionesDesdeTipo(textoTipo);
        return new FieldNode(tipoBase, ctx.ID().getText(), dimensiones,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // constructorDecl/metodoDecl necesitan el nombre de la clase, que no
    // viene en su propio ctx -- por eso son helpers privados en vez de
    // overrides de visitConstructorDecl/visitMetodoDecl.
    private ConstructorDeclNode construirConstructor(ZetarianoLanguageParser.ConstructorDeclContext ctx,
                                                     String claseDuena) {
        List<ParamNode> parametros = ctx.parametros() != null ? convertirParametros(ctx.parametros()) : new ArrayList<>();
        List<Statement> cuerpo = convertirBloque(ctx.bloque());
        return new ConstructorDeclNode(claseDuena, parametros, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private FunctionDeclNode construirMetodo(ZetarianoLanguageParser.MetodoDeclContext ctx, String claseDuena) {
        String nombre = ctx.ID().getText();
        List<ParamNode> parametros = ctx.parametros() != null ? convertirParametros(ctx.parametros()) : new ArrayList<>();
        String tipoRetorno = ctx.VOID() != null ? null : ctx.tipo().getText();
        List<Statement> cuerpo = convertirBloque(ctx.bloque());
        return new FunctionDeclNode(nombre, parametros, tipoRetorno, cuerpo, true, claseDuena,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private List<ParamNode> convertirParametros(ZetarianoLanguageParser.ParametrosContext ctx) {
        List<ParamNode> parametros = new ArrayList<>();
        for (var p : ctx.parametro()) parametros.add((ParamNode) visit(p));
        return parametros;
    }

    @Override
    public ASTNode visitParametro(ZetarianoLanguageParser.ParametroContext ctx) {
        String tipoBase = tipoBaseSinCorchetes(ctx.tipo().getText());
        // Zetariano no distingue paso por referencia en la sintaxis del
        // parámetro (a diferencia de Y?, que usa [] y {} explícitos).
        return new ParamNode(tipoBase, ctx.ID().getText(), ParamNode.ModoPaso.VALOR,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private List<Statement> convertirBloque(ZetarianoLanguageParser.BloqueContext ctx) {
        List<Statement> sentencias = new ArrayList<>();
        for (var s : ctx.sentencia()) sentencias.add((Statement) visit(s));
        return sentencias;
    }

    /**
     * if/while/for pueden tener cuerpo con llaves o una sola sentencia suelta.
     */
    private List<Statement> convertirCuerpo(ZetarianoLanguageParser.SentenciaOBloqueContext ctx) {
        if (ctx.bloque() != null) return convertirBloque(ctx.bloque());
        List<Statement> cuerpo = new ArrayList<>();
        cuerpo.add((Statement) visit(ctx.sentencia()));
        return cuerpo;
    }

    // ================= TIPOS CON ARREGLOS ESTILO JAVA =================

    /**
     * Zetariano declara arreglos con corchetes VACÍOS pegados al tipo
     * ("int[]", "int[][]") -- el tamaño real se define después, con
     * 'new int[5]' o un inicializador {...}, no en la propia declaración
     * de tipo como en Y? (que exige un tamaño constante ahí mismo).
     */
    private String tipoBaseSinCorchetes(String textoTipo) {
        return textoTipo.replace("[]", "");
    }

    private List<Integer> dimensionesDesdeTipo(String textoTipo) {
        List<Integer> dims = new ArrayList<>();
        String resto = textoTipo;
        while (resto.endsWith("[]")) {
            dims.add(-1); // tamaño desconocido en este punto (estilo Java)
            resto = resto.substring(0, resto.length() - 2);
        }
        return dims;
    }

    // ================= SENTENCIAS =================

    @Override
    public ASTNode visitSentencia(ZetarianoLanguageParser.SentenciaContext ctx) {
        if (ctx.declaracionVariable() != null) return visit(ctx.declaracionVariable());
        if (ctx.asignacion() != null) return visit(ctx.asignacion());
        if (ctx.incrementoDecremento() != null) return visit(ctx.incrementoDecremento());
        if (ctx.expresionSentencia() != null) return visit(ctx.expresionSentencia());
        if (ctx.condicional() != null) return visit(ctx.condicional());
        if (ctx.switchStmt() != null) return visit(ctx.switchStmt());
        if (ctx.cicloFor() != null) return visit(ctx.cicloFor());
        if (ctx.cicloWhile() != null) return visit(ctx.cicloWhile());
        if (ctx.cicloDoWhile() != null) return visit(ctx.cicloDoWhile());
        if (ctx.BREAK() != null) return new BreakNode(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        if (ctx.CONTINUE() != null)
            return new ContinueNode(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        if (ctx.retorno() != null) return visit(ctx.retorno());
        if (ctx.bloque() != null) {
            // Bloque anidado suelto "{ }" sin if/while/for encima: no aparece
            // en los ejemplos del documento y el catálogo de nodos no tiene
            // un "BlockNode" genérico todavía. Se deja explícito en vez de
            // fallar en silencio.
            throw new UnsupportedOperationException(
                    "Bloque anidado suelto '{ }' como sentencia no soportado (línea "
                            + ctx.getStart().getLine() + ")");
        }
        throw new IllegalStateException("Sentencia no reconocida en línea " + ctx.getStart().getLine());
    }

    @Override
    public ASTNode visitDeclaracionVariable(ZetarianoLanguageParser.DeclaracionVariableContext ctx) {
        String textoTipo = ctx.tipo().getText();
        String tipoBase = tipoBaseSinCorchetes(textoTipo);
        List<Integer> dimensiones = dimensionesDesdeTipo(textoTipo);
        ASTNode init = ctx.valorInicial() != null ? visit(ctx.valorInicial()) : null;
        return new VarDeclNode(tipoBase, ctx.ID().getText(), dimensiones, init,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitValorInicial(ZetarianoLanguageParser.ValorInicialContext ctx) {
        if (ctx.expresion() != null) return visit(ctx.expresion());
        return visit(ctx.arrayLiteral());
    }

    @Override
    public ASTNode visitArrayLiteral(ZetarianoLanguageParser.ArrayLiteralContext ctx) {
        List<Expression> valores = ctx.listaValores() != null
                ? convertirListaValores(ctx.listaValores())
                : new ArrayList<>();
        return new ArrayLiteralNode(valores,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private List<Expression> convertirListaValores(ZetarianoLanguageParser.ListaValoresContext ctx) {
        List<Expression> valores = new ArrayList<>();
        for (var e : ctx.expresion()) valores.add((Expression) visit(e));
        return valores;
    }

    @Override
    public ASTNode visitAsignacion(ZetarianoLanguageParser.AsignacionContext ctx) {
        Expression destino = (Expression) visit(ctx.acceso());
        Expression valor = (Expression) visit(ctx.expresion());
        return new AssignNode(destino, ctx.op.getText(), valor,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitIncrementoDecremento(ZetarianoLanguageParser.IncrementoDecrementoContext ctx) {
        String operador = ctx.getChild(1).getText();
        return new IncrDecrNode(ctx.ID().getText(), operador,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExpresionSentencia(ZetarianoLanguageParser.ExpresionSentenciaContext ctx) {
        Expression expr = (Expression) visit(ctx.expresion());
        return new ExprStatementNode(expr,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ---------------- Condicionales ----------------

    @Override
    public ASTNode visitCondicional(ZetarianoLanguageParser.CondicionalContext ctx) {
        Expression cond = (Expression) visit(ctx.expresion());
        List<Statement> cuerpoSi = convertirCuerpo(ctx.sentenciaOBloque(0));
        List<IfNode.Branch> ramas = new ArrayList<>();
        ramas.add(new IfNode.Branch(cond, cuerpoSi));

        // "else if (...)" NO se aplana: queda como un IfNode anidado dentro
        // de ramaContrario (equivalente semánticamente a "else { if (...) }").
        List<Statement> ramaContrario = ctx.ELSE() != null
                ? convertirCuerpo(ctx.sentenciaOBloque(1))
                : null;

        return new IfNode(ramas, ramaContrario,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitSwitchStmt(ZetarianoLanguageParser.SwitchStmtContext ctx) {
        Expression selector = (Expression) visit(ctx.expresion());
        List<CaseNode> casos = new ArrayList<>();
        for (var c : ctx.casoSwitch()) casos.add((CaseNode) visit(c));
        if (ctx.defaultSwitch() != null) casos.add((CaseNode) visit(ctx.defaultSwitch()));
        return new SwitchNode(selector, casos,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCasoSwitch(ZetarianoLanguageParser.CasoSwitchContext ctx) {
        String valor = ctx.ENTERO() != null ? ctx.ENTERO().getText() : ctx.CADENA_LITERAL().getText();
        List<Statement> cuerpo = new ArrayList<>();
        for (var s : ctx.sentencia()) cuerpo.add((Statement) visit(s));
        return new CaseNode(valor, false, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitDefaultSwitch(ZetarianoLanguageParser.DefaultSwitchContext ctx) {
        List<Statement> cuerpo = new ArrayList<>();
        for (var s : ctx.sentencia()) cuerpo.add((Statement) visit(s));
        return new CaseNode(null, true, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ---------------- Ciclos ----------------

    @Override
    public ASTNode visitCicloFor(ZetarianoLanguageParser.CicloForContext ctx) {
        Statement inicializacion = ctx.forInit() != null ? (Statement) visit(ctx.forInit()) : null;
        Expression condicion = ctx.expresion() != null ? (Expression) visit(ctx.expresion()) : null;
        Statement actualizacion = ctx.forUpdate() != null ? (Statement) visit(ctx.forUpdate()) : null;
        List<Statement> cuerpo = convertirCuerpo(ctx.sentenciaOBloque());
        return new ForNode(inicializacion, condicion, actualizacion, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitForInitDeclaracion(ZetarianoLanguageParser.ForInitDeclaracionContext ctx) {
        String textoTipo = ctx.tipo().getText();
        String tipoBase = tipoBaseSinCorchetes(textoTipo);
        List<Integer> dimensiones = dimensionesDesdeTipo(textoTipo);
        Expression valor = (Expression) visit(ctx.expresion());
        return new VarDeclNode(tipoBase, ctx.ID().getText(), dimensiones, valor,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitForInitAsignacion(ZetarianoLanguageParser.ForInitAsignacionContext ctx) {
        Expression destino = (Expression) visit(ctx.acceso());
        Expression valor = (Expression) visit(ctx.expresion());
        return new AssignNode(destino, "=", valor,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitForUpdateIncremento(ZetarianoLanguageParser.ForUpdateIncrementoContext ctx) {
        String operador = ctx.getChild(1).getText();
        return new IncrDecrNode(ctx.ID().getText(), operador,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitForUpdateAsignacion(ZetarianoLanguageParser.ForUpdateAsignacionContext ctx) {
        Expression destino = (Expression) visit(ctx.acceso());
        Expression valor = (Expression) visit(ctx.expresion());
        return new AssignNode(destino, "=", valor,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCicloWhile(ZetarianoLanguageParser.CicloWhileContext ctx) {
        Expression condicion = (Expression) visit(ctx.expresion());
        List<Statement> cuerpo = convertirCuerpo(ctx.sentenciaOBloque());
        return new WhileNode(condicion, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCicloDoWhile(ZetarianoLanguageParser.CicloDoWhileContext ctx) {
        List<Statement> cuerpo = convertirBloque(ctx.bloque());
        Expression condicion = (Expression) visit(ctx.expresion());
        return new DoWhileNode(cuerpo, condicion,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitRetorno(ZetarianoLanguageParser.RetornoContext ctx) {
        Expression valor = ctx.expresion() != null ? (Expression) visit(ctx.expresion()) : null;
        return new ReturnNode(valor,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ================= EXPRESIONES =================

    @Override
    public ASTNode visitExpresion(ZetarianoLanguageParser.ExpresionContext ctx) {
        return visit(ctx.expresionTernaria());
    }

    @Override
    public ASTNode visitExpresionTernaria(ZetarianoLanguageParser.ExpresionTernariaContext ctx) {
        ASTNode base = visit(ctx.expresionLogicaOr());
        if (ctx.expresion().isEmpty()) return base; // sin '?' ':' -- no es ternario
        Expression siVerdadero = (Expression) visit(ctx.expresion(0));
        Expression siFalso = (Expression) visit(ctx.expresion(1));
        return new TernaryExprNode((Expression) base, siVerdadero, siFalso,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // Los 6 niveles de precedencia binaria comparten la misma forma:
    // "operando (OPERADOR operando)*", sin alternativas etiquetadas. Se
    // pliegan con UN solo helper genérico que recorre los hijos crudos.
    @Override
    public ASTNode visitExpresionLogicaOr(ZetarianoLanguageParser.ExpresionLogicaOrContext ctx) {
        return plegarBinaria(ctx);
    }

    @Override
    public ASTNode visitExpresionLogicaAnd(ZetarianoLanguageParser.ExpresionLogicaAndContext ctx) {
        return plegarBinaria(ctx);
    }

    @Override
    public ASTNode visitExpresionIgualdad(ZetarianoLanguageParser.ExpresionIgualdadContext ctx) {
        return plegarBinaria(ctx);
    }

    @Override
    public ASTNode visitExpresionRelacional(ZetarianoLanguageParser.ExpresionRelacionalContext ctx) {
        return plegarBinaria(ctx);
    }

    @Override
    public ASTNode visitExpresionAditiva(ZetarianoLanguageParser.ExpresionAditivaContext ctx) {
        return plegarBinaria(ctx);
    }

    @Override
    public ASTNode visitExpresionMultiplicativa(ZetarianoLanguageParser.ExpresionMultiplicativaContext ctx) {
        return plegarBinaria(ctx);
    }

    private ASTNode plegarBinaria(org.antlr.v4.runtime.ParserRuleContext ctx) {
        ASTNode resultado = visit(ctx.getChild(0));
        int i = 1;
        while (i < ctx.getChildCount()) {
            String operador = ctx.getChild(i).getText();
            ASTNode derecha = visit(ctx.getChild(i + 1));
            resultado = new BinaryExprNode(operador, (Expression) resultado, (Expression) derecha,
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
            i += 2;
        }
        return resultado;
    }

    @Override
    public ASTNode visitExpresionUnaria(ZetarianoLanguageParser.ExpresionUnariaContext ctx) {
        if (ctx.expresionPrimaria() != null) return visit(ctx.expresionPrimaria());
        String operador = ctx.getChild(0).getText(); // '!' o '-'
        Expression operando = (Expression) visit(ctx.expresionUnaria());
        return new UnaryExprNode(operador, operando,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprParentesis(ZetarianoLanguageParser.ExprParentesisContext ctx) {
        return visit(ctx.expresion());
    }

    @Override
    public ASTNode visitExprNuevoObjeto(ZetarianoLanguageParser.ExprNuevoObjetoContext ctx) {
        String nombreClase = ctx.ID().getText();
        List<Expression> argumentos = ctx.argumentos() != null
                ? convertirArgumentos(ctx.argumentos())
                : new ArrayList<>();
        return new NewObjectNode(nombreClase, argumentos,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private List<Expression> convertirArgumentos(ZetarianoLanguageParser.ArgumentosContext ctx) {
        List<Expression> args = new ArrayList<>();
        for (var e : ctx.expresion()) args.add((Expression) visit(e));
        return args;
    }

    @Override
    public ASTNode visitExprNuevoArreglo(ZetarianoLanguageParser.ExprNuevoArregloContext ctx) {
        // new int[5]  /  new int[3][3]
        // No existe un "NewArrayNode" propio en el catálogo; se modela como
        // un ArrayLiteralNode relleno de ceros del tamaño pedido (mismo
        // criterio que usa QuadrupleGenerator para reservar espacio). Si el
        // tamaño no es un literal constante, se reserva 0 (TODO: tamaño
        // dinámico no soportado).
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        int tamanoTotal = 1;
        for (var e : ctx.expresion()) {
            try {
                tamanoTotal *= Integer.parseInt(e.getText());
            } catch (NumberFormatException ex) {
                tamanoTotal = 0;
            }
        }
        List<Expression> valoresVacios = new ArrayList<>();
        for (int i = 0; i < tamanoTotal; i++) valoresVacios.add(new LiteralNode("0", line, column));
        return new ArrayLiteralNode(valoresVacios, line, column);
    }

    @Override
    public ASTNode visitExprAcceso(ZetarianoLanguageParser.ExprAccesoContext ctx) {
        return visit(ctx.acceso());
    }

    @Override
    public ASTNode visitExprLiteral(ZetarianoLanguageParser.ExprLiteralContext ctx) {
        return visit(ctx.literal());
    }

    /**
     * acceso: ID sufijo* ; sufijo: '.' ID | '[' expresion ']' | '(' argumentos? ')' ;
     * Igual que en Y?, se usa el mismo Suffix unificado (FieldSuffix/IndexSuffix/CallSuffix).
     */
    @Override
    public ASTNode visitAcceso(ZetarianoLanguageParser.AccesoContext ctx) {
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        Expression base = new IdentifierNode(ctx.ID().getText(), line, column);

        List<Suffix> sufijos = new ArrayList<>();
        for (var s : ctx.sufijo()) sufijos.add(convertirSufijo(s));

        if (sufijos.isEmpty()) return base;
        return new PostfixExprNode(base, sufijos, line, column);
    }

    private Suffix convertirSufijo(ZetarianoLanguageParser.SufijoContext ctx) {
        // Se distingue por el PRIMER TOKEN crudo, no por accesores como
        // ctx.argumentos() (que puede ser null tanto si es '.ID' como si es
        // una llamada sin argumentos "()").
        String primerToken = ctx.getChild(0).getText();
        if (primerToken.equals(".")) return new FieldSuffix(ctx.ID().getText());
        if (primerToken.equals("[")) return new IndexSuffix((Expression) visit(ctx.argumentos().expresion(0)));
        if (primerToken.equals("(")) {
            List<Expression> args = ctx.argumentos() != null ? convertirArgumentos(ctx.argumentos()) : new ArrayList<>();
            return new CallSuffix(args);
        }
        throw new IllegalStateException("Sufijo de acceso no reconocido en línea " + ctx.getStart().getLine());
    }

    @Override
    public ASTNode visitLiteral(ZetarianoLanguageParser.LiteralContext ctx) {
        return new LiteralNode(ctx.getText(),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
}