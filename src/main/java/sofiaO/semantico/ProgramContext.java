package sofiaO.semantico;

import sofiaO.ast.declaracion.ClassDeclNode;
import sofiaO.ast.declaracion.ConstructorDeclNode;
import sofiaO.ast.declaracion.FunctionDeclNode;
import sofiaO.ast.declaracion.StructDeclNode;
import sofiaO.util.Type;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Todo lo que un programa conoce "globalmente" a través de sus imports:
 * qué estructuras (.y), qué clases (.z) y qué funciones libres (.y) existen,
 * más el "layout" ya calculado de cada una (nombre de campo -> tipo resuelto).
 *
 * Esta es la pieza que le faltaba a SemanticAnalyzer para resolver p1.promedio,
 * miObjeto.hablar(...), new Persona(...), etc.
 */
public class ProgramContext {

    public final Map<String, StructDeclNode> structs = new HashMap<>();
    public final Map<String, ClassDeclNode> classes = new HashMap<>();
    public final Map<String, FunctionDeclNode> funciones = new HashMap<>();

    /** nombreEstructura -> (nombreCampo -> tipo resuelto del campo) */
    public final Map<String, Map<String, ResolvedType>> layoutsEstructuras = new HashMap<>();

    /** nombreClase -> (nombreAtributo -> tipo resuelto del atributo) */
    public final Map<String, Map<String, ResolvedType>> layoutsClases = new HashMap<>();

    /** nombreClase -> (nombreMetodo -> declaración del método) */
    public final Map<String, Map<String, FunctionDeclNode>> metodosPorClase = new HashMap<>();

    /** nombreClase -> lista de constructores (puede haber varios por sobrecarga) */
    public final Map<String, List<ConstructorDeclNode>> constructoresPorClase = new HashMap<>();

    public Map<String, Map<String, Type>> obtenerLayoutsParaCEmitter() {
        Map<String, Map<String, Type>> resultado = new LinkedHashMap<>();

        if (this.layoutsEstructuras != null) {
            for (var entry : this.layoutsEstructuras.entrySet()) {
                Map<String, Type> campos = new LinkedHashMap<>();
                entry.getValue().forEach((campo, resType) -> campos.put(campo, resType.tipo()));
                resultado.put(entry.getKey(), campos);
            }
        }

        if (this.layoutsClases != null) {
            for (var entry : this.layoutsClases.entrySet()) {
                Map<String, Type> campos = new LinkedHashMap<>();
                entry.getValue().forEach((campo, resType) -> campos.put(campo, resType.tipo()));
                resultado.put(entry.getKey(), campos);
            }
        }

        return resultado;
    }
}
