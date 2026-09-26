package sofiaO.Traductor.emitters;

import sofiaO.Traductor.Quadruple;

/**
 * Una instrucción de cuarteta (un valor de Quadruple.op) sabe traducirse
 * a sí misma a una línea de C. Cada operación del C3D es una clase aparte,
 */
public interface InstructionEmitter {
    /** El texto de Quadruple.op que este emisor sabe traducir */
    String operacion();

    /** Traduce una cuarteta a su línea de C */
    String emitir(Quadruple q, EmissionContext ctx);
}