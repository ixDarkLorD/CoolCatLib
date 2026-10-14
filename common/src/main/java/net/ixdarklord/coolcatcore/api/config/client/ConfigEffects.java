package net.ixdarklord.coolcatcore.api.config.client;

import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client only: the registry of {@link ConfigEffect config screen effects}, by id. Themes name the effects they use by
 * these ids; one naming an id nobody registered skips it (and logs it once).
 * <p>
 * Built in: {@link ConfigTheme#STARFALL} (soft glows and falling stars, every theme's default).
 */
public final class ConfigEffects {
    private static final Map<ResourceLocation, ConfigEffect> EFFECTS = new ConcurrentHashMap<>();

    private ConfigEffects() {}

    /**
     * Registers an effect under an id, during client setup.
     *
     * @throws IllegalArgumentException when the id is taken
     */
    public static void register(ResourceLocation id, ConfigEffect effect) {
        if (EFFECTS.putIfAbsent(id, effect) != null) throw new IllegalArgumentException("A config effect with the id " + id + " is already registered");
    }

    public static @Nullable ConfigEffect get(ResourceLocation id) {
        return EFFECTS.get(id);
    }

    /** Every registered id. */
    public static Set<ResourceLocation> ids() {
        return Set.copyOf(EFFECTS.keySet());
    }
}
