package net.ixdarklord.coolcatcore.internal.config.client.gui.style;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.ixdarklord.coolcatcore.api.config.ConfigColorScheme;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.internal.config.client.CoolCatCoreClientSettings;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// The look shared by every config screen: panels with softened corners, drawn in the current theme's colors, and
// the themed background.
public final class ConfigStyle {
    private static final Map<ResourceLocation, int[]> TEXTURE_SIZES = new HashMap<>();
    // The title screen's panorama, turning on its own while a config screen shows it, and the blur of menu backgrounds
    // (as newer versions have it; see assets/coolcatcore/shaders/post/menu_blur.json).
    private static @Nullable PanoramaRenderer panoramaRenderer;
    private static final ResourceLocation BLUR = CoolCatCore.rl("shaders/post/menu_blur.json");
    private static @Nullable PostChain blurEffect;
    private static boolean blurUnavailable;
    private static int blurWidth;
    private static int blurHeight;
    private static ConfigTheme theme = ConfigTheme.DEFAULT;

    private ConfigStyle() {}

    /** Makes a theme the one widgets draw with; screens set theirs before drawing. */
    public static void use(ConfigTheme newTheme) {
        theme = newTheme;
    }

    public static ConfigTheme theme() {
        return theme;
    }

    /** The current theme's colors. */
    public static ConfigColorScheme colors() {
        return theme.colors(mode());
    }

    /** Dark or light, as the player chose. */
    public static ConfigTheme.Mode mode() {
        return CoolCatCoreClientSettings.themeMode();
    }

    /** Another theme's accent in the current mode, for things drawn in a mod's own color (its config cards). */
    public static int accentOf(ConfigTheme other) {
        return other.colors(mode()).accent();
    }

    public static int accent() {
        return colors().accent();
    }

    // --- Colors ---

    public static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | color & 0xFFFFFF;
    }

    public static int mix(int from, int to, float t) {
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Dark text on light colors, light text on dark ones. */
    public static int readableOn(int color) {
        double luminance = 0.2126 * (color >> 16 & 0xFF) + 0.7152 * (color >> 8 & 0xFF) + 0.0722 * (color & 0xFF);
        return luminance > 150 ? 0xFF0B0E14 : 0xFFFFFFFF;
    }

    // --- Shapes: rectangles with their corner pixels cut, which reads as slightly rounded at GUI scale ---

    public static void rect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 2 || height <= 2) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 1, y, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 1, y, x + width - 1, y + 1, color);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** A translucent panel with a hairline border and a faint accent sheen along its top. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        rect(graphics, x, y, width, height, colors().panel());
        outline(graphics, x, y, width, height, colors().panelBorder());
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + Math.min(10, height - 1), withAlpha(accent(), 0x14), withAlpha(accent(), 0));
    }

    public static void bar(GuiGraphics graphics, int x, int y, int width, int height) {
        rect(graphics, x, y, width, height, colors().bar());
        outline(graphics, x, y, width, height, colors().panelBorder());
    }

    /** A small label with a colored border, like a tag. */
    public static int badge(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        int width = font.width(text) + 8;
        rect(graphics, x, y, width, 11, withAlpha(color, 0x30));
        outline(graphics, x, y, width, 11, withAlpha(color, 0xB0));
        graphics.drawString(font, text, x + 4, y + 2, color, false);
        return width;
    }

    // --- Text ---

    public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, ellipsize(font, text, maxWidth), x, y, color, false);
    }

    public static void centeredText(GuiGraphics graphics, Font font, Component text, int centerX, int y, int maxWidth, int color) {
        FormattedCharSequence line = ellipsize(font, text, maxWidth);
        graphics.drawString(font, line, centerX - font.width(line) / 2, y, color, false);
    }

    public static FormattedCharSequence ellipsize(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("…")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    // --- Drawing helpers: what newer versions' GUI does in one call ---

    /**
     * Draws a region of a texture stretched over a rectangle, tinted and faded by an ARGB color.
     *
     * @param u the region's left in the texture, in pixels
     * @param v the region's top
     */
    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                            int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        graphics.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        RenderSystem.disableBlend();
        graphics.setColor(1, 1, 1, 1);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                            int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        blit(graphics, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    /** Draws a GUI sprite (a sprite id, or a texture path), scaled as its .mcmeta says; see {@link GuiSprites}. */
    public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        GuiSprites.blitSprite(graphics, sprite, x, y, width, height);
    }

    /** Shows a tooltip at the mouse this frame, drawn by the current screen after everything else. */
    public static void tooltip(List<FormattedCharSequence> lines) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) screen.setTooltipForNextRenderPass(lines);
    }

    /**
     * Starts a new layer: what's drawn next covers everything drawn so far, whatever its depth, as a screen drawn
     * over another must.
     */
    public static void nextLayer(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);
    }

    /**
     * Clips drawing to a rectangle. Unlike {@link GuiGraphics#enableScissor}, the rectangle moves with the pose (a page
     * sliding in, a card growing), as it does on newer versions. End it with {@link GuiGraphics#disableScissor}.
     */
    public static void enableScissor(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        Matrix4f pose = graphics.pose().last().pose();
        Vector3f from = pose.transformPosition(new Vector3f(x0, y0, 0));
        Vector3f to = pose.transformPosition(new Vector3f(x1, y1, 0));
        graphics.enableScissor((int) Math.floor(Math.min(from.x, to.x)), (int) Math.floor(Math.min(from.y, to.y)),
                (int) Math.ceil(Math.max(from.x, to.x)), (int) Math.ceil(Math.max(from.y, to.y)));
    }

    /** Draws the title screen's panorama, as a menu shows it outside a world. */
    public static void panorama(float partialTick) {
        if (panoramaRenderer == null) panoramaRenderer = new PanoramaRenderer(TitleScreen.CUBE_MAP);
        panoramaRenderer.render(partialTick, 1);
    }

    // Blurs what's been drawn so far (the panorama or the world), as newer versions' menus do.
    private static void blur(GuiGraphics graphics) {
        if (blurUnavailable) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        try {
            if (blurEffect == null) {
                blurEffect = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), target, BLUR);
                blurWidth = -1;
            }
            if (blurWidth != target.width || blurHeight != target.height) {
                blurEffect.resize(target.width, target.height);
                blurWidth = target.width;
                blurHeight = target.height;
            }
        } catch (Exception e) {
            CoolCatCore.LOGGER.warn("Config screens can't blur their background", e);
            blurUnavailable = true;
            if (blurEffect != null) blurEffect.close();
            blurEffect = null;
            return;
        }
        graphics.flush();
        blurEffect.process(minecraft.getFrameTime());
        target.bindWrite(false);
    }

    // --- Background ---

    /**
     * The theme's background. By default it's see-through: the title screen's panorama, or the world while playing,
     * blurred and darkened by the backdrop color so the panels stay readable. A theme's texture is drawn over it (in a
     * world only when the theme asks), fully covering it unless the texture is translucent. The theme's opacities are
     * scaled by the player's own choice in CoolCatLib: Core's client config.
     *
     * @param panorama draws the title screen's panorama
     */
    public static void background(GuiGraphics graphics, int width, int height, Runnable panorama) {
        Minecraft minecraft = Minecraft.getInstance();
        ConfigTheme current = theme;
        ConfigColorScheme colors = current.colors(mode());
        ResourceLocation texture = current.background();
        boolean textured = texture != null && (minecraft.level == null || current.backgroundInWorld());
        float textureOpacity = textured ? CoolCatCoreClientSettings.textureOpacity(current.textureOpacity()) : 0;
        // What shows through: the panorama or the world, blurred.
        if (textureOpacity < 1) {
            if (minecraft.level == null) panorama.run();
            blur(graphics);
        }
        if (textured && textureOpacity > 0) {
            texture(graphics, texture, current, width, height, withAlpha(0xFFFFFFFF, Math.round(255 * textureOpacity)));
        }
        float backgroundOpacity = CoolCatCoreClientSettings.backgroundOpacity(current.backgroundOpacity());
        if (backgroundOpacity > 0) graphics.fill(0, 0, width, height, withAlpha(colors.backdrop(), Math.round(255 * backgroundOpacity)));
        if (!textured) graphics.fillGradient(0, 0, width, height / 3, withAlpha(colors.accent(), 0x18), withAlpha(colors.accent(), 0));
    }

    private static void texture(GuiGraphics graphics, ResourceLocation texture, ConfigTheme current, int width, int height, int color) {
        int[] size = textureSize(texture);
        int textureWidth = size[0];
        int textureHeight = size[1];
        switch (current.mode()) {
            case STRETCH -> blit(graphics, texture, 0, 0, 0, 0, width, height, textureWidth, textureHeight, textureWidth, textureHeight, color);
            case COVER -> {
                float scale = Math.max(width / (float) textureWidth, height / (float) textureHeight);
                int drawWidth = Math.round(textureWidth * scale);
                int drawHeight = Math.round(textureHeight * scale);
                blit(graphics, texture, (width - drawWidth) / 2, (height - drawHeight) / 2, 0, 0,
                        drawWidth, drawHeight, textureWidth, textureHeight, textureWidth, textureHeight, color);
            }
            case TILE -> {
                int tileWidth = current.tileSize() > 0 ? current.tileSize() : textureWidth;
                int tileHeight = Math.max(1, Math.round(tileWidth * textureHeight / (float) textureWidth));
                for (int y = 0; y < height; y += tileHeight) {
                    for (int x = 0; x < width; x += tileWidth) {
                        blit(graphics, texture, x, y, 0, 0, tileWidth, tileHeight, textureWidth, textureHeight, textureWidth, textureHeight, color);
                    }
                }
            }
        }
    }

    // Read once; a missing texture shows as the missing-texture checkerboard.
    public static int[] textureSize(ResourceLocation texture) {
        return TEXTURE_SIZES.computeIfAbsent(texture, id -> {
            try {
                // Textures don't keep their size here: a mod icon's is in its pixels, a resource's in its file.
                AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(id);
                if (loaded instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
                    return new int[]{Math.max(1, dynamic.getPixels().getWidth()), Math.max(1, dynamic.getPixels().getHeight())};
                }
                Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
                if (resource.isEmpty()) return new int[]{256, 256};
                try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                    return new int[]{Math.max(1, image.getWidth()), Math.max(1, image.getHeight())};
                }
            } catch (Exception e) {
                return new int[]{256, 256};
            }
        });
    }

    /** Forgets texture sizes (all of them for null) and GUI sprites' scaling, after resources reload. */
    public static void clearTextureCache(@Nullable ResourceLocation texture) {
        if (texture == null) {
            TEXTURE_SIZES.clear();
            GuiSprites.clearCache();
        } else {
            TEXTURE_SIZES.remove(texture);
        }
    }
}
