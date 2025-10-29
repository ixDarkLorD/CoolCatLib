package net.ixdarklord.coolcatlib.api.datagen.language;

import java.util.Map;

public record AbstractLanguageWrapper(LanguageProvider provider) {

    public void generateTranslations(TranslationConsumer consumer) {
        provider.addTranslations();
        Map<String, String> translations = provider.getTranslations();
        translations.forEach(consumer::add);
    }

    @FunctionalInterface
    public interface TranslationConsumer {
        void add(String key, String value);
    }

    @Override
    public LanguageProvider provider() {
        return provider;
    }
}
