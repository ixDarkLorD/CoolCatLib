package net.ixdarklord.coolcatcanvas.api.client.armature.molang;

import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureContext;
import net.ixdarklord.coolcatcanvas.internal.client.armature.molang.MolangParser;

/**
 * A compiled Molang expression or script, the language Bedrock and Blockbench write animation values in. Keyframes,
 * {@code anim_time_update}, {@code blend_weight} and timeline instructions are all read as these, and mods can compile
 * their own.
 * <pre>{@code
 * MolangExpr bob = MolangExpr.parse("math.sin(q.anim_time * 360) * 2");
 * double value = bob.eval(context);
 * MolangExpr.parse("v.charge = math.min(v.charge + q.delta_time, 1); return v.charge;");
 * }</pre>
 * What is understood: numbers, {@code + - * /}, comparisons, {@code && || !}, {@code ?:} and {@code ??}, parentheses,
 * {@code math.*} (trigonometry in degrees, as in Bedrock), {@code query.}/{@code q.} values from {@link ArmatureQueries},
 * {@code variable.}/{@code v.} and {@code temp.}/{@code t.} variables, assignments, {@code return}, and several
 * statements split by {@code ;}. Names are not case sensitive. Anything else fails to parse.
 */
public final class MolangExpr {
    public static final MolangExpr ZERO = constant(0.0);
    public static final MolangExpr ONE = constant(1.0);

    private final Node node;
    private final String source;
    private final boolean constant;
    private final double value;

    private MolangExpr(Node node, String source, boolean constant, double value) {
        this.node = node;
        this.source = source;
        this.constant = constant;
        this.value = value;
    }

    /** An expression that is always this number. */
    public static MolangExpr constant(double value) {
        return new MolangExpr(context -> value, Double.toString(value), true, value);
    }

    /**
     * Compiles Molang source. A plain number gives a {@linkplain #isConstant() constant}.
     *
     * @throws IllegalArgumentException if the source can't be read; the message says where
     */
    public static MolangExpr parse(String source) {
        String trimmed = source.trim();
        if (trimmed.isEmpty()) return ZERO;
        try {
            double number = Double.parseDouble(trimmed);
            return constant(number);
        } catch (NumberFormatException ignored) {
        }
        Node node = MolangParser.parse(trimmed);
        if (node instanceof MolangParser.Constant c) return new MolangExpr(node, trimmed, true, c.value());
        return new MolangExpr(node, trimmed, false, 0.0);
    }

    /** Wraps a node the parser built. */
    public static MolangExpr of(Node node, String source) {
        return new MolangExpr(node, source, false, 0.0);
    }

    /** The value in this context. Never throws: a broken query or division by zero gives 0 or infinity, not an error. */
    public double eval(ArmatureContext context) {
        return this.constant ? this.value : this.node.eval(context);
    }

    public float evalFloat(ArmatureContext context) {
        return (float) this.eval(context);
    }

    /** Whether the value never changes, so it can be read once without a context. */
    public boolean isConstant() {
        return this.constant;
    }

    /** The value of a constant expression; 0 for any other. */
    public double constantValue() {
        return this.value;
    }

    public String source() {
        return this.source;
    }

    @Override
    public String toString() {
        return this.source;
    }

    /** A piece of a compiled expression. */
    @FunctionalInterface
    public interface Node {
        double eval(ArmatureContext context);
    }
}
