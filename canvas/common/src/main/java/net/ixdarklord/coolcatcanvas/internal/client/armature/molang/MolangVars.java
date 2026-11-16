package net.ixdarklord.coolcatcanvas.internal.client.armature.molang;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Locale;

/**
 * Every variable name ever compiled, as a slot number, so a running expression reads an array instead of a map.
 * {@code v.speed} and {@code variable.speed} share a slot; temp variables have their own ({@code t.x} is not {@code v.x}).
 */
public final class MolangVars {
    private static final Object2IntOpenHashMap<String> SLOTS = new Object2IntOpenHashMap<>();

    static {
        SLOTS.defaultReturnValue(-1);
    }

    private MolangVars() {
    }

    /** The slot of a variable, by its whole name in any spelling ({@code v.x}, {@code variable.x}, {@code t.x}). */
    public static synchronized int slot(String name) {
        String key = normalize(name);
        int slot = SLOTS.getInt(key);
        if (slot < 0) {
            slot = SLOTS.size();
            SLOTS.put(key, slot);
        }
        return slot;
    }

    /** The slot of a variable if it was ever compiled or set, else -1. */
    public static synchronized int existing(String name) {
        return SLOTS.getInt(normalize(name));
    }

    /** {@code v.x} as {@code variable.x}, {@code t.x} as {@code temp.x}, {@code c.x} as {@code context.x}, lower case. */
    public static String normalize(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("v.")) return "variable." + lower.substring(2);
        if (lower.startsWith("t.")) return "temp." + lower.substring(2);
        if (lower.startsWith("c.")) return "context." + lower.substring(2);
        if (lower.startsWith("q.")) return "query." + lower.substring(2);
        if (!lower.contains(".")) return "variable." + lower;
        return lower;
    }
}
