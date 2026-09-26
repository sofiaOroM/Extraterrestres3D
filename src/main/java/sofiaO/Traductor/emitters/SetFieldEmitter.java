package sofiaO.Traductor.emitters;

import sofiaO.Traductor.Quadruple;
import sofiaO.semantico.ResolvedType;
import static sofiaO.util.Type.CADENA;

import java.util.Map;

/**
 * obj.campo = valor   ==>   obj->campo = valor;
 * Caso especial: si el campo es de tipo CADENA, el campo en C es un
 * char[256] (no un puntero), así que no se puede usar '=' y hay que copiar
 * con strcpy.
 */
public class SetFieldEmitter implements InstructionEmitter {
    @Override public String operacion() { return "setfield"; }

    @Override
    public String emitir(Quadruple q, EmissionContext ctx) {
        String tipoStruct = ctx.tipoUsuarioDe(q.arg1);
        Map<String, ResolvedType> campos = tipoStruct == null ? null : ctx.camposDe(tipoStruct);
        boolean esCadena = campos != null && ResolvedType.primitivo(CADENA).equals(campos.get(q.arg2));
        String destino = q.arg1 + "->" + q.arg2;
        return esCadena
                ? "    strcpy(" + destino + ", " + q.result + ");\n"
                : "    " + destino + " = " + q.result + ";\n";
    }
}