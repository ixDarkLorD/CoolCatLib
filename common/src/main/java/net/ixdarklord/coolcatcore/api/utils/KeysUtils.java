package net.ixdarklord.coolcatcore.api.utils;


import net.minecraft.client.gui.screens.Screen;

public final class KeysUtils {
    public static boolean isHolden3ComboButtons() {
        return Screen.hasControlDown() && Screen.hasShiftDown() && Screen.hasAltDown();
    }
}
