package sofiaO.parser;

import org.antlr.v4.runtime.tree.ParseTree;
import sofiaO.ast.ASTNode;
import sofiaO.ast.Declaration;
import sofiaO.ast.Expression;
import sofiaO.ast.Statement;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.sentencia.*;
import sofiaO.ast.expresion.*;

import java.util.ArrayList;
import java.util.List;

public class YLanguageASTBuilder extends YLanguageBaseVisitor<ASTNode> {

    // ================= RAÍZ DEL PROGRAMA =================

    @Override
    public ASTNode visitPrograma(YLanguageParser.ProgramaContext ctx) {
        List<Declaration> declaraciones = new ArrayList<>();

        if (ctx == null) {
            return new ProgramNode(new ArrayList<>(), declaraciones, 1, 0);
        }

        if (ctx.seccionEstructuras() != null && ctx.seccionEstructuras().declaracionEstructuras() != null) {
            for (YLanguageParser.DeclaracionEstructurasContext d : ctx.seccionEstructuras().declaracionEstructuras()) {
                ASTNode nodo = visit(d);
                if (nodo instanceof Declaration decl) {
                    declaraciones.add(decl);
                }
            }
        }

        if (ctx.seccionFunciones() != null && ctx.seccionFunciones().declaracionFunciones() != null) {
            for (YLanguageParser.DeclaracionFuncionesContext d : ctx.seccionFunciones().declaracionFunciones()) {
                ASTNode nodo = visit(d);
                if (nodo instanceof Declaration decl) {
                    declaraciones.add(decl);
                }
            }
        }

        return new ProgramNode(new ArrayList<>(), declaraciones,
                ctx.getStart() != null ? ctx.getStart().getLine() : 1,
                ctx.getStart() != null ? ctx.getStart().getCharPositionInLine() : 0);
    }

    // ================= ESTRUCTURAS =================

    @Override
    public ASTNode visitDeclaracionEstructuras(YLanguageParser.DeclaracionEstructurasContext ctx) {
        String nombre = ctx.ID().getText();
        List<FieldNode> campos = new ArrayList<>();
        if (ctx.atributoEstructura() != null) {
            for (YLanguageParser.AtributoEstructuraContext a : ctx.atributoEstructura()) {
                ASTNode res = visit(a);
                if (res instanceof FieldNode campo) {
                    campos.add(campo);
                }
            }
        }
        return new StructDeclNode(nombre, campos,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitAtributoEstructura(YLanguageParser.AtributoEstructuraContext ctx) {
        String tipo = ctx.tipo().getText();
        String nombre = ctx.ID().getText();
        List<Integer> dimensiones = new ArrayList<>();
        if (ctx.ENTERO() != null) {
            for (var entero : ctx.ENTERO()) {
                dimensiones.add(Integer.parseInt(entero.getText()));
            }
        }
        return new FieldNode(tipo, nombre, dimensiones,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ================= FUNCIONES =================

    @Override
    public ASTNode visitDeclaracionFunciones(YLanguageParser.DeclaracionFuncionesContext ctx) {
        String nombre = ctx.ID().getText();
        List<ParamNode> parametros = ctx.parametros() != null
                ? convertirParametros(ctx.parametros())
                : new ArrayList<>();
        String tipoRetorno = ctx.tipo() != null ? ctx.tipo().getText() : null;
        List<Statement> cuerpo = convertirBloque(ctx.bloqueFuncion());

        return new FunctionDeclNode(nombre, parametros, tipoRetorno, cuerpo, false, null,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private List<ParamNode> convertirParametros(YLanguageParser.ParametrosContext ctx) {
        List<ParamNode> parametros = new ArrayList<>();
        if (ctx != null && ctx.parametro() != null) {
            for (YLanguageParser.ParametroContext p : ctx.parametro()) {
                ASTNode res = visit(p);
                if (res instanceof ParamNode param) {
                    parametros.add(param);
                }
            }
        }
        return parametros;
    }

    @Override
    public ASTNode visitParametroSimple(YLanguageParser.ParametroSimpleContext ctx) {
        return new ParamNode(ctx.tipo().getText(), ctx.ID().getText(), ParamNode.ModoPaso.VALOR,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitParametroArreglo(YLanguageParser.ParametroArregloContext ctx) {
        return new ParamNode(ctx.tipo().getText(), ctx.ID().getText(), ParamNode.ModoPaso.REFERENCIA_ARREGLO,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitParametroEstructura(YLanguageParser.ParametroEstructuraContext ctx) {
        String tipoEstructura = ctx.ID(0).getText();
        String nombreParametro = ctx.ID(1).getText();
        return new ParamNode(tipoEstructura, nombreParametro, ParamNode.ModoPaso.REFERENCIA_ESTRUCTURA,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private List<Statement> convertirBloque(YLanguageParser.BloqueFuncionContext ctx) {
        List<Statement> sentencias = new ArrayList<>();
        if (ctx == null || ctx.sentencia() == null) {
            return sentencias;
        }
        for (YLanguageParser.SentenciaContext s : ctx.sentencia()) {
            ASTNode nodo = visit(s);
            if (nodo instanceof Statement stmt) {
                sentencias.add(stmt);
            }
        }
        return sentencias;
    }

    // ================= SENTENCIAS =================

    @Override
    public ASTNode visitSentencia(YLanguageParser.SentenciaContext ctx) {
        if (ctx.declaracionVariableLocal() != null) return visit(ctx.declaracionVariableLocal());
        if (ctx.declaracionArrayLocal() != null) return visit(ctx.declaracionArrayLocal());
        if (ctx.asignacion() != null) return visit(ctx.asignacion());
        if (ctx.incremento() != null) return visit(ctx.incremento());
        if (ctx.entrada() != null) return visit(ctx.entrada());
        if (ctx.salida() != null) return visit(ctx.salida());
        if (ctx.condicional() != null) return visit(ctx.condicional());
        if (ctx.cicloWhile() != null) return visit(ctx.cicloWhile());
        if (ctx.cicloDo() != null) return visit(ctx.cicloDo());
        if (ctx.cicloFor() != null) return visit(ctx.cicloFor());
        if (ctx.interrupcion() != null) return visit(ctx.interrupcion());
        if (ctx.retorno() != null) return visit(ctx.retorno());
        if (ctx.llamadaFuncion() != null) {
            Expression llamada = (Expression) visit(ctx.llamadaFuncion());
            return new ExprStatementNode(llamada,
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        return null;
    }

    @Override
    public ASTNode visitDeclaracionVariableLocal(YLanguageParser.DeclaracionVariableLocalContext ctx) {
        String tipo = ctx.tipo().getText();
        String nombre = ctx.ID().getText();
        ASTNode init = ctx.valorInicial() != null ? visit(ctx.valorInicial()) : null;
        return new VarDeclNode(tipo, nombre, null, init,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitDeclaracionArrayLocal(YLanguageParser.DeclaracionArrayLocalContext ctx) {
        String tipo = ctx.tipo().getText();
        String nombre = ctx.ID().getText();
        List<Integer> dimensiones = new ArrayList<>();
        for (YLanguageParser.ExpresionContext dimCtx : ctx.expresion()) {
            dimensiones.add(intentarLeerEntero(dimCtx));
        }
        ASTNode init = ctx.inicializacionEstructura() != null ? visit(ctx.inicializacionEstructura()) : null;
        return new VarDeclNode(tipo, nombre, dimensiones, init,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private int intentarLeerEntero(YLanguageParser.ExpresionContext ctx) {
        try {
            return Integer.parseInt(ctx.getText());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public ASTNode visitValorInicial(YLanguageParser.ValorInicialContext ctx) {
        if (ctx.expresion() != null) return visit(ctx.expresion());
        return visit(ctx.inicializacionEstructura());
    }

    @Override
    public ASTNode visitInicializacionEstructura(YLanguageParser.InicializacionEstructuraContext ctx) {
        return new ArrayLiteralNode(convertirListaValores(ctx.listaValores()),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private List<Expression> convertirListaValores(YLanguageParser.ListaValoresContext ctx) {
        List<Expression> valores = new ArrayList<>();
        if (ctx != null && ctx.expresion() != null) {
            for (YLanguageParser.ExpresionContext e : ctx.expresion()) {
                ASTNode res = visit(e);
                if (res instanceof Expression expr) {
                    valores.add(expr);
                }
            }
        }
        return valores;
    }

    @Override
    public ASTNode visitAsignacion(YLanguageParser.AsignacionContext ctx) {
        Expression destino = (Expression) visit(ctx.acceso());
        Expression valor = (Expression) visit(ctx.expresion());
        return new AssignNode(destino, "=", valor,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitAcceso(YLanguageParser.AccesoContext ctx) {
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();

        String nombreBase = ctx.ID(0).getText();
        Expression base = new IdentifierNode(nombreBase, line, column);

        List<Suffix> sufijos = new ArrayList<>();
        int i = 1;
        while (i < ctx.getChildCount()) {
            String textoActual = ctx.getChild(i).getText();

            if (textoActual.equals("\n") || textoActual.equals("\r") || textoActual.trim().isEmpty()) {
                i++;
                continue;
            }

            if (textoActual.equals(".")) {
                String campo = ctx.getChild(i + 1).getText();
                sufijos.add(new FieldSuffix(campo));
                i += 2;
            } else if (textoActual.equals("[")) {
                ParseTree child = ctx.getChild(i + 1);
                if (child instanceof YLanguageParser.ExpresionContext) {
                    sufijos.add(new IndexSuffix((Expression) visit(child)));
                }
                i += 3;
            } else {
                i++;
            }
        }

        if (sufijos.isEmpty()) return base;
        return new PostfixExprNode(base, sufijos, line, column);
    }

    @Override
    public ASTNode visitIncremento(YLanguageParser.IncrementoContext ctx) {
        String nombre = ctx.ID().getText();
        String operador = ctx.getChild(1).getText();
        return new IncrDecrNode(nombre, operador,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitEntrada(YLanguageParser.EntradaContext ctx) {
        return new ReadNode(null, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitSalida(YLanguageParser.SalidaContext ctx) {
        Expression argumento = (Expression) visit(ctx.expresion());
        return new PrintNode(List.of(argumento),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ---------------- Condicionales ----------------

    @Override
    public ASTNode visitCondicional(YLanguageParser.CondicionalContext ctx) {
        if (ctx.condicionalElegir() != null) {
            return visit(ctx.condicionalElegir());
        }

        List<YLanguageParser.ExpresionContext> condiciones = ctx.expresion();
        List<YLanguageParser.BloqueFuncionContext> bloques = ctx.bloqueFuncion();
        int numRamas = condiciones.size();

        List<IfNode.Branch> ramas = new ArrayList<>();
        for (int i = 0; i < numRamas; i++) {
            Expression cond = (Expression) visit(condiciones.get(i));
            List<Statement> cuerpo = convertirBloque(bloques.get(i));
            ramas.add(new IfNode.Branch(cond, cuerpo));
        }

        List<Statement> ramaContrario = null;
        if (ctx.CONTRARIO() != null && bloques.size() > numRamas) {
            ramaContrario = convertirBloque(bloques.get(numRamas));
        }

        return new IfNode(ramas, ramaContrario,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCondicionalElegir(YLanguageParser.CondicionalElegirContext ctx) {
        Expression selector = (Expression) visit(ctx.expresion());
        List<CaseNode> casos = new ArrayList<>();
        if (ctx.casoElegir() != null) {
            for (YLanguageParser.CasoElegirContext c : ctx.casoElegir()) {
                ASTNode res = visit(c);
                if (res instanceof CaseNode caso) {
                    casos.add(caso);
                }
            }
        }
        if (ctx.casoDefault() != null) {
            ASTNode resDefault = visit(ctx.casoDefault());
            if (resDefault instanceof CaseNode casoDef) {
                casos.add(casoDef);
            }
        }
        return new SwitchNode(selector, casos,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCasoElegir(YLanguageParser.CasoElegirContext ctx) {
        String valor = ctx.ENTERO() != null ? ctx.ENTERO().getText() : ctx.ID().getText();
        List<Statement> cuerpo = convertirBloque(ctx.bloqueFuncion());
        return new CaseNode(valor, false, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCasoDefault(YLanguageParser.CasoDefaultContext ctx) {
        List<Statement> cuerpo = convertirBloque(ctx.bloqueFuncion());
        return new CaseNode(null, true, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // ---------------- Ciclos ----------------

    @Override
    public ASTNode visitCicloWhile(YLanguageParser.CicloWhileContext ctx) {
        Expression condicion = (Expression) visit(ctx.expresion());
        List<Statement> cuerpo = convertirBloque(ctx.bloqueFuncion());
        return new WhileNode(condicion, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCicloDo(YLanguageParser.CicloDoContext ctx) {
        List<Statement> cuerpo = convertirBloque(ctx.bloqueFuncion());
        Expression condicion = (Expression) visit(ctx.expresion());
        return new DoWhileNode(cuerpo, condicion,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitCicloFor(YLanguageParser.CicloForContext ctx) {
        Statement inicializacion = null;
        if (ctx.declaracionVariableLocal() != null) {
            inicializacion = (Statement) visit(ctx.declaracionVariableLocal());
        } else if (ctx.asignacion() != null && !ctx.asignacion().isEmpty()) {
            inicializacion = (Statement) visit(ctx.asignacion(0));
        }

        Expression condicion = ctx.expresion() != null ? (Expression) visit(ctx.expresion()) : null;

        Statement actualizacion = null;
        if (ctx.incremento() != null) {
            actualizacion = (Statement) visit(ctx.incremento());
        } else if (ctx.asignacion() != null && ctx.asignacion().size() > 1) {
            actualizacion = (Statement) visit(ctx.asignacion(1));
        } else if (ctx.asignacion() != null && ctx.asignacion().size() == 1 && ctx.declaracionVariableLocal() != null) {
            actualizacion = (Statement) visit(ctx.asignacion(0));
        }

        List<Statement> cuerpo = convertirBloque(ctx.bloqueFuncion());

        return new ForNode(inicializacion, condicion, actualizacion, cuerpo,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitInterrupcion(YLanguageParser.InterrupcionContext ctx) {
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        if (ctx.ROMPER() != null) return new BreakNode(line, column);
        return new ContinueNode(line, column);
    }

    @Override
    public ASTNode visitRetorno(YLanguageParser.RetornoContext ctx) {
        Expression valor = ctx.expresion() != null ? (Expression) visit(ctx.expresion()) : null;
        return new ReturnNode(valor, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitLlamadaFuncion(YLanguageParser.LlamadaFuncionContext ctx) {
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();

        Expression base = new IdentifierNode(ctx.ID().getText(), line, column);
        List<Expression> argumentos = ctx.argumentos() != null
                ? convertirArgumentos(ctx.argumentos())
                : new ArrayList<>();

        List<Suffix> sufijos = new ArrayList<>();
        sufijos.add(new CallSuffix(argumentos));
        return new PostfixExprNode(base, sufijos, line, column);
    }

    private List<Expression> convertirArgumentos(YLanguageParser.ArgumentosContext ctx) {
        List<Expression> argumentos = new ArrayList<>();
        if (ctx != null && ctx.expresion() != null) {
            for (YLanguageParser.ExpresionContext e : ctx.expresion()) {
                ASTNode res = visit(e);
                if (res instanceof Expression expr) {
                    argumentos.add(expr);
                }
            }
        }
        return argumentos;
    }

    // ================= EXPRESIONES =================

    @Override
    public ASTNode visitExprParentesis(YLanguageParser.ExprParentesisContext ctx) {
        return visit(ctx.expresion());
    }

    @Override
    public ASTNode visitExprNegacion(YLanguageParser.ExprNegacionContext ctx) {
        Expression operando = (Expression) visit(ctx.expresion());
        return new UnaryExprNode("!", operando,
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprAcceso(YLanguageParser.ExprAccesoContext ctx) {
        return visit(ctx.acceso());
    }

    @Override
    public ASTNode visitExprLlamadaFuncion(YLanguageParser.ExprLlamadaFuncionContext ctx) {
        return visit(ctx.llamadaFuncion());
    }

    @Override
    public ASTNode visitExprEntrada(YLanguageParser.ExprEntradaContext ctx) {
        return visit(ctx.entrada());
    }

    @Override
    public ASTNode visitExprLiteral(YLanguageParser.ExprLiteralContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public ASTNode visitExprMultiplicativo(YLanguageParser.ExprMultiplicativoContext ctx) {
        return new BinaryExprNode(ctx.op.getText(),
                (Expression) visit(ctx.expresion(0)), (Expression) visit(ctx.expresion(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprAditivo(YLanguageParser.ExprAditivoContext ctx) {
        return new BinaryExprNode(ctx.op.getText(),
                (Expression) visit(ctx.expresion(0)), (Expression) visit(ctx.expresion(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprRelacional(YLanguageParser.ExprRelacionalContext ctx) {
        return new BinaryExprNode(ctx.op.getText(),
                (Expression) visit(ctx.expresion(0)), (Expression) visit(ctx.expresion(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitExprLogico(YLanguageParser.ExprLogicoContext ctx) {
        return new BinaryExprNode(ctx.op.getText(),
                (Expression) visit(ctx.expresion(0)), (Expression) visit(ctx.expresion(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ASTNode visitLiteral(YLanguageParser.LiteralContext ctx) {
        return new LiteralNode(ctx.getText(),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
}