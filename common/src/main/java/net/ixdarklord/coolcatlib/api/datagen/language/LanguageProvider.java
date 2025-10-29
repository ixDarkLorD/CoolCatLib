package net.ixdarklord.coolcatlib.api.datagen.language;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.StatType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * <p>
 * A platform-agnostic, extensible language provider inspired by Fabric’s
 * {@code TranslationBuilder}. Used to define and collect all language
 * entries for data generation on both <b>Forge</b> and <b>Fabric</b>.
 * </p>
 *
 * <p>
 * Extend this class and implement {@link #addTranslations()} to register
 * translation keys. Platform-specific wrappers (Forge or Fabric) will
 * automatically call this method during datagen.
 * </p>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * public class MyModLanguageProvider extends LanguageProvider {
 *     @Override
 *     public void addTranslations() {
 *         add("itemGroup.my_mod_tab", "My Mod Items");
 *         add(MyModItems.MAGIC_WAND.get(), "Magic Wand");
 *         add(MyModEffects.MANA_BOOST.get(), "Mana Boost");
 *     }
 * }
 * }</pre>
 */
public abstract class LanguageProvider {

    private final Map<String, String> translations = new TreeMap<>();

    public LanguageProvider() {}

    public abstract void addTranslations();

    // ------------------------------------------------------------
    // Base Add
    // ------------------------------------------------------------

    /** Adds a direct translation entry. */
    public void add(@NotNull String key, @NotNull String value) {
        if (translations.putIfAbsent(key, value) != null) {
            throw new IllegalStateException("Duplicate translation key: " + key);
        }
    }

    // ------------------------------------------------------------
    // Minecraft Object Overloads
    // ------------------------------------------------------------

    /** Adds a translation for an {@link Item}. */
    public void add(Item item, String value) {
        add(item.getDescriptionId(), value);
    }

    /** Adds a translation for a {@link MobEffect}. */
    public void add(MobEffect effect, String value) {
        add(effect.getDescriptionId(), value);
    }

    /** Adds a translation for a {@link KeyMapping}. */
    public void add(KeyMapping keyMapping, String value) {
        add(keyMapping.getName(), value);
    }

    /** Adds a translation for an {@link Enchantment}. */
    public void addEnchantment(ResourceKey<Enchantment> enchantment, String value) {
        this.add(Util.makeDescriptionId("enchantment", enchantment.location()), value);
    }

    /** Adds a translation for an {@link Attribute}. */
    public void add(Holder<Attribute> entityAttribute, String value) {
        this.add(entityAttribute.value().getDescriptionId(), value);
    }

    /** Adds a translation for an {@link EntityType}. */
    public void add(EntityType<?> entityType, String value) {
        add(entityType.getDescriptionId(), value);
    }

    /** Adds a translation for a {@link StatType}. */
    public void add(StatType<?> statType, String value) {
        this.add("stat_type." + Objects.requireNonNull(BuiltInRegistries.STAT_TYPE.getKey(statType)).toString().replace(':', '.'), value);
    }

    /** Adds a translation for a {@link ResourceLocation}. */
    public void add(ResourceLocation identifier, String value) {
        add(identifier.toLanguageKey(), value);
    }

    /**
     * Adds a translation for a {@link CreativeModeTab}.
     */
    public void add(ResourceKey<CreativeModeTab> key, String value) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(key);
        ComponentContents contents = tab.getDisplayName().getContents();

        if (contents instanceof TranslatableContents translatable) {
            add(translatable.getKey(), value);
        } else {
            throw new UnsupportedOperationException(
                    "Cannot add language entry for CreativeModeTab '%s' because its name is not translatable."
                            .formatted(tab.getDisplayName().getString())
            );
        }
    }

    // ------------------------------------------------------------
    // Potions
    // ------------------------------------------------------------

    /** Adds translation entries for a {@link Potion}. */
    public void add(Potion potion, String value) {
        addPotion(potion, value, null);
    }

    /** Adds translation entries for a {@link Potion} with a specific type suffix. */
    public void addPotion(Potion potion, String baseName, @Nullable String type) {
        ResourceLocation key = BuiltInRegistries.POTION.getKey(potion);
        assert key != null;

        String id = key.getPath();
        String name = (type == null) ? baseName : baseName.replace("%t", type);

        add("item.minecraft.potion.effect." + id, name + " Potion");
        add("item.minecraft.tipped_arrow.effect." + id, "Arrow of " + name);
        add("item.minecraft.splash_potion.effect." + id, "Splash " + name + " Potion");
        add("item.minecraft.lingering_potion.effect." + id, "Lingering " + name + " Potion");
    }

    // ------------------------------------------------------------
    // Curios / Trinkets Helpers
    // ------------------------------------------------------------

    /** Adds a Curios slot translation. */
    public void addCurios(String key, String value) {
        add("curios.identifier." + key, value);
    }

    /** Adds a Trinkets slot translation. */
    public void addTrinkets(String key, String value) {
        add("trinkets.slot." + key, value);
    }

    // ------------------------------------------------------------
    // Merge Existing Language Files
    // ------------------------------------------------------------

    /**
     * Merges an existing language JSON file into this provider.
     *
     * @param path The path to the JSON language file.
     * @throws IOException If the file cannot be read.
     */
    public void add(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject translationsJson = JsonParser.parseReader(reader).getAsJsonObject();
            for (String key : translationsJson.keySet()) {
                add(key, translationsJson.get(key).getAsString());
            }
        }
    }

    // ------------------------------------------------------------
    // Output
    // ------------------------------------------------------------

    /** Returns all collected translations as an unmodifiable map. */
    public Map<String, String> getTranslations() {
        return Collections.unmodifiableMap(translations);
    }
}
