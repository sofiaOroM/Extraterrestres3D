package sofiaO.parser;

import sofiaO.ast.ASTNode;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.expresion.*;

public class ZetarianoASTBuilder extends ZetarianoLanguageBaseVisitor<ASTNode> {

    @Override
    public ASTNode visitDeclaracionVariable(ZetarianoLanguageParser.DeclaracionVariableContext ctx) {
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        String tipo = ctx.tipo().getText();            // "int"
        String nombre = ctx.ID().getText();             // "total"
        ASTNode init = null;
        if (ctx.valorInicial() != null && ctx.valorInicial().expresion() != null) {
            init = visit(ctx.valorInicial().expresion());
        }
        return new VarDeclNode(line, column, tipo, nombre, init);     // MISMO nodo que Y?
    }

    // expresionAditiva / expresionMultiplicativa de Zetariano son left-recursive
    // por repetición (a*(op b)*), no por alternativas etiquetadas como en Y?;
    // se recorren con un fold en vez de un solo visitXxx:
    @Override
    public ASTNode visitExpresionMultiplicativa(ZetarianoLanguageParser.ExpresionMultiplicativaContext ctx) {
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        ASTNode resultado = visit(ctx.expresionUnaria(0));
        for (int i = 1; i < ctx.expresionUnaria().size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText(); // el token de operador entre operandos
            resultado = new BinaryExprNode(line, column, op, resultado, visit(ctx.expresionUnaria(i)));
        }
        return resultado;
    }

    @Override
    public ASTNode visitExprLiteral(ZetarianoLanguageParser.ExprLiteralContext ctx) {
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        return new LiteralNode(line, column, ctx.literal().getText());
    }

    @Override
    public ASTNode visitExprAcceso(ZetarianoLanguageParser.ExprAccesoContext ctx) {
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        return new IdentifierNode(line, column, ctx.acceso().getText());
    }
}
