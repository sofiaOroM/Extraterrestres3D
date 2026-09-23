package sofiaO.servicio;

import org.antlr.v4.runtime.*;
import sofiaO.Traductor.CEmitter;
import sofiaO.Traductor.Quadruple;
import sofiaO.Traductor.QuadrupleGenerator;
import sofiaO.ast.Declaration;
import sofiaO.ast.declaracion.ClassDeclNode;
import sofiaO.ast.declaracion.ImportNode;
import sofiaO.ast.declaracion.ProgramNode;
import sofiaO.lexer.CustomYLanguageLexer;
import sofiaO.parser.*;
import sofiaO.semantico.Compilador;
import sofiaO.semantico.SemanticException;
import sofiaO.util.TypeMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ejecuta todo el proceso de compilación sin imprimir nada por consola:
 * léxico -> sintáctico -> semántico -> cuartetas -> código C.
 * Devuelve un {@link ResultadoCompilacion} que la interfaz gráfica sabe mostrar.
 *
 * Es la misma secuencia que hacía Main, pero: lee los archivos del árbol de trabajo
 * (siguiendo los import del .pig), junta TODOS los errores en vez de imprimirlos, y
 * se detiene antes de generar código si hubo alguno.
 */
public class ServicioCompilacion {

    private static final int MAX_ERRORES_POR_ARCHIVO = 100;
    private static final Pattern UBICACION_EN_MENSAJE = Pattern.compile("\\s*\\(línea \\d+\\)");

    private static final class ArchivoAnalizado {
        final Path archivo;
        final Lenguaje lenguaje;
        final ProgramNode ast;

        ArchivoAnalizado(Path archivo, Lenguaje lenguaje, ProgramNode ast) {
            this.archivo = archivo;
            this.lenguaje = lenguaje;
            this.ast = ast;
        }
    }

    // =====================================================================
    // Compilar un programa Pig Latin con todos sus imports
    // =====================================================================

    /**
     * @param archivoPig programa principal (.pig)
     * @param raiz       carpeta de trabajo, contra la que se resuelven los import (puede ser null)
     * @param fuente     de dónde se lee el texto de cada archivo
     */
    public synchronized ResultadoCompilacion compilarPrograma(Path archivoPig, Path raiz, FuenteArchivos fuente) {
        ResultadoCompilacion r = new ResultadoCompilacion();
        TypeMapper.reiniciar();
        r.archivos.add(archivoPig);

        Path carpetaBase = raiz != null ? raiz : archivoPig.toAbsolutePath().getParent();

        // 1. El programa principal debe ser válido léxica y sintácticamente antes de mirar sus imports.
        ProgramNode astPig = leerYParsear(archivoPig, Lenguaje.PIG, fuente, r.errores);
        if (astPig == null) return ordenar(r);

        // 2. Buscar y analizar cada archivo importado.
        List<ArchivoAnalizado> archivosY = new ArrayList<>();
        List<ArchivoAnalizado> archivosZ = new ArrayList<>();
        Set<Path> yaCargados = new HashSet<>();
        yaCargados.add(archivoPig.toAbsolutePath().normalize());

        for (ImportNode imp : astPig.imports) {
            Path resuelto = resolverImport(imp.ruta, archivoPig, carpetaBase);
            if (resuelto == null) {
                r.errores.add(new ErrorCompilador(ErrorCompilador.Tipo.SEMANTICO,
                        "No se encontró el archivo importado '" + imp.ruta + "'. Debe existir como "
                                + rutaEsperada(imp.ruta) + " dentro de la carpeta de trabajo.",
                        archivoPig, imp.getLine(), imp.getColumn(), 0));
                continue;
            }
            if (!yaCargados.add(resuelto.toAbsolutePath().normalize())) continue;

            Lenguaje lenguaje = Lenguaje.de(resuelto).orElse(Lenguaje.Y);
            r.archivos.add(resuelto);
            ProgramNode ast = leerYParsear(resuelto, lenguaje, fuente, r.errores);
            if (ast == null) continue;
            if (lenguaje == Lenguaje.ZETARIANO) validarNombreDeClase(resuelto, ast, r.errores);
            (lenguaje == Lenguaje.ZETARIANO ? archivosZ : archivosY).add(new ArchivoAnalizado(resuelto, lenguaje, ast));
        }
        if (!r.errores.isEmpty()) return ordenar(r);

        // 3. Análisis semántico. Primero lo que se importa (.y, luego .z) y al final el .pig.
        Compilador compilador = new Compilador();
        try {
            for (ArchivoAnalizado y : archivosY) {
                compilador.cargarEstructurasYFunciones(y.ast);
                recogerErroresSemanticos(compilador, y.archivo, r.errores);
            }
            for (ArchivoAnalizado z : archivosZ) {
                compilador.cargarClase(z.ast);
                recogerErroresSemanticos(compilador, z.archivo, r.errores);
            }
            compilador.compilarPrincipal(astPig);
            recogerErroresSemanticos(compilador, archivoPig, r.errores);
        } catch (SemanticException e) {
            r.errores.add(desdeExcepcion(e, archivoPig));
        } catch (RuntimeException e) {
            r.errores.add(errorInterno(e, archivoPig));
        }
        if (!r.errores.isEmpty()) return ordenar(r);

        // 4. Cuartetas y código C.
        List<ProgramNode> ordenDeGeneracion = new ArrayList<>();
        archivosY.forEach(a -> ordenDeGeneracion.add(a.ast));
        archivosZ.forEach(a -> ordenDeGeneracion.add(a.ast));
        ordenDeGeneracion.add(astPig);
        generarCodigo(r, compilador, ordenDeGeneracion, archivoPig);
        return ordenar(r);
    }

    // =====================================================================
    // Validar un .y o un .z por separado
    // =====================================================================

    /** Valida un solo archivo (.y o .z) sin necesidad de un programa .pig que lo importe. */
    public synchronized ResultadoCompilacion validarArchivo(Path archivo, Path raiz, FuenteArchivos fuente) {
        Optional<Lenguaje> lenguaje = Lenguaje.de(archivo);
        if (lenguaje.isPresent() && lenguaje.get() == Lenguaje.PIG) {
            return compilarPrograma(archivo, raiz, fuente);
        }

        ResultadoCompilacion r = new ResultadoCompilacion();
        TypeMapper.reiniciar();
        r.archivos.add(archivo);
        if (lenguaje.isEmpty()) {
            r.errores.add(new ErrorCompilador(ErrorCompilador.Tipo.INTERNO,
                    "Extensión no reconocida. Los archivos deben terminar en .y, .z o .pig.", archivo, 1, 0, 0));
            return r;
        }

        ProgramNode ast = leerYParsear(archivo, lenguaje.get(), fuente, r.errores);
        if (ast == null) return ordenar(r);
        if (lenguaje.get() == Lenguaje.ZETARIANO) validarNombreDeClase(archivo, ast, r.errores);
        if (!r.errores.isEmpty()) return ordenar(r);

        Compilador compilador = new Compilador();
        try {
            if (lenguaje.get() == Lenguaje.ZETARIANO) compilador.cargarClase(ast);
            else compilador.cargarEstructurasYFunciones(ast);
            recogerErroresSemanticos(compilador, archivo, r.errores);
        } catch (SemanticException e) {
            r.errores.add(desdeExcepcion(e, archivo));
        } catch (RuntimeException e) {
            r.errores.add(errorInterno(e, archivo));
        }
        if (!r.errores.isEmpty()) return ordenar(r);

        generarCodigo(r, compilador, List.of(ast), archivo);
        return ordenar(r);
    }

    // =====================================================================
    // Piezas internas
    // =====================================================================

    private void generarCodigo(ResultadoCompilacion r, Compilador compilador,
                               List<ProgramNode> programas, Path archivoParaErrores) {
        try {
            QuadrupleGenerator generador = new QuadrupleGenerator();
            for (ProgramNode p : programas) generador.visit(p);

            StringBuilder texto = new StringBuilder();
            int i = 0;
            for (Quadruple q : generador.cuartetas) {
                String arg2 = q.arg2 != null ? q.arg2
                        : (q.extraArgs.isEmpty() ? "" : String.join(", ", q.extraArgs));
                String instruccion = q.toString();
                r.cuartetas.add(new FilaCuarteta(i++, q.op, nvl(q.arg1), arg2, nvl(q.result), instruccion));
                texto.append(instruccion).append('\n');
            }
            r.cuartetasTexto = texto.toString();

            CEmitter emisor = new CEmitter();
            emisor.tiposUsuarioDeLugares = generador.tiposUsuarioDeLugares;
            emisor.setLayoutsEstructuras(compilador.contexto.obtenerLayoutsParaCEmitter());
            r.codigoC = emisor.emitir(generador.cuartetas, generador.tiposDeLugares);

            r.resumen.add("Estructuras: " + ordenados(compilador.contexto.structs.keySet()));
            r.resumen.add("Clases: " + ordenados(compilador.contexto.classes.keySet()));
            r.resumen.add("Funciones: " + ordenados(compilador.contexto.funciones.keySet()));
        } catch (RuntimeException e) {
            r.errores.add(errorInterno(e, archivoParaErrores));
        }
    }

    private static List<String> ordenados(Collection<String> nombres) {
        List<String> lista = new ArrayList<>(nombres);
        Collections.sort(lista);
        return lista;
    }

    /** Lee el archivo y lo convierte en AST. Devuelve null si hubo errores léxicos o sintácticos. */
    private ProgramNode leerYParsear(Path archivo, Lenguaje lenguaje, FuenteArchivos fuente,
                                     List<ErrorCompilador> errores) {
        String texto;
        try {
            texto = fuente.leer(archivo);
        } catch (IOException e) {
            errores.add(new ErrorCompilador(ErrorCompilador.Tipo.INTERNO,
                    "No se pudo leer el archivo: " + e.getMessage(), archivo, 1, 0, 0));
            return null;
        }

        List<ErrorCompilador> lexicos = new ArrayList<>();
        List<ErrorCompilador> sintacticos = new ArrayList<>();
        ProgramNode ast = null;

        try {
            CharStream entrada = CharStreams.fromString(texto);
            ColectorErroresAntlr colectorLexico = new ColectorErroresAntlr(lexicos, archivo, ErrorCompilador.Tipo.LEXICO);
            ColectorErroresAntlr colectorSintactico = new ColectorErroresAntlr(sintacticos, archivo, ErrorCompilador.Tipo.SINTACTICO);

            switch (lenguaje) {
                case Y -> {
                    Lexer lexer = new CustomYLanguageLexer(entrada);
                    reemplazarListeners(lexer, colectorLexico);
                    YLanguageParser parser = new YLanguageParser(new CommonTokenStream(lexer));
                    reemplazarListeners(parser, colectorSintactico);
                    var arbol = parser.programa();
                    if (lexicos.isEmpty() && sintacticos.isEmpty()) {
                        ast = (ProgramNode) new YLanguageASTBuilder().visit(arbol);
                    }
                }
                case ZETARIANO -> {
                    Lexer lexer = new ZetarianoLanguageLexer(entrada);
                    reemplazarListeners(lexer, colectorLexico);
                    ZetarianoLanguageParser parser = new ZetarianoLanguageParser(new CommonTokenStream(lexer));
                    reemplazarListeners(parser, colectorSintactico);
                    var arbol = parser.programa();
                    if (lexicos.isEmpty() && sintacticos.isEmpty()) {
                        ast = (ProgramNode) new ZetarianoASTBuilder().visit(arbol);
                    }
                }
                case PIG -> {
                    Lexer lexer = new PigLatinLanguageLexer(entrada);
                    reemplazarListeners(lexer, colectorLexico);
                    PigLatinLanguageParser parser = new PigLatinLanguageParser(new CommonTokenStream(lexer));
                    reemplazarListeners(parser, colectorSintactico);
                    var arbol = parser.programa();
                    if (lexicos.isEmpty() && sintacticos.isEmpty()) {
                        ast = (ProgramNode) new PigLatinASTBuilder().visit(arbol);
                    }
                }
            }
        } catch (RuntimeException e) {
            errores.add(errorInterno(e, archivo));
            return null;
        }

        // Un carácter inválido suele provocar además un error sintáctico en la misma línea;
        // solo se muestra el léxico, que es la causa real.
        Set<Integer> lineasConErrorLexico = new HashSet<>();
        lexicos.forEach(e -> lineasConErrorLexico.add(e.linea));
        sintacticos.removeIf(e -> lineasConErrorLexico.contains(e.linea));

        agregarSinRepetir(errores, lexicos);
        agregarSinRepetir(errores, sintacticos);

        if (!lexicos.isEmpty() || !sintacticos.isEmpty()) return null;
        if (ast == null) {
            errores.add(new ErrorCompilador(ErrorCompilador.Tipo.INTERNO,
                    "No se pudo construir el árbol de " + lenguaje.nombre + " para este archivo.", archivo, 1, 0, 0));
        }
        return ast;
    }

    private static void reemplazarListeners(Recognizer<?, ?> reconocedor, ANTLRErrorListener listener) {
        reconocedor.removeErrorListeners();
        reconocedor.addErrorListener(listener);
    }

    private static void agregarSinRepetir(List<ErrorCompilador> destino, List<ErrorCompilador> nuevos) {
        Set<String> claves = new HashSet<>();
        int delArchivo = 0;
        for (ErrorCompilador e : nuevos) {
            String clave = e.archivo + ":" + e.linea + ":" + e.columna + ":" + e.descripcion;
            if (!claves.add(clave)) continue;
            if (++delArchivo > MAX_ERRORES_POR_ARCHIVO) break;
            destino.add(e);
        }
    }

    /** El enunciado exige que el archivo .z se llame igual que la clase que define. */
    private void validarNombreDeClase(Path archivo, ProgramNode ast, List<ErrorCompilador> errores) {
        String esperado = Lenguaje.nombreBase(archivo);
        for (Declaration d : ast.declaraciones) {
            if (d instanceof ClassDeclNode clase && !clase.nombre.equals(esperado)) {
                errores.add(new ErrorCompilador(ErrorCompilador.Tipo.SEMANTICO,
                        "El archivo debe llamarse igual que la clase que define: la clase es '" + clase.nombre
                                + "' pero el archivo se llama '" + archivo.getFileName() + "' (debería ser '"
                                + clase.nombre + ".z').",
                        archivo, clase.getLine(), clase.getColumn(), 0));
            }
        }
    }

    private void recogerErroresSemanticos(Compilador compilador, Path archivo, List<ErrorCompilador> destino) {
        List<SemanticException> pendientes = compilador.getAnalizador().getErrores();
        for (SemanticException e : pendientes) destino.add(desdeExcepcion(e, archivo));
        pendientes.clear();
    }

    private static ErrorCompilador desdeExcepcion(SemanticException e, Path archivo) {
        String mensaje = UBICACION_EN_MENSAJE.matcher(e.getMessage()).replaceAll("").trim();
        return new ErrorCompilador(ErrorCompilador.Tipo.SEMANTICO, mensaje, archivo,
                e.tieneUbicacion() ? e.getLinea() : 1, Math.max(0, e.getColumna()), 0);
    }

    private static ErrorCompilador errorInterno(RuntimeException e, Path archivo) {
        String detalle = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return new ErrorCompilador(ErrorCompilador.Tipo.INTERNO,
                "Error inesperado del compilador: " + detalle, archivo, 1, 0, 0);
    }

    // ---------------- imports ----------------

    /** carpeta.Objeto1.z -> carpeta/Objeto1.z */
    private static String rutaEsperada(String rutaImport) {
        int ultimoPunto = rutaImport.lastIndexOf('.');
        if (ultimoPunto < 0) return rutaImport;
        return rutaImport.substring(0, ultimoPunto).replace('.', '/') + rutaImport.substring(ultimoPunto);
    }

    private static Path resolverImport(String rutaImport, Path archivoPig, Path raiz) {
        String relativa = rutaEsperada(rutaImport).replace('/', File.separatorChar);

        List<Path> candidatos = new ArrayList<>();
        if (raiz != null) candidatos.add(raiz.resolve(relativa));
        Path carpetaDelPig = archivoPig.toAbsolutePath().getParent();
        if (carpetaDelPig != null) candidatos.add(carpetaDelPig.resolve(relativa));
        for (Path c : candidatos) {
            if (Files.isRegularFile(c)) return c;
        }

        // Último recurso: buscar solo por nombre de archivo dentro de la carpeta de trabajo.
        if (raiz != null && Files.isDirectory(raiz)) {
            String nombre = Path.of(relativa).getFileName().toString();
            try (Stream<Path> recorrido = Files.walk(raiz, 8)) {
                return recorrido.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equals(nombre))
                        .findFirst().orElse(null);
            } catch (IOException ignorada) {
                return null;
            }
        }
        return null;
    }

    private static ResultadoCompilacion ordenar(ResultadoCompilacion r) {
        r.errores.sort(Comparator
                .comparing((ErrorCompilador e) -> e.nombreArchivo())
                .thenComparingInt(e -> e.linea)
                .thenComparingInt(e -> e.columna));
        return r;
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
