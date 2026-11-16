package net.ixdarklord.coolcatcanvas.internal.client.effect;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffects;
import net.ixdarklord.coolcatcore.api.client.registry.KeyMappingRegistry;
import net.ixdarklord.coolcatcore.api.command.ModCommands;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.SharedSuggestionProvider;

// Ways to open the effects screen: a key (unbound by default), and in a development environment only,
// "/coolcatcanvas effects" for testing.
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
        if (!Platform.isDevelopmentEnvironment()) return;
        // CoolCatLib's own client command; other subcommands can merge into it.
        ModCommands.registerClient(CoolCatCanvas.MOD_ID, "coolcatcanvas", (root, context) -> root
                        .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("effects").executes(ctx -> {
                            ClientConfigManager.openLater(() -> ScreenEffects.createScreen(null));
                            return 1;
                        })));
    }
}
