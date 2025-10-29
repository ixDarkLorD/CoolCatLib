package net.ixdarklord.coolcatlib.api.datagen;

import net.ixdarklord.coolcatlib.api.datagen.language.AbstractLanguageWrapper;
import net.ixdarklord.coolcatlib.api.datagen.language.LanguageProvider;
import net.minecraft.data.PackOutput;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Forge adapter that wraps {@link LanguageProvider}.
 */
public class ForgeLanguageWrapper extends net.minecraftforge.common.data.LanguageProvider {

    private final AbstractLanguageWrapper wrapper;
    private final String modid;

    /**
     * Creates a new Forge language wrapper for {@code en_us}.
     *
     * @param output   The data output.
     * @param modid    The mod ID.
     * @param provider The internal language provider.
     */
    public ForgeLanguageWrapper(PackOutput output, String modid, LanguageProvider provider) {
        this(output, modid, "en_us", provider);
    }

    /**
     * Creates a new Forge language wrapper for a specific locale.
     *
     * @param output   The data output.
     * @param modid    The mod ID.
     * @param locale   The locale (e.g. "en_us", "es_es").
     * @param provider The internal language provider.
     */
    public ForgeLanguageWrapper(PackOutput output, String modid, String locale, LanguageProvider provider) {
        super(output, modid, locale);
        this.modid = modid;
        this.wrapper = new AbstractLanguageWrapper(provider);
    }

    @Override
    protected void addTranslations() {
        wrapper.generateTranslations(this::add);
    }

    @Override
    public @NotNull String getName() {
        return String.format("%s %s", this.modid, super.getName());
    }

    /**
     * Creates a factory for generating language data providers targeting the
     * default locale ({@code en_us}).
     * <p>
     * This method is a convenience for the common case where only English
     * localization data is generated. It can be directly passed to Forge's
     * {@code DataGenerator#addProvider} method.
     * <p><strong>Example usage:</strong>
     * <pre>{@code
     * generator.addProvider(true,
     *     ForgeLanguageWrapper.factory("coolcatmod", MyEnglishLanguageProvider::new));
     * }</pre>
     *
     * @param modid    The mod ID used to locate the generated language file.
     * @param supplier A supplier that creates the wrapped {@link LanguageProvider}.
     * @return A {@link Factory} instance producing {@link ForgeLanguageWrapper} objects for {@code en_us}.
     */
    public static Factory<ForgeLanguageWrapper> factory(String modid, Supplier<LanguageProvider> supplier) {
        return factory(modid, "en_us", supplier);
    }

    /**
     * Creates a factory for generating language data providers targeting a specific locale.
     * <p>
     * This method allows multiple translations to be generated (for example,
     * {@code en_us}, {@code es_es}, {@code de_de}) by providing a distinct
     * {@link LanguageProvider} implementation for each locale.
     * <p><strong>Example usage:</strong>
     * <pre>{@code
     * generator.addProvider(true,
     *     ForgeLanguageWrapper.factory("coolcatmod", "es_es", MySpanishLanguageProvider::new));
     * }</pre>
     *
     * @param modid    The mod ID used to locate the generated language file.
     * @param locale   The locale code (e.g. {@code en_us}, {@code es_es}, {@code fr_fr}).
     * @param supplier A supplier that creates the wrapped {@link LanguageProvider}.
     * @return A {@link Factory} instance producing {@link ForgeLanguageWrapper} objects for the specified locale.
     */
    public static Factory<ForgeLanguageWrapper> factory(String modid, String locale, Supplier<LanguageProvider> supplier) {
        return output -> new ForgeLanguageWrapper(output, modid, locale, supplier.get());
    }
}
