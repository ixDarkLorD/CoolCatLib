package net.ixdarklord.testingmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.ixdarklord.coolcatlib.api.client.gui.components.TextScreen;
import net.ixdarklord.coolcatlib.api.utils.ColorUtils;

import java.awt.*;

public class TestingModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) ->
                TextScreen.build(50, 50, 157, 82, true, 2)
                .selectScreen(0)
                .create("Testing this shit!")
                .create("Testing this shit!")
                .create("Testing this shit!")
                .selectScreen(1)
                .create("Tsdfdfdasfdas")
                .alignToCenter(true)
                .shouldRender(true)
                .renderAllBoxes(guiGraphics, ColorUtils.rgbToRgba(Color.WHITE.getRGB(), 0.50F), Color.WHITE));
    }
}
