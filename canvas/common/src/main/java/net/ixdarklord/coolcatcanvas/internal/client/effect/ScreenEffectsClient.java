package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffects;
import net.ixdarklord.coolcatcore.api.client.registry.KeyMappingRegistry;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.SharedSuggestionProvider;

// Ways to open the effects screen: a key (unbound by default) and "/coolcatcanvas_client effects".
public final class ScreenEffectsClient {
    private static final KeyMapping OPEN_SCREEN = new KeyMapping("key.coolcatcanvas.screen_effects", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);

    private ScreenEffectsClient() {}

    public static void init() {
        KeyMappingRegistry.register(OPEN_SCREEN);
        ClientTickEvents.END.register(minecraft -> {
            while (OPEN_SCREEN.consumeClick()) {
                if (minecraft.screen == null) ScreenEffects.openScreen();
            }
        });
        // CoolCatLib: Canvas's own client command; other subcommands can merge into it.
        ClientCommandEvents.REGISTER.register((dispatcher, context) -> dispatcher.register(
                LiteralArgumentBuilder.<SharedSuggestionProvider>literal("coolcatcanvas_client")
                        .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("effects").executes(ctx -> {
                            ClientConfigManager.openLater(() -> ScreenEffects.createScreen(null));
                            return 1;
                        }))));
    }
}
