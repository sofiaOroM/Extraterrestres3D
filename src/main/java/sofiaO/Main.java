package sofiaO;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import sofiaO.Traductor.CodeGenVisitor;
import sofiaO.ast.declaracion.VarDeclNode;
import sofiaO.parser.*;
import sofiaO.semantico.SemanticAnalyzer;
import sofiaO.semantico.SymbolTable;
import sofiaO.util.Type;

public class Main {
    public static void main(String[] args) {
        SymbolTable tabla = new SymbolTable();
        tabla.declarar("fuerza", Type.ENTERO, 0, 0);

        // --- Y? (snippet aislado) ---
        YLanguageLexer lexerY = new YLanguageLexer(CharStreams.fromString("entero edadUsuario = 25"));
        YLanguageParser parserY = new YLanguageParser(new CommonTokenStream(lexerY));
        YLanguageParser.DeclaracionVariableLocalContext arbolY = parserY.declaracionVariableLocal();

        // --- Zetariano (snippet aislado) ---
        ZetarianoLanguageLexer lexerZ = new ZetarianoLanguageLexer(CharStreams.fromString("int total = fuerza * 2;"));
        ZetarianoLanguageParser parserZ = new ZetarianoLanguageParser(new CommonTokenStream(lexerZ));
        ZetarianoLanguageParser.DeclaracionVariableContext arbolZ = parserZ.declaracionVariable();

        // --- Pig Latin (snippet aislado, MISMO nivel que Y? y Zetariano) ---
        PigLatinLanguageLexer lexerPDecl = new PigLatinLanguageLexer(CharStreams.fromString("esto total2 : numerus fuerza * 2;"));
        PigLatinLanguageParser parserPDecl = new PigLatinLanguageParser(new CommonTokenStream(lexerPDecl));
        PigLatinLanguageParser.DeclaracionVariableContext arbolP = parserPDecl.declaracionVariable();

        // AST Builders
        VarDeclNode astY   = (VarDeclNode) new YLanguageASTBuilder().visit(arbolY);
        VarDeclNode astZ   = (VarDeclNode) new ZetarianoASTBuilder().visit(arbolZ);
        VarDeclNode astPig = (VarDeclNode) new PigLatinASTBuilder().visit(arbolP);

        // Análisis semántico
        SemanticAnalyzer analyzer = new SemanticAnalyzer(tabla);
        analyzer.visit(astY);
        analyzer.visit(astZ);
        analyzer.visit(astPig);

        // Generación de código de 3 direcciones
        CodeGenVisitor gen = new CodeGenVisitor();
        gen.visit(astY);
        gen.visit(astZ);
        gen.visit(astPig);

        System.out.println("--- CÓDIGO DE 3 DIRECCIONES GENERADO ---");
        gen.codigo.forEach(System.out::println);

        // --- Árbol del PROGRAMA COMPLETO: instancia INDEPENDIENTE, no reusar parserPDecl ---
        String programaCompleto = "MAIOR> esto totalX : numerus fuerza * 2; esto totalY : numerus 2 * 4; FINIS;";
        PigLatinLanguageLexer lexerPrograma = new PigLatinLanguageLexer(CharStreams.fromString(programaCompleto));
        PigLatinLanguageParser parserPrograma = new PigLatinLanguageParser(new CommonTokenStream(lexerPrograma));
        ParseTree treeCompleto = parserPrograma.seccionPrincipal();

        System.out.println("\n--- ÁRBOL DE SINTAXIS (PARSE TREE) ---");
        System.out.println(Trees.toStringTree(treeCompleto));
        System.out.println(treeCompleto.toStringTree(parserPrograma));
    }
}