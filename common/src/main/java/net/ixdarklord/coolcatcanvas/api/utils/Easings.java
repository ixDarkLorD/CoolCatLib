package net.ixdarklord.coolcatcanvas.api.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@link Easing} curves by name, for anything that is written down: cutscene files, commands, packets. The names
 * are the constants' in lower case ({@code "sine_in_out"}).
 * <pre>{@code
 * Easing easing = Easings.byName("cubic_out");      // null when there is no such curve
 * String name = Easings.nameOf(Easing.CUBIC_OUT);   // "cubic_out"
 * Easings.register("mymod:wobble", t -> ...);       // a mod's own curve, usable wherever names are read
 * }</pre>
 */
public final class Easings {
    private static final Map<String, Easing> BY_NAME = new LinkedHashMap<>();
    private static final Map<Easing, String> NAMES = new IdentityHashMap<>();

    /** Reads and writes a curve as its name; a curve that was never named can't be written. */
    public static final Codec<Easing> CODEC = Codec.STRING.flatXmap(
            name -> {
                Easing easing = byName(name);
                return easing != null ? DataResult.success(easing) : DataResult.error(() -> "Unknown easing: " + name);
            },
            easing -> {
                String name = nameOf(easing);
                return name != null ? DataResult.success(name) : DataResult.error(() -> "This easing has no name; give it one with Easings.register");
            });

    static {
        register("linear", Easing.LINEAR);
        register("step", Easing.STEP);
        register("smooth", Easing.SMOOTH);
        register("sine_in", Easing.SINE_IN);
        register("sine_out", Easing.SINE_OUT);
        register("sine_in_out", Easing.SINE_IN_OUT);
        register("quad_in", Easing.QUAD_IN);
        register("quad_out", Easing.QUAD_OUT);
        register("quad_in_out", Easing.QUAD_IN_OUT);
        register("cubic_in", Easing.CUBIC_IN);
        register("cubic_out", Easing.CUBIC_OUT);
        register("cubic_in_out", Easing.CUBIC_IN_OUT);
        register("quart_in", Easing.QUART_IN);
        register("quart_out", Easing.QUART_OUT);
        register("quart_in_out", Easing.QUART_IN_OUT);
        register("expo_in", Easing.EXPO_IN);
        register("expo_out", Easing.EXPO_OUT);
        register("expo_in_out", Easing.EXPO_IN_OUT);
        register("back_in", Easing.BACK_IN);
        register("back_out", Easing.BACK_OUT);
        register("back_in_out", Easing.BACK_IN_OUT);
        register("elastic_out", Easing.ELASTIC_OUT);
        register("bounce_out", Easing.BOUNCE_OUT);
    }

    private Easings() {}

    /**
     * Names a curve. Both sides of a connection need the same names for a curve to travel between them.
     *
     * @throws IllegalArgumentException if the name is taken by another curve
     */
    public static synchronized void register(String name, Easing easing) {
        String key = name.toLowerCase(Locale.ROOT);
        Easing old = BY_NAME.putIfAbsent(key, easing);
        if (old != null && old != easing) throw new IllegalArgumentException("Easing name already in use: " + key);
        NAMES.putIfAbsent(easing, key);
    }

    public static synchronized @Nullable Easing byName(String name) {
        return BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    /** The name a curve was registered under, or null for one that never was (a lambda, or {@code reversed()}). */
    public static synchronized @Nullable String nameOf(Easing easing) {
        return NAMES.get(easing);
    }

    /** Every name, in the order they were registered. */
    public static synchronized List<String> names() {
        return Collections.unmodifiableList(List.copyOf(BY_NAME.keySet()));
    }
}
