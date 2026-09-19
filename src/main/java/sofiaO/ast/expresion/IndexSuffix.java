package sofiaO.ast.expresion;

import sofiaO.ast.Expression;

/** [indice]  (ej. numeros[0], matriz[i][j] -> dos IndexSuffix encadenados) */
public class IndexSuffix implements Suffix {
    public Expression indice;
    public IndexSuffix(Expression indice) { this.indice = indice; }
}
