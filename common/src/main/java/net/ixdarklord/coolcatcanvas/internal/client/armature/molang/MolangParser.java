package net.ixdarklord.coolcatcanvas.internal.client.armature.molang;

import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureContext;
import net.ixdarklord.coolcatcanvas.api.client.armature.molang.MolangExpr.Node;
import net.ixdarklord.coolcatcanvas.api.client.armature.molang.ArmatureQueries;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Molang source to a tree of {@link Node}s. A recursive descent parser over a small tokenizer; constant parts are
 * folded while parsing, so {@code "2 * 45"} costs nothing to run.
 */
public final class MolangParser {
    private static final double[] NO_ARGS = new double[0];
    private static final Set<String> WARNED = new HashSet<>();

    private final String source;
    private final List<Token> tokens;
    private int at;

    private MolangParser(String source) {
        this.source = source;
        this.tokens = tokenize(source);
    }

    /** @throws IllegalArgumentException if the source isn't Molang this parser knows */
    public static Node parse(String source) {
        MolangParser parser = new MolangParser(source);
        return parser.program();
    }

    /** A node whose value never changes. */
    public record Constant(double value) implements Node {
        @Override
        public double eval(ArmatureContext context) {
            return this.value;
        }
    }

    // ---- Statements ----

    // Statements split by ';'. A lone expression is the value; with several, only "return" gives one (else 0).
    private Node program() {
        List<Node> statements = new ArrayList<>();
        List<Boolean> returns = new ArrayList<>();
        while (!this.atEnd()) {
            if (this.match(";")) continue;
            boolean isReturn = this.peekWord("return");
            if (isReturn) this.at++;
            statements.add(this.statement());
            returns.add(isReturn);
            if (!this.atEnd() && !this.match(";")) throw this.error("Expected ';'");
        }
        if (statements.isEmpty()) return new Constant(0.0);
        if (statements.size() == 1) return statements.getFirst();
        Node[] all = statements.toArray(Node[]::new);
        boolean[] isReturn = new boolean[all.length];
        for (int i = 0; i < all.length; i++) isReturn[i] = returns.get(i);
        return context -> {
            for (int i = 0; i < all.length; i++) {
                double value = all[i].eval(context);
                if (isReturn[i]) return value;
            }
            return 0.0;
        };
    }

    private Node statement() {
        // name = expression
        if (this.peek().kind == Kind.NAME && this.peekAt(1, "=")) {
            String name = this.next().text;
            this.at++;
            Node value = this.expression();
            return assignment(name, value);
        }
        return this.expression();
    }

    private Node assignment(String name, Node value) {
        String normal = MolangVars.normalize(name);
        if (normal.startsWith("query.") || normal.startsWith("math.")) throw this.error("Can't assign to " + name);
        int slot = MolangVars.slot(normal);
        boolean temp = normal.startsWith("temp.");
        return context -> {
            double v = value.eval(context);
            if (temp) context.setTempSlot(slot, v);
            else context.setVariableSlot(slot, v);
            return v;
        };
    }

    // ---- Expressions, loosest first ----

    private Node expression() {
        return this.conditional();
    }

    // a ? b : c, and Bedrock's binary a ? b (b when a, else 0).
    private Node conditional() {
        Node condition = this.coalesce();
        if (!this.match("?")) return condition;
        Node then = this.conditional();
        Node otherwise = this.match(":") ? this.conditional() : new Constant(0.0);
        if (condition instanceof Constant c) return c.value != 0.0 ? then : otherwise;
        return context -> condition.eval(context) != 0.0 ? then.eval(context) : otherwise.eval(context);
    }

    // a ?? b: Molang uses it for "a if it's set"; every name here has a value, so it is a.
    private Node coalesce() {
        Node left = this.or();
        while (this.match("??")) this.or();
        return left;
    }

    private Node or() {
        Node left = this.and();
        while (this.match("||")) {
            Node a = left, b = this.and();
            left = fold(a, b, context -> a.eval(context) != 0.0 || b.eval(context) != 0.0 ? 1.0 : 0.0);
        }
        return left;
    }

    private Node and() {
        Node left = this.equality();
        while (this.match("&&")) {
            Node a = left, b = this.equality();
            left = fold(a, b, context -> a.eval(context) != 0.0 && b.eval(context) != 0.0 ? 1.0 : 0.0);
        }
        return left;
    }

    private Node equality() {
        Node left = this.comparison();
        while (true) {
            Node a = left;
            if (this.match("==")) {
                Node b = this.comparison();
                left = fold(a, b, context -> Math.abs(a.eval(context) - b.eval(context)) < 1.0E-6 ? 1.0 : 0.0);
            } else if (this.match("!=")) {
                Node b = this.comparison();
                left = fold(a, b, context -> Math.abs(a.eval(context) - b.eval(context)) >= 1.0E-6 ? 1.0 : 0.0);
            } else {
                return left;
            }
        }
    }

    private Node comparison() {
        Node left = this.additive();
        while (true) {
            Node a = left;
            if (this.match("<=")) {
                Node b = this.additive();
                left = fold(a, b, context -> a.eval(context) <= b.eval(context) ? 1.0 : 0.0);
            } else if (this.match(">=")) {
                Node b = this.additive();
                left = fold(a, b, context -> a.eval(context) >= b.eval(context) ? 1.0 : 0.0);
            } else if (this.match("<")) {
                Node b = this.additive();
                left = fold(a, b, context -> a.eval(context) < b.eval(context) ? 1.0 : 0.0);
            } else if (this.match(">")) {
                Node b = this.additive();
                left = fold(a, b, context -> a.eval(context) > b.eval(context) ? 1.0 : 0.0);
            } else {
                return left;
            }
        }
    }

    private Node additive() {
        Node left = this.multiplicative();
        while (true) {
            Node a = left;
            if (this.match("+")) {
                Node b = this.multiplicative();
                left = fold(a, b, context -> a.eval(context) + b.eval(context));
            } else if (this.match("-")) {
                Node b = this.multiplicative();
                left = fold(a, b, context -> a.eval(context) - b.eval(context));
            } else {
                return left;
            }
        }
    }

    private Node multiplicative() {
        Node left = this.unary();
        while (true) {
            Node a = left;
            if (this.match("*")) {
                Node b = this.unary();
                left = fold(a, b, context -> a.eval(context) * b.eval(context));
            } else if (this.match("/")) {
                Node b = this.unary();
                left = fold(a, b, context -> {
                    double divisor = b.eval(context);
                    return divisor == 0.0 ? 0.0 : a.eval(context) / divisor;
                });
            } else {
                return left;
            }
        }
    }

    private Node unary() {
        if (this.match("-")) {
            Node a = this.unary();
            return fold(a, a, context -> -a.eval(context));
        }
        if (this.match("+")) return this.unary();
        if (this.match("!")) {
            Node a = this.unary();
            return fold(a, a, context -> a.eval(context) == 0.0 ? 1.0 : 0.0);
        }
        return this.primary();
    }

    private Node primary() {
        Token token = this.next();
        switch (token.kind) {
            case NUMBER:
                return new Constant(Double.parseDouble(token.text));
            case STRING:
                // Strings only appear in comparisons Molang animations rarely use; they read as 0.
                return new Constant(0.0);
            case SYMBOL:
                if (token.text.equals("(")) {
                    Node inner = this.expression();
                    this.expect(")");
                    return inner;
                }
                if (token.text.equals("{")) {
                    // A braced block: its statements, then on.
                    int depth = 1;
                    while (!this.atEnd() && depth > 0) {
                        Token t = this.next();
                        if (t.text.equals("{")) depth++;
                        else if (t.text.equals("}")) depth--;
                    }
                    return new Constant(0.0);
                }
                throw this.error("Unexpected '" + token.text + "'");
            case NAME:
                return this.name(token.text);
            default:
                throw this.error("Unexpected end");
        }
    }

    private Node name(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        List<Node> args = new ArrayList<>();
        boolean call = false;
        if (this.match("(")) {
            call = true;
            if (!this.match(")")) {
                do {
                    args.add(this.expression());
                } while (this.match(","));
                this.expect(")");
            }
        }
        switch (name) {
            case "true":
                return new Constant(1.0);
            case "false":
                return new Constant(0.0);
            case "this":
                return new Constant(0.0);
            default:
                break;
        }
        if (name.startsWith("math.")) return this.math(name.substring(5), args);
        String normal = MolangVars.normalize(name);
        if (normal.startsWith("query.")) {
            ArmatureQueries.Query query = ArmatureQueries.get(normal);
            if (query == null) {
                warnOnce("Unknown Molang query " + name + " in \"" + this.source + "\"; it reads as 0");
                return new Constant(0.0);
            }
            if (!call || args.isEmpty()) return context -> query.get(context, NO_ARGS);
            Node[] argNodes = args.toArray(Node[]::new);
            return context -> {
                double[] values = new double[argNodes.length];
                for (int i = 0; i < values.length; i++) values[i] = argNodes[i].eval(context);
                return query.get(context, values);
            };
        }
        int slot = MolangVars.slot(normal);
        if (normal.startsWith("temp.")) return context -> context.tempSlot(slot);
        return context -> context.variableSlot(slot);
    }

    private Node math(String function, List<Node> args) {
        Node a = args.size() > 0 ? args.get(0) : new Constant(0.0);
        Node b = args.size() > 1 ? args.get(1) : new Constant(0.0);
        Node c = args.size() > 2 ? args.get(2) : new Constant(0.0);
        Node result = switch (function) {
            case "pi" -> new Constant(Math.PI);
            case "abs" -> ctx -> Math.abs(a.eval(ctx));
            case "sin" -> ctx -> Math.sin(Math.toRadians(a.eval(ctx)));
            case "cos" -> ctx -> Math.cos(Math.toRadians(a.eval(ctx)));
            case "asin" -> ctx -> Math.toDegrees(Math.asin(a.eval(ctx)));
            case "acos" -> ctx -> Math.toDegrees(Math.acos(a.eval(ctx)));
            case "atan" -> ctx -> Math.toDegrees(Math.atan(a.eval(ctx)));
            case "atan2" -> ctx -> Math.toDegrees(Math.atan2(a.eval(ctx), b.eval(ctx)));
            case "ceil" -> ctx -> Math.ceil(a.eval(ctx));
            case "floor" -> ctx -> Math.floor(a.eval(ctx));
            case "round" -> ctx -> Math.round(a.eval(ctx));
            case "trunc" -> ctx -> (double) (long) a.eval(ctx);
            case "sqrt" -> ctx -> Math.sqrt(a.eval(ctx));
            case "exp" -> ctx -> Math.exp(a.eval(ctx));
            case "ln" -> ctx -> Math.log(a.eval(ctx));
            case "pow" -> ctx -> Math.pow(a.eval(ctx), b.eval(ctx));
            case "sign" -> ctx -> Math.signum(a.eval(ctx));
            case "copy_sign" -> ctx -> Math.copySign(a.eval(ctx), b.eval(ctx));
            case "min" -> ctx -> Math.min(a.eval(ctx), b.eval(ctx));
            case "max" -> ctx -> Math.max(a.eval(ctx), b.eval(ctx));
            case "mod" -> ctx -> {
                double d = b.eval(ctx);
                return d == 0.0 ? 0.0 : a.eval(ctx) % d;
            };
            case "clamp" -> ctx -> Math.max(b.eval(ctx), Math.min(c.eval(ctx), a.eval(ctx)));
            case "lerp" -> ctx -> {
                double from = a.eval(ctx);
                return from + (b.eval(ctx) - from) * c.eval(ctx);
            };
            case "inverse_lerp" -> ctx -> {
                double from = a.eval(ctx), to = b.eval(ctx);
                return to == from ? 0.0 : (c.eval(ctx) - from) / (to - from);
            };
            case "lerprotate" -> ctx -> {
                double from = a.eval(ctx);
                return from + wrapDegrees(b.eval(ctx) - from) * c.eval(ctx);
            };
            case "min_angle" -> ctx -> wrapDegrees(a.eval(ctx));
            case "hermite_blend" -> ctx -> {
                double t = a.eval(ctx);
                return 3.0 * t * t - 2.0 * t * t * t;
            };
            case "random" -> ctx -> {
                double lo = a.eval(ctx);
                return lo + ThreadLocalRandom.current().nextDouble() * (b.eval(ctx) - lo);
            };
            case "random_integer" -> ctx -> {
                long lo = Math.round(a.eval(ctx)), hi = Math.round(b.eval(ctx));
                return hi <= lo ? lo : ThreadLocalRandom.current().nextLong(lo, hi + 1);
            };
            case "die_roll" -> ctx -> {
                int n = (int) a.eval(ctx);
                double lo = b.eval(ctx), hi = c.eval(ctx), sum = 0.0;
                for (int i = 0; i < n; i++) sum += lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
                return sum;
            };
            case "die_roll_integer" -> ctx -> {
                int n = (int) a.eval(ctx);
                long lo = Math.round(b.eval(ctx)), hi = Math.round(c.eval(ctx));
                long sum = 0;
                for (int i = 0; i < n; i++) sum += hi <= lo ? lo : ThreadLocalRandom.current().nextLong(lo, hi + 1);
                return sum;
            };
            default -> throw this.error("Unknown function math." + function);
        };
        // Pure functions of constants fold away; random ones never do.
        boolean pure = !function.startsWith("random") && !function.startsWith("die_roll");
        if (pure && !(result instanceof Constant) && args.stream().allMatch(n -> n instanceof Constant)) {
            return new Constant(result.eval(null));
        }
        return result;
    }

    private static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    private static Node fold(Node a, Node b, Node op) {
        if (a instanceof Constant && b instanceof Constant) return new Constant(op.eval(null));
        return op;
    }

    private static void warnOnce(String message) {
        synchronized (WARNED) {
            if (WARNED.add(message)) CoolCatCanvas.LOGGER.warn(message);
        }
    }

    // ---- Tokens ----

    private enum Kind { NUMBER, NAME, STRING, SYMBOL, END }

    private record Token(Kind kind, String text, int position) {
    }

    private static final String[] SYMBOLS = {"??", "==", "!=", "<=", ">=", "&&", "||", "->", "+", "-", "*", "/", "(", ")", ",", "?", ":", "<", ">", "!", "=", ";", "{", "}", "[", "]"};

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = 0, n = source.length();
        outer:
        while (i < n) {
            char ch = source.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            if (Character.isDigit(ch) || (ch == '.' && i + 1 < n && Character.isDigit(source.charAt(i + 1)))) {
                int start = i;
                while (i < n && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
                if (i < n && (source.charAt(i) == 'f' || source.charAt(i) == 'F')) {
                    tokens.add(new Token(Kind.NUMBER, source.substring(start, i), start));
                    i++;
                    continue;
                }
                tokens.add(new Token(Kind.NUMBER, source.substring(start, i), start));
                continue;
            }
            if (Character.isLetter(ch) || ch == '_') {
                int start = i;
                while (i < n) {
                    char c = source.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || (c == '.' && i + 1 < n && (Character.isLetter(source.charAt(i + 1)) || source.charAt(i + 1) == '_'))) i++;
                    else break;
                }
                tokens.add(new Token(Kind.NAME, source.substring(start, i), start));
                continue;
            }
            if (ch == '\'' || ch == '"') {
                int start = ++i;
                while (i < n && source.charAt(i) != ch) i++;
                tokens.add(new Token(Kind.STRING, source.substring(start, Math.min(i, n)), start));
                i++;
                continue;
            }
            for (String symbol : SYMBOLS) {
                if (source.startsWith(symbol, i)) {
                    tokens.add(new Token(Kind.SYMBOL, symbol, i));
                    i += symbol.length();
                    continue outer;
                }
            }
            throw new IllegalArgumentException("Unexpected '" + ch + "' at " + i + " in \"" + source + "\"");
        }
        tokens.add(new Token(Kind.END, "", n));
        return tokens;
    }

    private Token peek() {
        return this.tokens.get(this.at);
    }

    private boolean peekAt(int offset, String symbol) {
        int index = this.at + offset;
        return index < this.tokens.size() && this.tokens.get(index).kind == Kind.SYMBOL && this.tokens.get(index).text.equals(symbol);
    }

    private boolean peekWord(String word) {
        Token token = this.peek();
        return token.kind == Kind.NAME && token.text.equalsIgnoreCase(word);
    }

    private Token next() {
        Token token = this.tokens.get(this.at);
        if (token.kind != Kind.END) this.at++;
        return token;
    }

    private boolean atEnd() {
        return this.peek().kind == Kind.END;
    }

    private boolean match(String symbol) {
        Token token = this.peek();
        if (token.kind == Kind.SYMBOL && token.text.equals(symbol)) {
            this.at++;
            return true;
        }
        return false;
    }

    private void expect(String symbol) {
        if (!this.match(symbol)) throw this.error("Expected '" + symbol + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + this.peek().position + " in \"" + this.source + "\"");
    }
}
