package net.ixdarklord.coolcatlib.api.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.ixdarklord.coolcatlib.api.datagen.language.AbstractLanguageWrapper;
import net.ixdarklord.coolcatlib.api.datagen.language.LanguageProvider;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Fabric adapter that wraps {@link LanguageProvider}.
 */
public class FabricLanguageWrapper extends FabricLanguageProvider {

    private final AbstractLanguageWrapper wrapper;
    private final String modid;

    public FabricLanguageWrapper(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registryLookup, String locale, LanguageProvider provider) {
        super(output, locale, registryLookup);
        this.modid = output.getModId();
        this.wrapper = new AbstractLanguageWrapper(provider);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider provider, TranslationBuilder translationBuilder) {
        wrapper.generateTranslations(translationBuilder::add);
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
     * localization data is generated. It can be directly passed to Fabric's
     * {@code FabricDataGenerator.Pack#addProvider} method.
     * <p><strong>Example usage:</strong>
     * <pre>{@code
     * pack.addProvider(FabricLanguageWrapper.factory(MyEnglishLanguageProvider::new));
     * }</pre>
     *
     * @param supplier A supplier that creates the wrapped {@link LanguageProvider}.
     * @return A {@link Factory} instance producing {@link FabricLanguageWrapper} objects for {@code en_us}.
     */
    public static FabricDataGenerator.Pack.RegistryDependentFactory<FabricLanguageWrapper> factory(Supplier<LanguageProvider> supplier) {
        return factory("en_us", supplier);
    }

    /**
     * Creates a factory for generating language data providers targeting a specific locale.
     * <p>
     * This method allows multiple translations to be generated (for example,
     * {@code en_us}, {@code es_es}, {@code de_de}) by providing a distinct
     * {@link LanguageProvider} implementation for each locale. It can be directly passed to Fabric's
     * {@code FabricDataGenerator.Pack#addProvider} method.
     * <p><strong>Example usage:</strong>
     * <pre>{@code
     * pack.addProvider(FabricLanguageWrapper.factory("es_es", MySpanishLanguageProvider::new));
     * }</pre>
     *
     * @param locale   The locale code (e.g. {@code en_us}, {@code es_es}, {@code fr_fr}).
     * @param supplier A supplier that creates the wrapped {@link LanguageProvider}.
     * @return A {@link Factory} instance producing {@link FabricLanguageWrapper} objects for the specified locale.
     */
    public static FabricDataGenerator.Pack.RegistryDependentFactory<FabricLanguageWrapper> factory(String locale, Supplier<LanguageProvider> supplier) {
        return (output, lookup) -> new FabricLanguageWrapper(output, lookup, locale, supplier.get());
    }
}
