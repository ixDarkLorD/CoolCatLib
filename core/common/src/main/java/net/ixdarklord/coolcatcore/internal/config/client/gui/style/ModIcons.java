package net.ixdarklord.coolcatcore.internal.config.client.gui.style;

import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.core.ModPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Mods' icons (their logo on NeoForge) as textures, loaded once from the mod files; a theme may name its own instead.
// Icons are usually far larger than they're drawn, and scaling them down by sampling skips most of their pixels, so
// each is kept at halving sizes, averaged, and drawn from the one nearest the size on screen.
public final class ModIcons {
    private static final Map<String, Optional<Icon>> LOADED = new HashMap<>();
    // Halving stops here; nothing is drawn smaller.
    private static final int SMALLEST = 16;

    private ModIcons() {}

    private record Level(Identifier id, int width, int height) {}

    // Largest first.
    private record Icon(List<Level> levels) {
        Level forSize(int pixels) {
            Level chosen = this.levels.getFirst();
            for (Level level : this.levels) {
                if (Math.max(level.width, level.height) < pixels) break;
                chosen = level;
            }
            return chosen;
        }
    }

    /**
     * Draws the mod's icon fitted into a square, keeping its proportions.
     *
     * @return whether there was an icon to draw
     */
    public static boolean draw(GuiGraphicsExtractor graphics, String modId, ConfigTheme theme, int x, int y, int size) {
        Optional<Icon> icon = get(modId, theme);
        if (icon.isEmpty()) return false;
        int pixels = (int) Math.ceil(size * Minecraft.getInstance().getWindow().getGuiScale());
        Level texture = icon.get().forSize(pixels);
        float scale = Math.min(size / (float) texture.width, size / (float) texture.height);
        int width = Math.max(1, Math.round(texture.width * scale));
        int height = Math.max(1, Math.round(texture.height * scale));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id, x + (size - width) / 2, y + (size - height) / 2, 0, 0,
                width, height, texture.width, texture.height, texture.width, texture.height);
        return true;
    }

    /** Whether the mod has an icon to draw. */
    public static boolean has(String modId, ConfigTheme theme) {
        return get(modId, theme).isPresent();
    }

    private static Optional<Icon> get(String modId, ConfigTheme theme) {
        Identifier themed = theme.icon();
        if (themed != null) {
            int[] size = ConfigStyle.textureSize(themed);
            return Optional.of(new Icon(List.of(new Level(themed, size[0], size[1]))));
        }
        return LOADED.computeIfAbsent(modId, ModIcons::load);
    }

    private static Optional<Icon> load(String modId) {
        return ModPlatform.get().readModIcon(modId).flatMap(bytes -> {
            try {
                NativeImage image = NativeImage.read(bytes);
                String base = "mod_icon/" + modId.replaceAll("[^a-z0-9_.-]", "_");
                List<Level> levels = new ArrayList<>();
                for (int i = 0; ; i++) {
                    Identifier id = CoolCatCore.rl(i == 0 ? base : base + "_" + i);
                    int width = image.getWidth();
                    int height = image.getHeight();
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "CoolCatCore icon of " + modId, image));
                    levels.add(new Level(id, width, height));
                    if (Math.min(width, height) / 2 < SMALLEST) break;
                    image = half(image);
                }
                return Optional.of(new Icon(List.copyOf(levels)));
            } catch (IOException | RuntimeException e) {
                CoolCatCore.LOGGER.debug("Couldn't load the icon of {}", modId, e);
                return Optional.empty();
            }
        });
    }

    // Each pixel the average of four, weighted by their alpha so transparent pixels don't darken the edges.
    private static NativeImage half(NativeImage source) {
        int width = source.getWidth() / 2;
        int height = source.getHeight() / 2;
        NativeImage result = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = 0;
                int red = 0;
                int green = 0;
                int blue = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int pixel = source.getPixel(x * 2 + dx, y * 2 + dy);
                        int a = pixel >>> 24;
                        alpha += a;
                        red += (pixel >> 16 & 0xFF) * a;
                        green += (pixel >> 8 & 0xFF) * a;
                        blue += (pixel & 0xFF) * a;
                    }
                }
                int argb = alpha == 0 ? 0 : (alpha / 4) << 24 | (red / alpha) << 16 | (green / alpha) << 8 | blue / alpha;
                result.setPixel(x, y, argb);
            }
        }
        return result;
    }
}
