package sofiaO.ast.expresion;

import sofiaO.ast.Expression;

import java.util.List;

/** (args)  llamada a función libre o a método sobre el resultado del sufijo anterior */
public class CallSuffix implements Suffix {
    public List<Expression> argumentos;
    public CallSuffix(List<Expression> argumentos) { this.argumentos = argumentos; }
}
