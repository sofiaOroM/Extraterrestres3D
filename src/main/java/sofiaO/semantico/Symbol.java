package sofiaO.semantico;

import sofiaO.util.Type;

public class Symbol {
    public String nombre;
    public Type tipo;
    public String tipoUsuario;

    public int line;
    public int column;

    public Symbol(String nombre, Type tipo) {
        this(nombre, tipo, null, 0, 0);
    }

    public Symbol(String nombre, Type tipo, String tipoUsuario, int line, int column) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.tipoUsuario = tipoUsuario;
        this.line = line;
        this.column = column;
    }
}