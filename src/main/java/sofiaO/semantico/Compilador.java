package sofiaO.semantico;

import sofiaO.ast.declaracion.ProgramNode;

/**
 * Orquesta la compilación de los 3 archivos: primero se cargan los que
 * declaran cosas (.y con structs/funciones, .z con clases), registrándolas
 * en un ProgramContext compartido; DESPUÉS se compila el .pig principal,
 * que puede referenciar todo lo ya cargado a través de sus imports.
 *
 * El orden de llamadas SÍ importa:
 *   1) cargarEstructurasYFunciones(astDelY)   -- una vez por cada .y
 *   2) cargarClase(astDelZ)                    -- una vez por cada .z
 *   3) compilarPrincipal(astDelPig)             -- al final, una sola vez
 */
public class Compilador {

    public final ProgramContext contexto = new ProgramContext();
    public final SymbolTable scopeGlobal = new SymbolTable();
    private final SemanticAnalyzer analizador;

    public Compilador() {
        this.analizador = new SemanticAnalyzer(scopeGlobal, contexto);
    }

    /** Registra structs y funciones libres de un archivo .y. */
    public void cargarEstructurasYFunciones(ProgramNode astArchivoY) {
        analizador.visit(astArchivoY);
    }

    /** Registra la clase de un archivo .z. */
    public void cargarClase(ProgramNode astArchivoZ) {
        analizador.visit(astArchivoZ);
    }

    /** Valida el programa principal (.pig) contra todo lo ya cargado. */
    public void compilarPrincipal(ProgramNode astPrincipal) {
        analizador.visit(astPrincipal);
    }

    public SemanticAnalyzer getAnalizador() { return analizador; }

}