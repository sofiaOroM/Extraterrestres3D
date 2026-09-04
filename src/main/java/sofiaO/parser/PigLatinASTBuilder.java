package sofiaO.parser;

import sofiaO.ast.ASTNode;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.expresion.*;

public class PigLatinASTBuilder extends PigLatinLanguageBaseVisitor<ASTNode> {

    @Override
    public ASTNode visitDeclaracionVariable(PigLatinLanguageParser.DeclaracionVariableContext ctx) {
        // ESTO ID ':' tipo valor? ';'   (rama sin verum/falsus)
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        String tipo = ctx.tipo().getText();             // "numerus"
        String nombre = ctx.ID().getText();              // "total"
        ASTNode init = null;
        if (ctx.valor() != null && ctx.valor().expresion() != null) {
            init = visit(ctx.valor().expresion());
        }
        return new VarDeclNode(line, column, tipo, nombre, init);      // MISMO nodo otra vez
    }

    @Override
    public ASTNode visitExprMultiplicativo(PigLatinLanguageParser.ExprMultiplicativoContext ctx) {
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        return new BinaryExprNode(line, column,ctx.op.getText(), visit(ctx.expresion(0)), visit(ctx.expresion(1)));
    }

    @Override
    public ASTNode visitExprLiteral(PigLatinLanguageParser.ExprLiteralContext ctx) {
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        return new LiteralNode(line, column,ctx.literal().getText());
    }

    @Override
    public ASTNode visitExprAcceso(PigLatinLanguageParser.ExprAccesoContext ctx) {
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        return new IdentifierNode(line, column,ctx.acceso().getText());
    }
}