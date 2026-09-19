package sofiaO.Traductor;

import sofiaO.util.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CEmitter {

    private final StringBuilder salida = new StringBuilder();

    private Map<String, Type> tipos = new LinkedHashMap<>();
    private final Map<String, Integer> tamanosArreglos = new LinkedHashMap<>();
    private Map<String, Map<String, Type>> layoutsEstructuras = new LinkedHashMap<>();
    private final Set<String> variablesArreglo = new HashSet<>();

    public Map<String, String> tiposUsuarioDeLugares = new LinkedHashMap<>();

    public void setLayoutsEstructuras(Map<String, Map<String, Type>> layouts) {
        this.layoutsEstructuras = layouts;
    }

    public String emitir(List<Quadruple> cuartetas, Map<String, Type> tiposDeLugares) {
        salida.setLength(0);
        tamanosArreglos.clear();
        variablesArreglo.clear();
        this.tipos = tiposDeLugares == null ? new LinkedHashMap<>() : tiposDeLugares;

        // Identificar qué temporales/variables fueron creados con newarray
        for (Quadruple q : cuartetas) {
            if ("newarray".equals(q.op) && q.result != null) {
                variablesArreglo.add(q.result);
            }
        }

        salida.append("#include <stdio.h>\n");
        salida.append("#include <stdlib.h>\n");
        salida.append("#include <string.h>\n\n");

        emitirDefinicionesEstructuras();
        emitirPrototipos(cuartetas);
        emitirFunciones(cuartetas, false);
        emitirFunciones(cuartetas, true);

        return salida.toString();
    }

    public void emitirArchivo(List<Quadruple> cuartetas, Map<String, Type> tiposDeLugares,
                              String rutaSalida) throws IOException {
        Files.writeString(Paths.get(rutaSalida), emitir(cuartetas, tiposDeLugares));
    }

    private void emitirDefinicionesEstructuras() {
        for (Map.Entry<String, Map<String, Type>> entrada : layoutsEstructuras.entrySet()) {
            String nombreStruct = entrada.getKey();
            Map<String, Type> campos = entrada.getValue();

            salida.append("struct ").append(nombreStruct).append(" {\n");
            for (Map.Entry<String, Type> campo : campos.entrySet()) {
                if (campo.getValue() == Type.CADENA) {
                    salida.append("    char ").append(campo.getKey()).append("[256];\n");
                } else {
                    salida.append("    ").append(campo.getValue().aC())
                            .append(" ").append(campo.getKey()).append(";\n");
                }
            }
            salida.append("};\n");
            salida.append("typedef struct ").append(nombreStruct).append(" ").append(nombreStruct).append(";\n\n");
        }
    }

    private void emitirPrototipos(List<Quadruple> cuartetas) {
        String nombreFunc = null;
        List<String> params = new ArrayList<>();

        for (Quadruple q : cuartetas) {
            if ("func".equals(q.op)) {
                nombreFunc = q.result;
                params.clear();
            } else if ("param".equals(q.op)) {
                String pName = q.result != null ? q.result : q.arg1;
                if ("this".equals(pName) && nombreFunc != null && nombreFunc.contains("_")) {
                    String clase = nombreFunc.split("_")[0];
                    params.add(clase + "* this");
                } else {
                    String tipoUsuario = tiposUsuarioDeLugares.get(pName);
                    String tipoC = "int";
                    if ("nombreParametro".equals(pName) || q.tipoResultado == Type.CADENA) {
                        tipoC = "char*";
                    } else if (tipoUsuario != null) {
                        tipoC = tipoUsuario;
                    }
                    params.add(tipoC + " " + pName);
                }
            } else if ("endfunc".equals(q.op)) {
                if (!"main".equals(nombreFunc) && !"principal".equals(nombreFunc)) {
                    String tipoRetorno = nombreFunc.endsWith("_init") ? "void" : "int";
                    salida.append(tipoRetorno).append(" ").append(nombreFunc)
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
        for (Quadruple q : cuartetas) {
            if ("func".equals(q.op)) {
                nombreFunc = q.result;
                cuerpo.clear();
                params.clear();
            } else if ("param".equals(q.op)) {
                String pName = q.result != null ? q.result : q.arg1;
                if ("this".equals(pName) && nombreFunc != null && nombreFunc.contains("_")) {
                    String clase = nombreFunc.split("_")[0];
                    params.add(clase + "* this");
                } else {
                    String tipoC = "int";
                    if ("nombreParametro".equals(pName) || q.tipoResultado == Type.CADENA) {
                        tipoC = "char*";
                    }
                    params.add(tipoC + " " + pName);
                }
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
                        String tipoRetorno = nombreFunc.endsWith("_init") ? "void" : "int";
                        salida.append(tipoRetorno).append(" ").append(nombreFunc)
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

    private String traducir(Quadruple q, String funcionActual) {
        switch (q.op) {
            case "declare":
                String tipoNombre = tiposUsuarioDeLugares.get(q.result);

                if (q.tipoResultado == Type.VOID) {
                    return "";
                }

                if (esArreglo(q.result)) {
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
                    return "    " + tC + " " + q.result + ";\n";
                }

            case "label":   return q.result + ":;\n";
            case "goto":    return "    goto " + q.result + ";\n";
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
            case "setfield":
                return "    " + q.arg1 + "->" + q.arg2 + " = " + q.result + ";\n";
            case "getindex":return "    " + q.result + " = " + q.arg1 + "[" + q.arg2 + "];\n";
            case "setindex":return traducirSetIndex(q);

            case "newarray":
                return "    " + q.result + " = (int*) malloc(" + q.arg1 + " * sizeof(int));\n";
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
                Map<String, Type> campos = layoutsEstructuras.get(clase);
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
                Map<String, Type> campos = layoutsEstructuras.get(clase);
                if (campos.containsKey(q.result)) {
                    if (campos.get(q.result) == Type.CADENA) {
                        return "    strcpy(this->" + q.result + ", " + q.arg1 + ");\n";
                    }
                    return "    this->" + q.result + " = " + q.arg1 + ";\n";
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