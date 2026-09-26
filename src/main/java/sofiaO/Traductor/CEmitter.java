package sofiaO.Traductor;

import sofiaO.Traductor.emitters.EmissionContext;
import sofiaO.Traductor.emitters.InstructionEmitter;
import sofiaO.Traductor.emitters.EmitterRegistry;
import sofiaO.semantico.ResolvedType;
import sofiaO.util.Type;
import sofiaO.util.TypeMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CEmitter {

    private final StringBuilder salida = new StringBuilder();
    private final EmitterRegistry emitters = new EmitterRegistry();

    private Map<String, Type> tipos = new LinkedHashMap<>();
    private final Map<String, Integer> tamanosArreglos = new LinkedHashMap<>();
    private Map<String, Map<String, ResolvedType>> layoutsEstructuras = new LinkedHashMap<>();
    private Map<String, Map<String, List<Integer>>> dimensionesCamposEstructuras = new LinkedHashMap<>();
    private final Set<String> variablesArreglo = new HashSet<>();

    public Map<String, String> tiposUsuarioDeLugares = new LinkedHashMap<>();

    public void setLayoutsEstructuras(Map<String, Map<String, ResolvedType>> layouts) {
        this.layoutsEstructuras = layouts;
    }

    public void setDimensionesCamposEstructuras(Map<String, Map<String, List<Integer>>> dimensiones) {
        this.dimensionesCamposEstructuras = dimensiones == null ? new LinkedHashMap<>() : dimensiones;
    }

    public String emitir(List<Quadruple> cuartetas, Map<String, Type> tiposDeLugares) {
        salida.setLength(0);
        tamanosArreglos.clear();
        variablesArreglo.clear();
        this.tipos = tiposDeLugares == null ? new LinkedHashMap<>() : tiposDeLugares;

        for (Quadruple q : cuartetas) {
            if ("newarray".equals(q.op) && q.result != null) {
                variablesArreglo.add(q.result);
            } else if ("declare".equals(q.op) && q.arg1 != null && q.result != null) {
                variablesArreglo.add(q.result);
            } else if ("=".equals(q.op) && q.result != null && variablesArreglo.contains(q.arg1)) {
                variablesArreglo.add(q.result);
            } else if ("getfield".equals(q.op) && q.result != null) {
                String tipoStructBase = tiposUsuarioDeLugares.get(q.arg1);
                Map<String, List<Integer>> camposArreglo = tipoStructBase == null
                        ? null : dimensionesCamposEstructuras.get(tipoStructBase);
                if (camposArreglo != null && camposArreglo.containsKey(q.arg2)) {
                    variablesArreglo.add(q.result);
                }
            }
        }

        salida.append("#include <stdio.h>\n");
        salida.append("#include <stdlib.h>\n");
        salida.append("#include <string.h>\n\n");

        if (usaCadenas(cuartetas)) emitirRuntimeCadenas();

        emitirDefinicionesEstructuras();
        emitirPrototipos(cuartetas);
        emitirFunciones(cuartetas, false);
        emitirFunciones(cuartetas, true);

        return salida.toString();
    }

    private static boolean esConversionACadena(String op) {
        return "int_to_str".equals(op) || "double_to_str".equals(op)
                || "char_to_str".equals(op) || "bool_to_str".equals(op);
    }

    private static boolean usaCadenas(List<Quadruple> cuartetas) {
        for (Quadruple q : cuartetas) {
            if ("concat".equals(q.op) || esConversionACadena(q.op)) return true;
        }
        return false;
    }

    /**
     * Funciones de apoyo para concatenar. Toda cadena que devuelven vive en
     * el HEAP (malloc): la cadena resultante mide lo que mida la suma de las
     * dos, y no puede ser un arreglo de tamaño fijo en el stack.
     * Solo se emiten si el programa realmente concatena algo.
     */
    private void emitirRuntimeCadenas() {
        salida.append("/* ---- runtime de cadenas (memoria en el heap) ---- */\n");
        salida.append("static inline char* c3d_concat(const char* a, const char* b) {\n");
        salida.append("    if (a == NULL) a = \"null\";\n");
        salida.append("    if (b == NULL) b = \"null\";\n");
        salida.append("    size_t la = strlen(a), lb = strlen(b);\n");
        salida.append("    char* r = (char*) malloc(la + lb + 1);\n");
        salida.append("    memcpy(r, a, la);\n");
        salida.append("    memcpy(r + la, b, lb + 1);\n");
        salida.append("    return r;\n");
        salida.append("}\n");
        salida.append("static inline char* c3d_int_to_str(int v) {\n");
        salida.append("    char* r = (char*) malloc(16);\n");
        salida.append("    snprintf(r, 16, \"%d\", v);\n");
        salida.append("    return r;\n");
        salida.append("}\n");
        salida.append("static inline char* c3d_double_to_str(double v) {\n");
        salida.append("    char* r = (char*) malloc(32);\n");
        salida.append("    snprintf(r, 32, \"%g\", v);\n");
        salida.append("    return r;\n");
        salida.append("}\n");
        salida.append("static inline char* c3d_char_to_str(char v) {\n");
        salida.append("    char* r = (char*) malloc(2);\n");
        salida.append("    r[0] = v; r[1] = '\\0';\n");
        salida.append("    return r;\n");
        salida.append("}\n");
        salida.append("static inline char* c3d_bool_to_str(int v) {\n");
        salida.append("    const char* s = v ? \"true\" : \"false\";\n");
        salida.append("    size_t n = strlen(s) + 1;\n");
        salida.append("    char* r = (char*) malloc(n);\n");
        salida.append("    memcpy(r, s, n);\n");
        salida.append("    return r;\n");
        salida.append("}\n\n");
    }

    public void emitirArchivo(List<Quadruple> cuartetas, Map<String, Type> tiposDeLugares,
                              String rutaSalida) throws IOException {
        Files.writeString(Paths.get(rutaSalida), emitir(cuartetas, tiposDeLugares));
    }

    private void emitirDefinicionesEstructuras() {
        // 1) Declaraciones adelantadas: así el orden entre structs no importa
        //    (Persona puede tener un campo "Direccion* domicilio" aunque, por lo
        //    que sea, Direccion se defina más abajo en este mapa).
        for (String nombreStruct : layoutsEstructuras.keySet()) {
            salida.append("typedef struct ").append(nombreStruct).append(" ").append(nombreStruct).append(";\n");
        }
        if (!layoutsEstructuras.isEmpty()) salida.append("\n");

        // 2) Cuerpos completos
        for (Map.Entry<String, Map<String, ResolvedType>> entrada : layoutsEstructuras.entrySet()) {
            String nombreStruct = entrada.getKey();
            Map<String, ResolvedType> campos = entrada.getValue();

            salida.append("struct ").append(nombreStruct).append(" {\n");
            for (Map.Entry<String, ResolvedType> campo : campos.entrySet()) {
                salida.append("    ").append(lineaCampoC(nombreStruct, campo.getKey(), campo.getValue()));
            }
            salida.append("};\n\n");
        }
    }

    /**
     * La línea completa de un campo dentro de un struct/clase ("char nombre[256];",
     * "int edad;", "Direccion* domicilio;", "int notas[3];"...). A diferencia de tipoC()
     * (para variables sueltas), aquí SIEMPRE se conoce el tipoUsuario del campo (viene del
     * layout calculado por el análisis semántico), así que un campo de tipo
     * ESTRUCTURA/CLASE se declara con su nombre real, nunca "void*": "void* domicilio;"
     * compila igual por la conversión implícita de void* en C, pero pierde el tipo y
     * no avisa si por error se mezclan dos structs distintos.
     */
    private String lineaCampoC(String nombreStruct, String nombreCampo, ResolvedType campo) {
        Map<String, List<Integer>> dimensionesDelStruct = dimensionesCamposEstructuras.get(nombreStruct);
        List<Integer> dimensiones = dimensionesDelStruct == null ? null : dimensionesDelStruct.get(nombreCampo);
        if (dimensiones != null && !dimensiones.isEmpty()) {
            int total = 1;
            for (int d : dimensiones) total *= d;
            String tC = campo.tipo() == Type.CADENA ? "char" : campo.tipo().aC();
            return tC + " " + nombreCampo + "[" + total + "];\n";
        }
        if (campo.tipo() == Type.CADENA) {
            return "char " + nombreCampo + "[256];\n";
        }
        if (campo.tipo() == Type.ESTRUCTURA || campo.tipo() == Type.CLASE) {
            String tipoConcreto = campo.tipoUsuario() != null ? campo.tipoUsuario() + "*" : "void*";
            return tipoConcreto + " " + nombreCampo + ";\n";
        }
        return campo.tipo().aC() + " " + nombreCampo + ";\n";
    }

    /** Tipo C a partir del nombre de tipo declarado en el lenguaje (entero, String, Nodo...). */
    private String tipoC(String texto) {
        if (texto == null || texto.equals("void")) return "void";
        Type t = TypeMapper.deTexto(texto);
        if (t != Type.ERROR) return t.aC();
        if (layoutsEstructuras.containsKey(texto)) return texto + "*"; // objetos y structs viven en el heap
        return "int";
    }

    /** Tipo de retorno en C de la función que abre la cuarteta 'func'. */
    private String tipoRetornoC(Quadruple funcQ) {
        if (funcQ.result != null && funcQ.result.endsWith("_init")) return "void";
        return tipoC(funcQ.arg1);
    }

    /** Un parámetro de la firma en C, ej. "Nodo* siguiente1" o "int dato". */
    private String parametroC(Quadruple q, String nombreFunc) {
        String pName = q.result != null ? q.result : q.arg1;
        if ("this".equals(pName) && nombreFunc != null && nombreFunc.contains("_")) {
            return nombreFunc.split("_")[0] + "* this";
        }
        if (q.arg2 != null) { // tipo declarado en el código fuente
            String t = q.arg2;
            boolean arreglo = t.endsWith("[]");
            if (arreglo) t = t.substring(0, t.length() - 2);
            return tipoC(t) + (arreglo ? "*" : "") + " " + pName;
        }
        String tipoUsuario = tiposUsuarioDeLugares.get(pName);
        String tC = "int";
        if (q.tipoResultado == Type.CADENA) tC = "char*";
        else if (tipoUsuario != null) tC = tipoUsuario;
        return tC + " " + pName;
    }

    private void emitirPrototipos(List<Quadruple> cuartetas) {
        String nombreFunc = null;
        String retorno = "void";
        List<String> params = new ArrayList<>();

        for (Quadruple q : cuartetas) {
            if ("func".equals(q.op)) {
                nombreFunc = q.result;
                retorno = tipoRetornoC(q);
                params.clear();
            } else if ("param".equals(q.op)) {
                params.add(parametroC(q, nombreFunc));
            } else if ("endfunc".equals(q.op)) {
                if (!"main".equals(nombreFunc) && !"principal".equals(nombreFunc)) {
                    salida.append(retorno).append(" ").append(nombreFunc)
                            .append("(").append(String.join(", ", params)).append(");\n");
                }
            }
        }
        salida.append("\n");
    }

    private void emitirFunciones(List<Quadruple> cuartetas, boolean principal) {
        List<Quadruple> cuerpo = new ArrayList<>();
        List<Quadruple> instruccionesGlobales = new ArrayList<>();
        String nombreFunc = null;
        List<String> params = new ArrayList<>();

        // Recolectar instrucciones fuera de cualquier función (bloque global)
        for (Quadruple q : cuartetas) {
            if ("func".equals(q.op)) {
                nombreFunc = q.result;
            } else if ("endfunc".equals(q.op)) {
                nombreFunc = null;
            } else if (nombreFunc == null && !"halt".equals(q.op)) {
                instruccionesGlobales.add(q);
            }
        }

        nombreFunc = null;
        String retorno = "void";
        for (Quadruple q : cuartetas) {
            if ("func".equals(q.op)) {
                nombreFunc = q.result;
                retorno = tipoRetornoC(q);
                cuerpo.clear();
                params.clear();
            } else if ("param".equals(q.op)) {
                params.add(parametroC(q, nombreFunc));
            } else if ("endfunc".equals(q.op)) {
                boolean esMain = "principal".equals(nombreFunc) || "main".equals(nombreFunc);
                if ((principal && esMain) || (!principal && !esMain)) {
                    if (esMain) {
                        salida.append("int main() {\n");
                        // Emitir las variables/asignaciones globales al inicio del main
                        for (Quadruple glob : instruccionesGlobales) {
                            salida.append(traducir(glob, "main"));
                        }
                    } else {
                        salida.append(retorno).append(" ").append(nombreFunc)
                                .append("(").append(String.join(", ", params)).append(") {\n");
                    }
                    for (Quadruple instr : cuerpo) {
                        salida.append(traducir(instr, nombreFunc));
                    }
                    if (esMain) {
                        salida.append("    return 0;\n");
                    }
                    salida.append("}\n\n");
                }
                nombreFunc = null;
            } else if (nombreFunc != null) {
                cuerpo.add(q);
            }
        }
    }

    /** Crea el "puente" hacia los emisores para la cuarteta que se está traduciendo ahora. */
    private EmissionContext crearContexto(String funcionActual) {
        return new EmissionContext() {
            @Override public Type tipoDe(String lugar) { return tipos.getOrDefault(lugar, Type.ERROR); }
            @Override public String funcionActual() { return funcionActual; }
            @Override public String resolverAtributo(String lugar) { return CEmitter.this.resolverAtributo(lugar, funcionActual); }
            @Override public String tipoUsuarioDe(String lugar) {
                return "this".equals(lugar) && funcionActual != null && funcionActual.contains("_")
                        ? funcionActual.split("_")[0]
                        : tiposUsuarioDeLugares.get(lugar);
            }
            @Override public Map<String, ResolvedType> camposDe(String nombreTipoUsuario) { return layoutsEstructuras.get(nombreTipoUsuario); }
        };
    }

    private String traducir(Quadruple q, String funcionActual) {
        // 1. ¿Ya se migró esta operación a su propia clase InstructionEmitter?
        InstructionEmitter emitter = emitters.buscar(q.op);
        if (emitter != null) {
            return emitter.emitir(q, crearContexto(funcionActual));
        }
        // 2. Si no, sigue el switch de siempre (se va vaciando a medida que se migra cada caso).
        switch (q.op) {
            case "declare":
                String tipoNombre = tiposUsuarioDeLugares.get(q.result);

                if (q.tipoResultado == Type.VOID) {
                    return "";
                }

                if (esArreglo(q.result)) {
                    String tipoUsrArreglo = tiposUsuarioDeLugares.get(q.result);
                    if (tipoUsrArreglo != null && layoutsEstructuras.containsKey(tipoUsrArreglo)) {
                        return "    " + tipoUsrArreglo + "* " + q.result + " = NULL;\n";
                    }
                    return "    int* " + q.result + ";\n";
                }

                if (q.tipoResultado == Type.ESTRUCTURA || q.tipoResultado == Type.CLASE || tipoNombre != null) {
                    if (tipoNombre == null) {
                        String tC = q.tipoResultado != null ? q.tipoResultado.aC() : "int";
                        return "    " + tC + " " + q.result + ";\n";
                    }

                    if (layoutsEstructuras.containsKey(tipoNombre)) {
                        return "    " + tipoNombre + "* " + q.result + " = NULL;\n";
                    } else {
                        return "    " + tipoNombre + " " + q.result + ";\n";
                    }
                } else {
                    String tC = q.tipoResultado != null ? q.tipoResultado.aC() : "int";
                    if (tC.equals("double") && !tipos.containsKey(q.result)) tC = "int";
                    if (tC.equals("char*")) return "    char* " + q.result + " = NULL;\n";
                    return "    " + tC + " " + q.result + ";\n";
                }

            case "label":   return q.result + ":;\n";
            case "goto":    return "    goto " + q.result + ";\n";
            case "blockstart": return "    {\n";
            case "blockend":   return "    }\n";
            case "if_false":return "    if (!(" + q.arg1 + ")) goto " + q.result + ";\n";
            case "if_true": return "    if (" + q.arg1 + ") goto " + q.result + ";\n";
            case "=":       return traducirAsignacion(q, funcionActual);

            case "call":
                if (q.tipoResultado == Type.VOID || (q.result != null && q.result.startsWith("t") && tipos.get(q.result) == Type.VOID)) {
                    return "    " + q.arg1 + "(" + String.join(", ", q.extraArgs) + ");\n";
                }
                return "    " + (q.result != null ? q.result + " = " : "")
                        + q.arg1 + "(" + String.join(", ", q.extraArgs) + ");\n";

            case "print":   return traducirPrint(q);
            case "read":    return traducirRead(q);
            case "return":  return "    return" + (q.arg1 != null ? " " + q.arg1 : "") + ";\n";
            case "halt":    return "";
            case "getfield":
                return "    " + q.result + " = " + q.arg1 + "->" + q.arg2 + ";\n";
            case "getindex": {
                String tipoUsrElemento = tiposUsuarioDeLugares.get(q.result);
                if (tipoUsrElemento != null && layoutsEstructuras.containsKey(tipoUsrElemento)) {
                    return "    " + q.result + " = &" + q.arg1 + "[" + q.arg2 + "];\n";
                }
                return "    " + q.result + " = " + q.arg1 + "[" + q.arg2 + "];\n";
            }
            case "setindex":return traducirSetIndex(q);

            case "newarray": {
                String tipoUsrArreglo = tiposUsuarioDeLugares.get(q.result);
                if (tipoUsrArreglo != null && layoutsEstructuras.containsKey(tipoUsrArreglo)) {
                    return "    " + q.result + " = (" + tipoUsrArreglo + "*) malloc(" + q.arg1
                            + " * sizeof(" + tipoUsrArreglo + "));\n";
                }
                return "    " + q.result + " = (int*) malloc(" + q.arg1 + " * sizeof(int));\n";
            }
            case "new":
                return "    " + q.result + " = (" + q.arg1 + "*) malloc(sizeof(" + q.arg1 + "));\n";

            default:
                String izq = resolverAtributo(q.arg1, funcionActual);
                String der = resolverAtributo(q.arg2, funcionActual);
                if (q.arg2 == null) {
                    return "    " + q.result + " = " + q.op + " " + izq + ";\n";
                }
                return "    " + q.result + " = " + izq + " " + q.op + " " + der + ";\n";
        }
    }

    private boolean esArreglo(String identificador) {
        return variablesArreglo.contains(identificador);
    }

    private String resolverAtributo(String var, String funcionActual) {
        if (var == null) return null;
        if (funcionActual != null && funcionActual.contains("_")) {
            String clase = funcionActual.split("_")[0];
            if (layoutsEstructuras.containsKey(clase)) {
                Map<String, ResolvedType> campos = layoutsEstructuras.get(clase);
                if (campos.containsKey(var)) {
                    return "this->" + var;
                }
            }
        }
        return var;
    }

    private String traducirAsignacion(Quadruple q, String funcionActual) {
        // Si asignamos un temporal int* (de un newarray) a un puntero de struct/clase, hacer cast explícito
        String tipoDestino = tiposUsuarioDeLugares.get(q.result);
        if (tipoDestino != null && layoutsEstructuras.containsKey(tipoDestino) && esArreglo(q.arg1)) {
            return "    " + q.result + " = (" + tipoDestino + "*) " + q.arg1 + ";\n";
        }

        if (funcionActual != null && funcionActual.contains("_")) {
            String clase = funcionActual.split("_")[0];
            if (layoutsEstructuras.containsKey(clase)) {
                Map<String, ResolvedType> campos = layoutsEstructuras.get(clase);
                // el generador ya escribe "this->campo"; también se acepta el nombre suelto
                String campo = q.result.startsWith("this->") ? q.result.substring(6) : q.result;
                if (campos.containsKey(campo)) {
                    if (campos.get(campo).tipo() == Type.CADENA) {
                        // el campo es char[256]: no se puede asignar con '=', hay que copiar
                        return "    strcpy(this->" + campo + ", " + q.arg1 + ");\n";
                    }
                    return "    this->" + campo + " = " + q.arg1 + ";\n";
                }
            }
        }
        return "    " + q.result + " = " + q.arg1 + ";\n";
    }

    private String traducirSetIndex(Quadruple q) {
        return "    " + q.arg1 + "[" + q.arg2 + "] = " + q.result + ";\n";
    }

    private String traducirRead(Quadruple q) {
        Type tipo = q.tipoResultado;
        if (tipo == Type.CADENA) {
            return "    " + q.result + " = (char*) malloc(256);\n"
                    + "    scanf(\"%255s\", " + q.result + ");\n";
        }
        if (tipo == Type.ENTERO || tipo == Type.BOOL) {
            return "    scanf(\"%d\", &" + q.result + ");\n";
        }
        if (tipo == Type.CARACTER) {
            return "    scanf(\" %c\", &" + q.result + ");\n";
        }
        return "    scanf(\"%lf\", &" + q.result + ");\n";
    }

    private String traducirPrint(Quadruple q) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < q.extraArgs.size(); i++) {
            String arg = q.extraArgs.get(i);
            Type tipo = i < q.tiposExtra.size() ? q.tiposExtra.get(i) : Type.ERROR;

            if (arg.startsWith("\"")) tipo = Type.CADENA;
            else if (tipo == Type.ERROR || tipo == Type.FLOTANTE) tipo = tipos.getOrDefault(arg, Type.ENTERO);

            sb.append("    printf(\"").append(tipo.formatoPrintf()).append("\", ")
                    .append(arg).append(");\n");
        }
        sb.append("    printf(\"\\n\");\n");
        return sb.toString();
    }
}