package sofiaO.Traductor;

import sofiaO.util.Type;
import sofiaO.util.TypeMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Una cuarteta de código de tres direcciones.
 *
 * Formato clásico: (operador, argumento1, argumento2, resultado)
 *
 * Se agrega 'extraArgs' porque hay instrucciones del propio enunciado que
 * son VARIÁDICAS y no caben en 4 campos fijos:
 *   - "t2 = call verificar_semantica, t0, t1"   (N argumentos)
 *   - ">> mi_string >> mi_textum;"              (N cosas a imprimir)
 *   - "{10, 20, 30, 40, 50}"                    (N valores de un arreglo)
 */
public class Quadruple {

    public String op;               // "=", "+", "call", "if_false", "goto", "label", "print", "halt"...
    public String arg1;
    public String arg2;
    public String result;
    public List<String> extraArgs;  // solo para call/print/newarray; vacío en el resto

    /** Tipo del valor que queda en 'result'. VOID/ERROR si no aplica. */
    public Type tipoResultado;

    /** Tipos de cada elemento de extraArgs (para print con varios argumentos). */
    public List<Type> tiposExtra;

    public Quadruple(String op, String arg1, String arg2, String result, Type tipoResultado) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.result = result;
        this.tipoResultado = tipoResultado == null ? Type.ERROR : tipoResultado;
        this.extraArgs = new ArrayList<>();
        this.tiposExtra = new ArrayList<>();
    }

    public Quadruple(String op, String arg1, String arg2, String result,
                     Type tipoResultado, List<String> extraArgs, List<Type> tiposExtra) {
        this(op, arg1, arg2, result, tipoResultado);
        if (extraArgs != null) this.extraArgs = extraArgs;
        if (tiposExtra != null) this.tiposExtra = tiposExtra;
    }

    /**
     * Representación legible de la cuarteta, en el mismo estilo del ejemplo
     * oficial del enunciado. Sirve para IMPRIMIR las cuartetas en la interfaz
     * y para el reporte de documentación técnica.
     */
    @Override
    public String toString() {
        switch (op) {
            case "label":    return result + ":";
            case "goto":     return "goto " + result;
            case "if_false": return "if_false " + arg1 + " goto " + result;
            case "if_true":  return "if_true " + arg1 + " goto " + result;
            case "=":        return result + " = " + arg1;
            case "call":     return (result != null ? result + " = " : "")
                    + "call " + arg1
                    + (extraArgs.isEmpty() ? "" : ", " + String.join(", ", extraArgs));
            case "print":    return "print " + String.join(", ", extraArgs);
            case "read":     return result + " = read";
            case "return":   return "return" + (arg1 != null ? " " + arg1 : "");
            case "halt":     return "halt";
            case "func":     return "func " + result + ":";
            case "endfunc":  return "endfunc " + result;
            case "param":    return "param " + arg1;
            case "new":      return result + " = new " + arg1
                    + (extraArgs.isEmpty() ? "" : ", " + String.join(", ", extraArgs));
            case "newarray": return result + " = newarray " + arg1;
            case "getfield": return result + " = " + arg1 + "." + arg2;
            case "setfield": return arg1 + "." + arg2 + " = " + result;
            case "getindex": return result + " = " + arg1 + "[" + arg2 + "]";
            case "setindex": return arg1 + "[" + arg2 + "] = " + result;
            case "declare":
                String tipoStr = tipoResultado.toString();
                if (tipoResultado == Type.ESTRUCTURA || tipoResultado == Type.CLASE) {
                    tipoStr = TypeMapper.nombreReal(result); // usa el nombre real
                }
                return "declare " + tipoStr + " " + result
                        + (arg1 != null ? "[" + arg1 + "]" : "");

            default:
                if (arg2 == null) return result + " = " + op + " " + arg1;
                return result + " = " + arg1 + " " + op + " " + arg2;
        }
    }
}
