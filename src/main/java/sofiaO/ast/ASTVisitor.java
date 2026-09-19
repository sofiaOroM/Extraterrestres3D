package sofiaO.ast;

import sofiaO.ast.declaracion.*;
import sofiaO.ast.sentencia.*;
import sofiaO.ast.expresion.*;
import sofiaO.semantico.ResolvedType;
import sofiaO.util.Type;

/**
 * Un solo visitor compartido entre los 3 lenguajes. Lo implementan:
 *  - SemanticAnalyzer implements ASTVisitor<Type>   (cada nodo resuelve su tipo)
 */
public interface ASTVisitor<T> {

    // ---- Declaraciones ----
    T visit(ProgramNode n);
    T visit(ImportNode n);
    T visit(StructDeclNode n);
    T visit(ClassDeclNode n);
    T visit(FunctionDeclNode n);
    T visit(ConstructorDeclNode n);
    T visit(FieldNode n);
    T visit(ParamNode n);
    T visit(VarDeclNode n);

    // ---- Sentencias ----
    T visit(AssignNode n);
    T visit(IncrDecrNode n);
    T visit(IfNode n);
    T visit(SwitchNode n);
    T visit(CaseNode n);
    T visit(WhileNode n);
    T visit(DoWhileNode n);
    T visit(ForNode n);
    T visit(BreakNode n);
    T visit(ContinueNode n);
    T visit(ReturnNode n);
    T visit(PrintNode n);
    T visit(ReadNode n);
    T visit(ExprStatementNode n);

    // ---- Expresiones ----
    T visit(BinaryExprNode n);
    T visit(UnaryExprNode n);
    T visit(TernaryExprNode n);
    T visit(LiteralNode n);
    T visit(IdentifierNode n);
    T visit(PostfixExprNode n);
    T visit(NewObjectNode n);
    T visit(ArrayLiteralNode n);
}
