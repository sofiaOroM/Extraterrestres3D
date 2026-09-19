package sofiaO.ast;

import sofiaO.util.Type;

/**
 * Implementación base de ASTNode: guarda línea/columna
 * para que SemanticAnalyzer pueda reportar errores con ubicación
 * exacta sin que cada clase de nodo repita el mismo boilerplate.
 */
public abstract class BaseNode implements ASTNode {
    protected final int line;
    protected final int column;

    public Type tipoResuelto = Type.ERROR;
    public String tipoUsuarioResuelto = null;

    protected BaseNode(int line, int column) {
        this.line = line;
        this.column = column;
    }

    @Override
    public int getLine() { return line; }

    @Override
    public int getColumn() { return column; }
}
