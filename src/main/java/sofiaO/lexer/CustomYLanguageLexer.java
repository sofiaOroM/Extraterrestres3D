package sofiaO.lexer;

import org.antlr.v4.runtime.*;
import sofiaO.parser.YLanguageLexer;

import java.util.ArrayDeque;
import java.util.Deque;

public class CustomYLanguageLexer extends YLanguageLexer {
    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private final Deque<Token> pendingTokens = new ArrayDeque<>();

    public CustomYLanguageLexer(CharStream input) {
        super(input);
        indentStack.push(0); // Nivel base de indentación (0)
    }

    @Override
    public Token nextToken() {
        if (!pendingTokens.isEmpty()) {
            return pendingTokens.poll();
        }

        Token token = super.nextToken();

        if (token.getType() == NL) {
            // Ignorar saltos de línea si el siguiente carácter es un salto de línea adicional o EOF
            int nextChar = _input.LA(1);
            if (nextChar == '\n' || nextChar == '\r' || nextChar == CharStream.EOF) {
                return token;
            }

            String text = token.getText();
            int indent = 0;
            // Contar espacios/tabulaciones tras el último '\n' o '\r'
            for (int i = text.length() - 1; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == ' ') indent++;
                else if (c == '\t') indent += 4; // Ajuste según tabulador
                else if (c == '\n' || c == '\r') break;
            }

            int currentIndent = indentStack.peek() != null ? indentStack.peek() : 0;

            if (indent > currentIndent) {
                indentStack.push(indent);
                // Retornar el token INDENT primero y después el NL
                Token indentToken = commonToken(YLanguageLexer.INDENT, token);
                pendingTokens.add(token); // Conservar NL en la cola
                return indentToken;
            } else if (indent < currentIndent) {
                while (!indentStack.isEmpty() && indent < indentStack.peek()) {
                    indentStack.pop();
                    pendingTokens.add(commonToken(YLanguageLexer.DEDENT, token));
                }
                pendingTokens.add(token); // Encolar el NL original al final de los DEDENTs
                return pendingTokens.poll();
            }
        }

        if (token.getType() == EOF) {
            // Generar todos los DEDENT pendientes antes de entregar EOF
            while (!indentStack.isEmpty() && indentStack.peek() > 0) {
                indentStack.pop();
                pendingTokens.add(commonToken(YLanguageLexer.DEDENT, token));
            }
            pendingTokens.add(token); // Mantener el token EOF al final de la cola
            return pendingTokens.poll();
        }

        return token;
    }

    private CommonToken commonToken(int type, Token attr) {
        CommonToken t = new CommonToken(attr);
        t.setType(type);
        t.setChannel(Token.DEFAULT_CHANNEL);
        return t;
    }
}