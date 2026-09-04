package sofiaO.parser;

import sofiaO.ast.ASTNode;
import sofiaO.ast.declaracion.*;
import sofiaO.ast.expresion.*;

public class YLanguageASTBuilder extends YLanguageBaseVisitor<ASTNode> {

    @Override
    public ASTNode visitDeclaracionVariableLocal(YLanguageParser.DeclaracionVariableLocalContext ctx) {
        int line = ctx.start.getLine();
        int column = ctx.start.getCharPositionInLine();
        String tipo = ctx.tipo().getText();          // "entero"
        String nombre = ctx.ID().getText();           // "edadUsuario"
        ASTNode init = null;
        if (ctx.valorInicial() != null) {
            init = visit(ctx.valorInicial().expresion());
        }
        return new VarDeclNode(line, column, tipo, nombre, init);
    }

    @Override
    public ASTNode visitExprAditivo(YLanguageParser.ExprAditivoContext ctx) {
        return new BinaryExprNode(ctx.expresion(0).getStart().getLine(), ctx.expresion(0).start.getCharPositionInLine(), ctx.op.getText(), visit(ctx.expresion(0)), visit(ctx.expresion(1)));
    }

    @Override
    public ASTNode visitExprMultiplicativo(YLanguageParser.ExprMultiplicativoContext ctx) {
        return new BinaryExprNode(ctx.expresion(0).getStart().getLine(), ctx.expresion(0).start.getCharPositionInLine(), ctx.op.getText(), visit(ctx.expresion(0)), visit(ctx.expresion(1)));
    }

    @Override
    public ASTNode visitExprLiteral(YLanguageParser.ExprLiteralContext ctx) {
        return new LiteralNode(ctx.literal().getStart().getLine(), ctx.literal().start.getCharPositionInLine(),ctx.literal().getText());
    }

    @Override
    public ASTNode visitExprAcceso(YLanguageParser.ExprAccesoContext ctx) {
        return new IdentifierNode(ctx.acceso().getStart().getLine(), ctx.acceso().start.getCharPositionInLine(),ctx.acceso().getText());
    }
}
