package net.ixdarklord.coolcat_lib.util;

public class MouseHelper {

    public static boolean isMouseOver(double mouseX, double mouseY, int x, int y, int size) {
        return isMouseOver(mouseX, mouseY, x, y, size, size);
    }

    public static boolean isMouseOver(double pMouseX, double pMouseY, int x, int y, int offsetX, int offsetY, int width, int height) {
        return isMouseOver(pMouseX, pMouseY, x + offsetX, y + offsetY, width, height);
    }

    public static boolean isMouseOver(double pMouseX, double pMouseY, double x, double y, double offsetX, double offsetY, int width, int height) {
        return isMouseOver(pMouseX, pMouseY, x + offsetX, y + offsetY, width, height);
    }

    public static boolean isMouseOver(double mouseX, double mouseY, int x, int y, int sizeX, int sizeY) {
        return (mouseX >= x && mouseX <= x + sizeX) && (mouseY >= y && mouseY <= y + sizeY);
    }

    public static boolean isMouseOver(double mouseX, double mouseY, double x, double y, int sizeX, int sizeY) {
        return (mouseX >= x && mouseX <= x + sizeX) && (mouseY >= y && mouseY <= y + sizeY);
    }
}
