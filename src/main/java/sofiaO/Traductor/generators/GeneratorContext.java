package sofiaO.Traductor.generators;

import sofiaO.ast.ASTNode;
import sofiaO.ast.Expression;
import sofiaO.ast.declaracion.FunctionDeclNode;
import sofiaO.ast.expresion.Suffix;
import sofiaO.semantico.ProgramContext;
import sofiaO.semantico.ResolvedType;
import sofiaO.util.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Todo lo que un *Generador colaborador (Declaracion/Sentencia/Expresion) necesita
 * de QuadrupleGenerator: emitir cuartetas, pedir temporales/etiquetas, consultar
 * tipos y recursar en el AST. QuadrupleGenerator es quien implementa esta interfaz
 * y quien guarda el estado real; los colaboradores no guardan nada por su cuenta.
 */
public interface GeneratorContext {

    // ---- Emisión de cuartetas ----
    void emit(String op, String arg1, String arg2, String result, Type tipo);
    void emitVariadic(String op, String arg1, String result, Type tipo, List<String> extra, List<Type> tiposExtra);
    void emitLabel(String etiqueta);
    String newTemp(Type tipo);
    /** Como newTemp, pero además recuerda que ese temporal es de un tipo de usuario (Nodo, Persona...). */
    String newTempOf(Type tipo, String tipoUsuario);
    String newLabel();

    // ---- Recursión en el AST (equivalente a "nodo.accept(this)") ----
    String generate(ASTNode n);

    // ---- Tipos ----
    Type typeOf(ASTNode n);
    Type typeOfPlace(String lugar);
    /** Tipo de una variable o atributo (los atributos no viven en placeTypes()). */
    Type typeOfVariable(String nombre);
    /** Nombre del tipo de usuario (Nodo, Persona...) del valor que vive en 'lugar', si se conoce. */
    String userTypeOfBase(Expression base, String lugar);
    ResolvedType typeOfField(String userType, String field);
    ResolvedType resolveTypeText(String typeText);
    FunctionDeclNode findMethod(String className, String name);

    // ---- Arreglos ----
    Map<String, List<Integer>> arrayDimensions();
    int countConsecutiveIndices(List<Suffix> suffixes, int from);
    String flattenIndices(String arrayName, List<String> indices);

    // ---- Concatenación de cadenas ('+' cuando el resultado es CADENA) ----
    /** Tipo real de un operando ya generado (usa el del AST, y si no, el del lugar). */
    Type typeOfOperand(Expression e, String lugar);
    /** Devuelve un lugar que contiene el valor como cadena (convierte con int_to_str, etc. si hace falta). */
    String asString(Type type, String lugar);

    // ---- Ciclos (para 'romper' / 'continuar') ----
    void startLoop(String continueLabel, String breakLabel);
    void endLoop();
    LoopLabels currentLoop();

    // ---- Contexto de clase / método actual ----
    ProgramContext context();
    String currentClass();
    Set<String> currentClassMethods();
    Set<String> currentClassFields();
    Set<String> currentMethodLocals();
    void enterClass(String name, Set<String> fields, Set<String> methods);
    void exitClass();
    void startLocals(Set<String> parameterNames);

    // ---- Mapas compartidos con CEmitter (se exponen tal cual, mutables) ----
    Map<String, Type> placeTypes();
    Map<String, String> placeUserTypes();
}