package sofiaO;

import org.antlr.v4.runtime.*;
import sofiaO.Traductor.CEmitter;
import sofiaO.Traductor.QuadrupleGenerator;
import sofiaO.ast.ASTNode;
import sofiaO.ast.declaracion.ProgramNode;
import sofiaO.lexer.CustomYLanguageLexer;
import sofiaO.parser.*;
import sofiaO.semantico.Compilador;

import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        // ---------------------------------------------------------
        // Los 3 archivos. En tu proyecto real esto vendría de leer
        // los .y/.z/.pig desde disco; aquí van como texto para probar.
        // ---------------------------------------------------------
        String codigoY = """
%estructuras
    estructura Rectangulo:
        entero ancho
        entero alto

%funciones
    definir calcularArea({} Rectangulo r) -> entero :
        retornar r.ancho * r.alto

    definir calcularPerimetro({} Rectangulo r) -> entero :
        retornar 2 * (r.ancho + r.alto)
""".stripIndent();

        String codigoZ = """
        public class Transformador {
            int factor;

            public Transformador(int f) {
                factor = f;
            }

            public int escalarArea(int area) {
                return area * factor;
            }
        }
        """;

        String codigoPig = """
        import utilidades.y
        import Transformador.z

        VARIABILES>
        esto rect : Rectangulo;

        MAIOR>
        rect.ancho = 5;
        rect.alto = 8;

        esto area : numerus 0;
        area = calcularArea(rect);
        >> area;

        esto perimetro : numerus 0;
        perimetro = calcularPerimetro(rect);
        >> perimetro;

        esto t : Transformador novus Transformador(3);
        esto areaEscalada : numerus 0;
        areaEscalada = t.escalarArea(area);
        >> areaEscalada;
        FINIS;
        """;

        // ---------------------------------------------------------
        // 1) Parsear los 3 archivos con SU PROPIA gramática
        // ---------------------------------------------------------
        /*ProgramNode astY = (ProgramNode) new YLanguageASTBuilder().visit(
                new YLanguageParser(new CommonTokenStream(
                        //new YLanguageLexer(CharStreams.fromString(codigoY)))).programa());
                        new CustomYLanguageLexer(CharStreams.fromString(codigoY)))).programa());
*/
        // 1. Crear el stream de entrada
        CharStream input = CharStreams.fromString(codigoY);

// 2. Lexer para depuración de tokens
        CustomYLanguageLexer lexerDebug = new CustomYLanguageLexer(input);
        CommonTokenStream tokensDebug = new CommonTokenStream(lexerDebug);
        tokensDebug.fill();

        System.out.println("=== TOKENS EMITIDOS ===");
        for (Token t : tokensDebug.getTokens()) {
            String nombre = YLanguageLexer.VOCABULARY.getSymbolicName(t.getType());
            System.out.println((nombre != null ? nombre : t.getType()) + "  \t'" + t.getText().replace("\n", "\\n") + "'");
        }

// 3. CRÍTICO: Reiniciar el puntero de tokens al inicio antes de parsear
        tokensDebug.seek(0);

// 4. Crear el Parser con el stream restablecido
        YLanguageParser parser = new YLanguageParser(tokensDebug);

        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                System.err.println("Error Sintáctico en Línea " + line + ":" + charPositionInLine + " - " + msg);
            }
        });

// 5. Parsear y construir el AST
        YLanguageParser.ProgramaContext tree = parser.programa();
        YLanguageASTBuilder builder = new YLanguageASTBuilder();

        ProgramNode astY = (ProgramNode) builder.visit(tree);

        if (astY == null) {
            System.err.println("Error: YLanguageASTBuilder devolvió null para astY.");
            return;
        }

        System.out.println("AST construido exitosamente para YLanguage.");

        ProgramNode astZ = (ProgramNode) new ZetarianoASTBuilder().visit(
                new ZetarianoLanguageParser(new CommonTokenStream(
                        new ZetarianoLanguageLexer(CharStreams.fromString(codigoZ)))).programa());

        ProgramNode astPig = (ProgramNode) new PigLatinASTBuilder().visit(
                new PigLatinLanguageParser(new CommonTokenStream(
                        new PigLatinLanguageLexer(CharStreams.fromString(codigoPig)))).programa());

        // ---------------------------------------------------------
        // 2) Compilar en el orden correcto: lo que se importa PRIMERO
        // ---------------------------------------------------------
        Compilador compilador = new Compilador();
        compilador.cargarEstructurasYFunciones(astY);
        compilador.cargarClase(astZ);
        compilador.compilarPrincipal(astPig);

        System.out.println("Análisis semántico OK para los 3 archivos.");
        System.out.println("Estructuras: " + compilador.contexto.structs.keySet());
        System.out.println("Clases: " + compilador.contexto.classes.keySet());
        System.out.println("Funciones libres: " + compilador.contexto.funciones.keySet());

        // ---------------------------------------------------------
        // 3) Generar cuartetas de LOS 3 (mismo AST ya anotado por el
        //    análisis semántico que se acaba de correr arriba)
        // ---------------------------------------------------------
        QuadrupleGenerator generador = new QuadrupleGenerator();
        generador.visit(astY);
        generador.visit(astZ);
        generador.visit(astPig);

        System.out.println("\n--- CUARTETAS (Y? + Zetariano + Pig Latin) ---");
        generador.imprimir();

        // ---------------------------------------------------------
        // 4) Cuartetas -> C
        // ---------------------------------------------------------
        CEmitter emisor = new CEmitter();
        emisor.tiposUsuarioDeLugares = generador.tiposUsuarioDeLugares;
        emisor.setLayoutsEstructuras(compilador.contexto.obtenerLayoutsParaCEmitter());
        String ruta = "salida_completa.c";
        emisor.emitirArchivo(generador.cuartetas, generador.tiposDeLugares, ruta);
        System.out.println("\nArchivo C generado en: " + new File(ruta).getAbsolutePath());
        System.out.println("\n--- CÓDIGO C ---");
        System.out.println(emisor.emitir(generador.cuartetas, generador.tiposDeLugares));
    }
}