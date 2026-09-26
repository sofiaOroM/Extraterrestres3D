package sofiaO.Traductor.emitters;

import java.util.HashMap;
import java.util.Map;

/**
 * Junta todos los InstructionEmitter disponibles y se los sirve a CEmitter
 * por nombre de operación. Agregar una instrucción nueva de C3D es: escribir
 * la clase del emisor y registrarla aquí.
 */
public class EmitterRegistry {
    private final Map<String, InstructionEmitter> emisores = new HashMap<>();

    public EmitterRegistry() {
        registrar(new ConcatEmitter());
        registrar(new SetFieldEmitter());
        registrar(new StringConversionEmitter("int_to_str"));
        registrar(new StringConversionEmitter("double_to_str"));
        registrar(new StringConversionEmitter("char_to_str"));
        registrar(new StringConversionEmitter("bool_to_str"));
    }

    private void registrar(InstructionEmitter emisor) {
        emisores.put(emisor.operacion(), emisor);
    }

    public InstructionEmitter buscar(String operacion) {
        return emisores.get(operacion);
    }
}