package sofiaO.Traductor.emitters;

import sofiaO.Traductor.Quadruple;

/**
 * t = int_to_str v   ==>   t = c3d_int_to_str(v);
 * Un solo emisor atiende las 4 conversiones (int/double/char/bool a cadena),
 * porque las 4 tienen exactamente la misma forma: solo cambia el nombre de la
 * función de runtime, y ese nombre es igual a q.op con el prefijo "c3d_".
 */
public class StringConversionEmitter implements InstructionEmitter {
    private final String operacion;

    public StringConversionEmitter(String operacion) { this.operacion = operacion; }

    @Override public String operacion() { return operacion; }

    @Override
    public String emitir(Quadruple q, EmissionContext ctx) {
        return "    " + q.result + " = c3d_" + operacion + "(" + ctx.resolverAtributo(q.arg1) + ");\n";
    }
}