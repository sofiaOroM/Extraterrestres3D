package sofiaO.Traductor.emitters;

import sofiaO.Traductor.Quadruple;

/** t = concat a, b   ==>   t = c3d_concat(a, b);  */
public class ConcatEmitter implements InstructionEmitter {
    @Override public String operacion() { return "concat"; }

    @Override
    public String emitir(Quadruple q, EmissionContext ctx) {
        return "    " + q.result + " = c3d_concat("
                + ctx.resolverAtributo(q.arg1) + ", " + ctx.resolverAtributo(q.arg2) + ");\n";
    }
}