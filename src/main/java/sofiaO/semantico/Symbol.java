package sofiaO.semantico;

import sofiaO.util.Type;

public class Symbol {
    public String nombre;
    public Type tipo;
    public int line;
    public int column;

    public Symbol(String nombre, Type tipo, int line, int column) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.line = line;
        this.column = column;
    }
}