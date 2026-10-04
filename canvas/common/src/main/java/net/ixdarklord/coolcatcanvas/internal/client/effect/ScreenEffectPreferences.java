package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffect;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.ScreenEffectEvents;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// What the player chose in the effects screen: which selectable effects are on, their strengths, the layer order (only
// once rearranged), and dark or light mode. Effects missing now are kept, for when their mod comes back.
public final class ScreenEffectPreferences {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<Identifier> ENABLED = new LinkedHashSet<>();
    private static final Map<Identifier, Float> STRENGTHS = new LinkedHashMap<>();
    private static @Nullable List<Identifier> order;
    private static boolean lightMode;
    private static boolean loaded;
    private static boolean dirty;

    private ScreenEffectPreferences() {}

    private static Path file() {
        return Platform.getConfigFolder().resolve("coolcatcanvas-screen-effects.json");
    }

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path file = file();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("enabled")) json.getAsJsonArray("enabled").forEach(id -> parse(id).ifPresent(ENABLED::add));
            if (json.has("strength")) {
                json.getAsJsonObject("strength").entrySet().forEach(entry -> {
                    Identifier id = Identifier.tryParse(entry.getKey());
                    if (id != null) STRENGTHS.put(id, entry.getValue().getAsFloat());
                });
            }
            lightMode = json.has("lightMode") && json.get("lightMode").getAsBoolean();
            if (json.has("order")) {
                List<Identifier> saved = new ArrayList<>();
                json.getAsJsonArray("order").forEach(id -> parse(id).ifPresent(saved::add));
                order = saved;
            }
        } catch (IOException | RuntimeException e) {
            CoolCatCanvas.LOGGER.warn("Couldn't read {}, starting with no saved screen effects", file, e);
        }
    }

    private static Optional<Identifier> parse(JsonElement element) {
        return Optional.ofNullable(element.isJsonPrimitive() ? Identifier.tryParse(element.getAsString()) : null);
    }

    /** Whether the effects screen is in light mode, the player's choice with the sun/moon button. */
    public static synchronized boolean lightMode() {
        load();
        return lightMode;
    }

    public static synchronized void setLightMode(boolean light) {
        load();
        dirty |= lightMode != light;
        lightMode = light;
        save();
    }

    static synchronized @Nullable List<Identifier> savedOrder() {
        return order;
    }

    /** Applies the saved choices to an effect, as the player's. */
    static void apply(ScreenEffectImpl effect) {
        if (!effect.isSelectable()) return;
        Float strength;
        boolean enabled;
        synchronized (ScreenEffectPreferences.class) {
            strength = STRENGTHS.get(effect.id());
            enabled = ENABLED.contains(effect.id());
        }
        if (strength != null) effect.setStrength(strength);
        if (enabled && !effect.isAutomatic()) ScreenEffectManager.runAs(ScreenEffectEvents.ToggleCause.PLAYER, effect::enable);
    }

    public static synchronized void setEnabled(ScreenEffect effect, boolean enabled) {
        dirty |= enabled ? ENABLED.add(effect.id()) : ENABLED.remove(effect.id());
    }

    public static synchronized void setStrength(ScreenEffect effect, float strength) {
        Float old = strength >= 1.0F ? STRENGTHS.remove(effect.id()) : STRENGTHS.put(effect.id(), strength);
        dirty |= old == null || old != strength;
    }

    public static synchronized void setOrder(@Nullable List<ScreenEffect> layers) {
        if (layers == null) {
            order = null;
        } else {
            // Effects from mods that aren't here now keep their place at the end.
            List<Identifier> ids = new ArrayList<>(layers.stream().map(ScreenEffect::id).toList());
            if (order != null) order.stream().filter(id -> !ids.contains(id)).forEach(ids::add);
            order = ids;
        }
        dirty = true;
    }

    public static synchronized void save() {
        if (!dirty) return;
        dirty = false;
        JsonObject json = new JsonObject();
        JsonArray enabled = new JsonArray();
        ENABLED.forEach(id -> enabled.add(id.toString()));
        json.add("enabled", enabled);
        JsonObject strengths = new JsonObject();
        STRENGTHS.forEach((id, strength) -> strengths.addProperty(id.toString(), strength));
        json.add("strength", strengths);
        if (lightMode) json.addProperty("lightMode", true);
        if (order != null) {
            JsonArray layers = new JsonArray();
            order.forEach(id -> layers.add(id.toString()));
            json.add("order", layers);
        }
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            CoolCatCanvas.LOGGER.warn("Couldn't save {}", file, e);
        }
    }
}
