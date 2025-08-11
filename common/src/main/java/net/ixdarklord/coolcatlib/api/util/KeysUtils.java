package net.ixdarklord.coolcatlib.api.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import static net.minecraft.client.gui.screens.Screen.*;

@Environment(EnvType.CLIENT)
public class KeysUtils {
    public static boolean isHolden3ComboButtons() {
        return hasControlDown() && hasShiftDown() && hasAltDown();
    }
}
