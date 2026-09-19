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
 * Convierte el ParseTree de Pig Latin al AST unificado.
 *
 * Detalle de diseño: el cuerpo de MAIOR> se envuelve en un FunctionDeclNode
 * sintético llamado "main", porque en el AST unificado toda ejecución vive
 * dentro de una función (no existe un "cuerpo suelto" a nivel de programa).
 * Esto además reutiliza todo el manejo de scopes que ya tiene
 * SemanticAnalyzer.visit(FunctionDeclNode).
 */
public class PigLatinASTBuilder extends PigLatinLanguageBaseVisitor<ASTNode> {

    // ================= RAÍZ =================

    @Override
    public ASTNode visitPrograma(PigLatinLanguageParser.ProgramaContext ctx) {
        List<ImportNode> imports = new ArrayList<>();
        for (var imp : ctx.importacion()) imports.add((ImportNode) visit(imp));

        List<Declaration> declaraciones = new ArrayList<>();
        if (ctx.seccionVariables() != null) {
            for (var d : ctx.seccionVariables().declaracionGlobal()) {
                declaraciones.add((Declaration) visit(d));
            }
        }

        List<Statement> cuerpoPrincipal = new ArrayList<>();
        for (var s : ctx.seccionPrincipal().sentencia()) {
            cuerpoPrincipal.add((Statement) visit(s));
        }
        FunctionDeclNode main = new FunctionDeclNode("main", new ArrayList<>(), null, cuerpoPrincipal,
                false, null,
                ctx.seccionPrincipal().getStart().getLine(),
                ctx.seccionPrincipal().getStart().getCharPositionInLine());
        declaraciones.add(main);

        return new ProgramNode(imports, declaraciones,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitImportacion(PigLatinLanguageParser.ImportacionContext ctx) {
        String ruta = ctx.rutaImport().getText(); // "carpeta.Objeto1.z" (getText concatena sin espacios)
        ImportNode.TipoImport tipo = ruta.endsWith(".z")
                ? ImportNode.TipoImport.CLASE_Z
                : ImportNode.TipoImport.ESTRUCTURAS_Y;
        return new ImportNode(ruta, tipo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitDeclaracionGlobal(PigLatinLanguageParser.DeclaracionGlobalContext ctx) {
        if (ctx.declaracionVariable() != null) return visit(ctx.declaracionVariable());
        return visit(ctx.declaracionArreglo());
    }

    // ================= DECLARACIONES =================

    @Override
    public ASTNode visitDeclaracionVariable(PigLatinLanguageParser.DeclaracionVariableContext ctx) {
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        String nombre = ctx.ID().getText();

        // Los booleanos NO llevan palabra de tipo ("esto cifrado : falsus;"),
        // se infieren directo del literal -- por eso se fuerza tipo="bool"
        // (TypeMapper.deTexto("bool") ya lo resuelve a Type.BOOL).
        if (ctx.VERUM() != null) {
            return new VarDeclNode("bool", nombre, null, new LiteralNode("verum", line, column), line, column);
        }
        if (ctx.FALSUS() != null) {
            return new VarDeclNode("bool", nombre, null, new LiteralNode("falsus", line, column), line, column);
        }

        String tipo = ctx.tipo().getText();
        ASTNode init = ctx.valor() != null ? visit(ctx.valor()) : null;
        return new VarDeclNode(tipo, nombre, null, init, line, column);
    }

    @Override
    public ASTNode visitDeclaracionArreglo(PigLatinLanguageParser.DeclaracionArregloContext ctx) {
        String tipo = ctx.tipo().getText();
        String nombre = ctx.ID().getText();
        List<Integer> dimensiones = new ArrayList<>();
        dimensiones.add(intentarLeerEntero(ctx.expresion()));

        ASTNode init = ctx.listaValores() != null
                ? new ArrayLiteralNode(convertirListaValores(ctx.listaValores()),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine())
                : null;

        return new VarDeclNode(tipo, nombre, dimensiones, init,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private int intentarLeerEntero(PigLatinLanguageParser.ExpresionContext ctx) {
        try { return Integer.parseInt(ctx.getText()); }
        catch (NumberFormatException e) { return -1; } // TODO: tamaño no constante (igual que en Y?)
    }

    @Override
    public ASTNode visitValor(PigLatinLanguageParser.ValorContext ctx) {
        if (ctx.expresion() != null) return visit(ctx.expresion());
        return new ArrayLiteralNode(convertirListaValores(ctx.listaValores()),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private List<Expression> convertirListaValores(PigLatinLanguageParser.ListaValoresContext ctx) {
        List<Expression> valores = new ArrayList<>();
        for (var e : ctx.expresion()) valores.add((Expression) visit(e));
        return valores;
    }

    // ================= SENTENCIAS =================

    @Override
    public ASTNode visitSentencia(PigLatinLanguageParser.SentenciaContext ctx) {
        if (ctx.declaracionVariable() != null) return visit(ctx.declaracionVariable());
        if (ctx.declaracionArreglo() != null) return visit(ctx.declaracionArreglo());
        if (ctx.asignacion() != null) return visit(ctx.asignacion());
        if (ctx.incrementoDecremento() != null) return visit(ctx.incrementoDecremento());
        if (ctx.escritura() != null) return visit(ctx.escritura());
        if (ctx.lectura() != null) return visit(ctx.lectura());
        if (ctx.condicional() != null) return visit(ctx.condicional());
        if (ctx.cicloDum() != null) return visit(ctx.cicloDum());
        if (ctx.cicloFacere() != null) return visit(ctx.cicloFacere());
        if (ctx.cicloPer() != null) return visit(ctx.cicloPer());
        if (ctx.PERGE() != null) return new ContinueNode(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        if (ctx.INTERRUMPE() != null) return new BreakNode(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        if (ctx.llamada() != null) return visit(ctx.llamada());
        throw new IllegalStateException("Sentencia no reconocida en línea " + ctx.getStart().getLine());
    }

    @Override
    public ASTNode visitAsignacion(PigLatinLanguageParser.AsignacionContext ctx) {
        Expression destino = (Expression) visit(ctx.acceso());
        Expression valor = (Expression) visit(ctx.expresion());
        return new AssignNode(destino, "=", valor,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitIncrementoDecremento(PigLatinLanguageParser.IncrementoDecrementoContext ctx) {
        String operador = ctx.getChild(1).getText();
        return new IncrDecrNode(ctx.ID().getText(), operador,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitEscritura(PigLatinLanguageParser.EscrituraContext ctx) {
        List<Expression> argumentos = new ArrayList<>();
        for (var e : ctx.expresion()) argumentos.add((Expression) visit(e));
        return new PrintNode(argumentos,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitLectura(PigLatinLanguageParser.LecturaContext ctx) {
        String destino = ctx.ID() != null ? ctx.ID().getText() : null;
        return new ReadNode(destino,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitLlamada(PigLatinLanguageParser.LlamadaContext ctx) {
        Expression expr = (Expression) visit(ctx.acceso());
        return new ExprStatementNode(expr,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ---------------- Acceso (idéntico patrón a Y?/Zetariano) ----------------

    @Override
    public ASTNode visitAcceso(PigLatinLanguageParser.AccesoContext ctx) {
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        Expression base = new IdentifierNode(ctx.ID().getText(), line, column);

        List<Suffix> sufijos = new ArrayList<>();
        for (var s : ctx.sufijo()) sufijos.add(convertirSufijo(s));

        if (sufijos.isEmpty()) return base;
        return new PostfixExprNode(base, sufijos, line, column);
    }

    private Suffix convertirSufijo(PigLatinLanguageParser.SufijoContext ctx) {
        String primerToken = ctx.getChild(0).getText();
        if (primerToken.equals(".")) return new FieldSuffix(ctx.ID().getText());
        if (primerToken.equals("[")) return new IndexSuffix((Expression) visit(ctx.argumentos().expresion(0)));
        if (primerToken.equals("(")) {
            List<Expression> args = ctx.argumentos() != null ? convertirArgumentos(ctx.argumentos()) : new ArrayList<>();
            return new CallSuffix(args);
        }
        throw new IllegalStateException("Sufijo de acceso no reconocido en línea " + ctx.getStart().getLine());
    }

    private List<Expression> convertirArgumentos(PigLatinLanguageParser.ArgumentosContext ctx) {
        List<Expression> args = new ArrayList<>();
        for (var e : ctx.expresion()) args.add((Expression) visit(e));
        return args;
    }

    // ---------------- Condicional (si/aliter/finis) ----------------

    /**
     * A diferencia de Y?, esta gramática NO tiene una regla "bloque" separada:
     * las llaves '{' sentencia* '}' están escritas directo dentro de
     * 'condicional'/'cicloDum'/'cicloFacere'/'cicloPer'. Eso significa que
     * ctx.sentencia() mezclaría en una sola lista TODAS las sentencias de
     * TODAS las ramas (si/aliter/aliter) sin distinguir a cuál pertenece
     * cada una. Por eso se recorren los hijos crudos agrupando por cada
     * par de llaves encontrado, en orden.
     */
    private List<List<Statement>> extraerBloquesEntreLlaves(org.antlr.v4.runtime.ParserRuleContext ctx) {
        List<List<Statement>> bloques = new ArrayList<>();
        List<Statement> actual = null;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            var hijo = ctx.getChild(i);
            String texto = hijo.getText();
            if (texto.equals("{")) {
                actual = new ArrayList<>();
            } else if (texto.equals("}")) {
                bloques.add(actual);
                actual = null;
            } else if (actual != null && hijo instanceof PigLatinLanguageParser.SentenciaContext sc) {
                actual.add((Statement) visit(sc));
            }
        }
        return bloques;
    }

    @Override
    public ASTNode visitCondicional(PigLatinLanguageParser.CondicionalContext ctx) {
        List<List<Statement>> bloques = extraerBloquesEntreLlaves(ctx);
        List<PigLatinLanguageParser.ExpresionContext> condiciones = ctx.expresion();
        int numRamas = condiciones.size();

        List<IfNode.Branch> ramas = new ArrayList<>();
        for (int i = 0; i < numRamas; i++) {
            Expression cond = (Expression) visit(condiciones.get(i));
            ramas.add(new IfNode.Branch(cond, bloques.get(i)));
        }

        List<Statement> ramaContrario = (bloques.size() > numRamas) ? bloques.get(numRamas) : null;

        return new IfNode(ramas, ramaContrario,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ---------------- Ciclos ----------------

    @Override
    public ASTNode visitCicloDum(PigLatinLanguageParser.CicloDumContext ctx) {
        Expression condicion = (Expression) visit(ctx.expresion());
        List<Statement> cuerpo = extraerBloquesEntreLlaves(ctx).get(0);
        return new WhileNode(condicion, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCicloFacere(PigLatinLanguageParser.CicloFacereContext ctx) {
        List<Statement> cuerpo = extraerBloquesEntreLlaves(ctx).get(0);
        Expression condicion = (Expression) visit(ctx.expresion());
        return new DoWhileNode(cuerpo, condicion,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCicloPer(PigLatinLanguageParser.CicloPerContext ctx) {
        Statement inicializacion = (Statement) visit(ctx.forInit());
        Expression condicion = (Expression) visit(ctx.expresion());
        Statement actualizacion = (Statement) visit(ctx.forUpdate());
        List<Statement> cuerpo = extraerBloquesEntreLlaves(ctx).get(0);
        return new ForNode(inicializacion, condicion, actualizacion, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitForInit(PigLatinLanguageParser.ForInitContext ctx) {
        String tipo = ctx.tipo().getText();
        ASTNode valor = visit(ctx.valor());
        return new VarDeclNode(tipo, ctx.ID().getText(), null, valor,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitForUpdate(PigLatinLanguageParser.ForUpdateContext ctx) {
        if (ctx.acceso() != null) {
            Expression destino = (Expression) visit(ctx.acceso());
            Expression valor = (Expression) visit(ctx.expresion());
            return new AssignNode(destino, "=", valor,
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        String operador = ctx.getChild(1).getText();
        return new IncrDecrNode(ctx.ID().getText(), operador,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ================= EXPRESIONES =================

    @Override
    public ASTNode visitExprParentesis(PigLatinLanguageParser.ExprParentesisContext ctx) {
        return visit(ctx.expresion());
    }

    @Override
    public ASTNode visitExprNuevoObjeto(PigLatinLanguageParser.ExprNuevoObjetoContext ctx) {
        String nombreClase = ctx.ID().getText();
        List<Expression> argumentos = ctx.argumentos() != null
                ? convertirArgumentos(ctx.argumentos())
                : new ArrayList<>();
        return new NewObjectNode(nombreClase, argumentos,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprAcceso(PigLatinLanguageParser.ExprAccesoContext ctx) {
        return visit(ctx.acceso());
    }

    @Override
    public ASTNode visitExprLiteral(PigLatinLanguageParser.ExprLiteralContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public ASTNode visitExprMultiplicativo(PigLatinLanguageParser.ExprMultiplicativoContext ctx) {
        return new BinaryExprNode(ctx.op.getText(),
                (Expression) visit(ctx.expresion(0)), (Expression) visit(ctx.expresion(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprAditivo(PigLatinLanguageParser.ExprAditivoContext ctx) {
        return new BinaryExprNode(ctx.op.getText(),
                (Expression) visit(ctx.expresion(0)), (Expression) visit(ctx.expresion(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprRelacional(PigLatinLanguageParser.ExprRelacionalContext ctx) {
        return new BinaryExprNode(ctx.op.getText(),
                (Expression) visit(ctx.expresion(0)), (Expression) visit(ctx.expresion(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprLogico(PigLatinLanguageParser.ExprLogicoContext ctx) {
        return new BinaryExprNode(ctx.op.getText(),
                (Expression) visit(ctx.expresion(0)), (Expression) visit(ctx.expresion(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitLiteral(PigLatinLanguageParser.LiteralContext ctx) {
        return new LiteralNode(ctx.getText(),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
}