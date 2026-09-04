package sofiaO.ast;

import sofiaO.ast.declaracion.*;
import sofiaO.ast.expresion.*;

public interface ASTVisitor<T> {
    /*T visit(ProgramNode n);
    T visit(ImportNode n);
    T visit(StructDeclNode n);
    T visit(ClassDeclNode n);
    T visit(FunctionDeclNode n);
    T visit(ConstructorDeclNode n);*/
    T visit(VarDeclNode n);
    /*T visit(AssignNode n);
    T visit(IfNode n);
    T visit(SwitchNode n);
    T visit(WhileNode n);
    T visit(DoWhileNode n);
    T visit(ForNode n);
    T visit(BreakNode n);
    T visit(ContinueNode n);
    T visit(ReturnNode n);
    T visit(PrintNode n);
    T visit(ReadNode n);*/
    T visit(BinaryExprNode n);
    /*T visit(UnaryExprNode n);
    T visit(TernaryExprNode n);*/
    T visit(LiteralNode n);
   // T visit(PostfixExprNode n);      // ver sección 4
    T visit(IdentifierNode n);
    /*T visit(NewObjectNode n);
    T visit(ArrayLiteralNode n);*/
}
