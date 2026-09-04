package sofiaO.semantico;

import sofiaO.util.Type;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    private final Map<String, Symbol> tablaActual = new HashMap<>();
    private final SymbolTable scopePadre;

    public SymbolTable() {
        this.scopePadre = null; // Scope global
    }

    public SymbolTable(SymbolTable scopePadre) {
        this.scopePadre = scopePadre;
    }

    // Verifica si la variable ya existe EN EL AMBIENTE ACTUAL (evita re-declaraciones)
    public boolean existeEnScopeActual(String nombre) {
        return tablaActual.containsKey(nombre);
    }

    // Registra una nueva variable
    public void declarar(String nombre, Type tipo, int line, int column) {
        tablaActual.put(nombre, new Symbol(nombre, tipo, line, column));
    }

    // Busca una variable desde el scope actual subiendo hasta el global
    public Symbol resolver(String nombre) {
        if (tablaActual.containsKey(nombre)) {
            return tablaActual.get(nombre);
        }
        if (scopePadre != null) {
            return scopePadre.resolver(nombre);
        }
        return null; // No encontrada
    }

    public SymbolTable getScopePadre() {
        return scopePadre;
    }
}