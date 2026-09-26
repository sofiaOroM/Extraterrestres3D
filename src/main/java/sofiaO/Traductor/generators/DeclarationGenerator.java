package sofiaO.Traductor.generators;

import sofiaO.ast.ASTNode;
import sofiaO.ast.Statement;
import sofiaO.ast.declaracion.*;
import sofiaO.util.Type;
import sofiaO.util.TypeMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Genera cuartetas para todo lo que declara algo: programa, clases, funciones, variables. */
public class DeclarationGenerator {

    private final GeneratorContext ctx;

    public DeclarationGenerator(GeneratorContext ctx) {
        this.ctx = ctx;
    }

    public String visit(ProgramNode n) {
        for (var d : n.declaraciones) ctx.generate(d);
        ctx.emit("halt", null, null, null, Type.VOID);
        return null;
    }

    public String visit(ImportNode n) { return null; }

    public String visit(StructDeclNode n) {
        TypeMapper.registrarTipoUsuario(n.nombre, Type.ESTRUCTURA);
        return null;
    }

    public String visit(FieldNode n) { return null; }

    public String visit(ParamNode n) {
        Type tipo = TypeMapper.deTexto(n.tipo);
        if (tipo == Type.ERROR) {
            tipo = TypeMapper.deUsuario(n.tipo);
            if (tipo == Type.ESTRUCTURA || tipo == Type.CLASE) {
                ctx.placeUserTypes().put(n.nombre, n.tipo);
            }
        }
        ctx.placeTypes().put(n.nombre, tipo);
        // arg2 = tipo declarado ("[]" al final si es un arreglo pasado por referencia)
        String tipoDeclarado = n.tipo + (n.modo == ParamNode.ModoPaso.REFERENCIA_ARREGLO ? "[]" : "");
        ctx.emit("param", n.nombre, tipoDeclarado, null, tipo);
        return null;
    }

    public String visit(ClassDeclNode n) {
        TypeMapper.registrarTipoUsuario(n.nombre, Type.CLASE);
        Set<String> nombresAtributos = new HashSet<>();
        for (var atributo : n.atributos) nombresAtributos.add(atributo.nombre);
        Set<String> nombresMetodos = new HashSet<>();
        for (var m : n.metodos) nombresMetodos.add(m.nombre);

        ctx.enterClass(n.nombre, nombresAtributos, nombresMetodos);
        for (var c : n.constructores) ctx.generate(c);
        for (var m : n.metodos) ctx.generate(m);
        ctx.exitClass();
        return null;
    }

    public String visit(FunctionDeclNode n) {
        String nombreCompleto = n.esMetodo ? n.claseDuena + "_" + n.nombre : n.nombre;
        // arg1 = tipo de retorno declarado (null = void); lo usa CEmitter para la firma en C
        ctx.emit("func", n.tipoRetorno, null, nombreCompleto, Type.VOID);

        Set<String> nombresParametros = new HashSet<>();
        for (ParamNode p : n.parametros) nombresParametros.add(p.nombre);
        ctx.startLocals(nombresParametros);

        if (n.esMetodo) {
            ctx.emit("param", "this", null, null, Type.CLASE);
            ctx.placeTypes().put("this", Type.CLASE);
            ctx.placeUserTypes().put("this", n.claseDuena);
        }

        for (ParamNode p : n.parametros) ctx.generate(p);
        for (Statement s : n.cuerpo) ctx.generate(s);
        ctx.emit("endfunc", null, null, nombreCompleto, Type.VOID);
        return null;
    }

    public String visit(ConstructorDeclNode n) {
        String nombreCompleto = n.claseDuena + "_init";
        ctx.emit("func", "void", null, nombreCompleto, Type.VOID);
        ctx.emit("param", "this", null, null, Type.CLASE);
        ctx.placeTypes().put("this", Type.CLASE);
        ctx.placeUserTypes().put("this", n.claseDuena);

        Set<String> nombresParametros = new HashSet<>();
        for (ParamNode p : n.parametros) nombresParametros.add(p.nombre);
        ctx.startLocals(nombresParametros);

        for (ParamNode p : n.parametros) ctx.generate(p);
        for (Statement s : n.cuerpo) ctx.generate(s);
        ctx.emit("endfunc", null, null, nombreCompleto, Type.VOID);
        return null;
    }

    public String visit(VarDeclNode n) {
        Type tipo = TypeMapper.deTexto(n.tipo);
        if (tipo == Type.ERROR) {
            tipo = TypeMapper.deUsuario(n.tipo);
            if (tipo == Type.ESTRUCTURA || tipo == Type.CLASE) {
                ctx.placeUserTypes().put(n.nombre, n.tipo);
            }
        }
        ctx.placeTypes().put(n.nombre, tipo);

        if (!n.dimensionesArreglo.isEmpty()) {
            ctx.arrayDimensions().put(n.nombre, n.dimensionesArreglo);
            int total = 1;
            for (int d : n.dimensionesArreglo) total *= d;
            ctx.emit("declare", String.valueOf(total), null, n.nombre, tipo);
            if (n.inicializador == null) {
                // No trae "{...}" de inicialización: igual hay que reservarle memoria
                // ahora mismo, si no queda apuntando a basura y "v[0] = ..." truena en
                // tiempo de ejecución (o ni compila, si antes ni el malloc se hacía).
                // Si es arreglo de una estructura/clase de usuario (ej. "series
                // personas[3] : Persona;"), el temporal necesita saber que es de tipo
                // "Persona" -- si no, más adelante se declara y reserva memoria como si
                // fuera un arreglo de int.
                String tipoUsr = ctx.placeUserTypes().get(n.nombre);
                String temp = ctx.newTempOf(tipo, tipoUsr);
                ctx.emit("newarray", String.valueOf(total), null, temp, tipo);
                ctx.emit("=", temp, null, n.nombre, tipo);
            }
        } else {
            ctx.emit("declare", null, null, n.nombre, tipo);
        }

        if (n.inicializador != null) {
            String valor = ctx.generate((ASTNode) n.inicializador);
            ctx.emit("=", valor, null, n.nombre, tipo);
        } else if (tipo == Type.ESTRUCTURA && n.dimensionesArreglo.isEmpty()) {
            // Instancia por defecto de UNA sola estructura ("esto d : Direccion;"). No
            // aplica a un arreglo de estructuras: ahí cada elemento ya vive dentro del
            // bloque reservado arriba por newarray, y "crear una Persona más" encima
            // solo pisaría el puntero al arreglo completo con un malloc de un solo
            // elemento (justo el bug que rompía "series personas[3] : Persona;").
            String tipoUsr = ctx.placeUserTypes().get(n.nombre);
            ctx.emit("new", tipoUsr, null, n.nombre, Type.ESTRUCTURA);
            inicializarCamposEstructura(n.nombre, tipoUsr);
        }
        return null;
    }

    private void inicializarCamposEstructura(String lugar, String tipoUsr) {
        if (tipoUsr == null || ctx.context() == null) return;
        var layout = ctx.context().layoutsEstructuras.get(tipoUsr);
        if (layout == null) return;

        for (var campo : layout.entrySet()) {
            if (campo.getValue().tipo() != Type.ESTRUCTURA) continue;
            String tipoCampo = campo.getValue().tipoUsuario();
            String temp = ctx.newTempOf(Type.ESTRUCTURA, tipoCampo);
            ctx.emit("new", tipoCampo, null, temp, Type.ESTRUCTURA);
            ctx.emit("setfield", lugar, campo.getKey(), temp, Type.ESTRUCTURA);
            inicializarCamposEstructura(temp, tipoCampo);
        }
    }
}