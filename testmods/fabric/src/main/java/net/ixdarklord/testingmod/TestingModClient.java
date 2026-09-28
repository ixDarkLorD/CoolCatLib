package net.ixdarklord.testingmod;

import net.ixdarklord.coolcatcore.api.client.gui.screens.StorageScreen;
import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.ixdarklord.coolcatcore.api.config.ConfigColorScheme;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.client.ConfigEffects;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.testingmod.gametest.ClientGameTestRunner;

public class TestingModClient implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        // Every Testing Mod config screen in purple, unless a config sets its own theme (the startup one does).
        ConfigTheme.setForMod("testingmod", ConfigTheme.builder().colors(ConfigColorScheme.tinted(0xFFA77BFF)).build());
        // A custom effect for config screens; the startup config uses it.
        ConfigEffects.register(EmbersEffect.ID, new EmbersEffect());
        ScreenEffectsDemo.init();
        SkyboxDemo.init();
        TextEffectsDemo.init();
        MenuScreenRegistry.register(TestAttachments.CRATE_MENU, CrateScreen::new);
        MenuScreenRegistry.register(TestAttachments.POUCH_MENU, StorageScreen::new);
        ClientGameTestRunner.init();
    }
}
