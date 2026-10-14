package net.ixdarklord.coolcatcore.api.client.utils;


import net.minecraft.client.Minecraft;

public final class KeysUtils {
    public static boolean isHolden3ComboButtons() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.hasControlDown() && minecraft.hasShiftDown() && minecraft.hasAltDown();
    }
}
