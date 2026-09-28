package net.ixdarklord.coolcatcore.internal.config.client.gui.style;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;

import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// GUI sprites as newer versions have them, on a game without the GUI sprite atlas: a sprite id like
// "mymod:config/popup" is the texture assets/mymod/textures/gui/sprites/config/popup.png (a texture path ending in
// ".png" is taken as is), scaled as the "gui" section of its .mcmeta says:
//   {"gui": {"scaling": {"type": "stretch"}}}                                  (the default)
//   {"gui": {"scaling": {"type": "tile", "width": 16, "height": 16}}}
//   {"gui": {"scaling": {"type": "nine_slice", "width": 32, "height": 32, "border": 4, "stretch_inner": false}}}
// ("border" may also be {"left": .., "top": .., "right": .., "bottom": ..}). The sizes are in the sprite's own units,
// whatever the texture's resolution, as with the atlas. Each sprite's scaling is read once; ConfigStyle.clearTextureCache
// forgets it.
public final class GuiSprites {
    private static final Map<ResourceLocation, Sprite> SPRITES = new HashMap<>();

    private GuiSprites() {}

    /** The texture a sprite id names: {@code ns:path} is {@code ns:textures/gui/sprites/path.png}, a {@code .png} path is itself. */
    public static ResourceLocation texture(ResourceLocation sprite) {
        if (sprite.getPath().endsWith(".png")) return sprite;
        return new ResourceLocation(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
    }

    /** Draws a sprite over a rectangle, scaled as its .mcmeta says. */
    public static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        blitSprite(graphics, sprite, x, y, width, height, 0xFFFFFFFF);
    }

    /** As {@link #blitSprite(GuiGraphics, ResourceLocation, int, int, int, int)}, tinted and faded by an ARGB color. */
    public static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        Sprite loaded = SPRITES.computeIfAbsent(sprite, GuiSprites::load);
        loaded.scaling.draw(graphics, loaded.texture, x, y, width, height, color);
    }

    /** Forgets every sprite's scaling, after resources reload. */
    public static void clearCache() {
        SPRITES.clear();
    }

    private record Sprite(ResourceLocation texture, Scaling scaling) {}

    private static Sprite load(ResourceLocation sprite) {
        ResourceLocation texture = texture(sprite);
        ResourceLocation meta = new ResourceLocation(texture.getNamespace(), texture.getPath() + ".mcmeta");
        Scaling scaling = new Stretch();
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(meta);
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject gui = GsonHelper.getAsJsonObject(json, "gui", null);
                JsonObject scalingJson = gui == null ? null : GsonHelper.getAsJsonObject(gui, "scaling", null);
                if (scalingJson != null) scaling = scaling(scalingJson);
            } catch (Exception e) {
                CoolCatCore.LOGGER.warn("Ignoring the GUI scaling of {}: {}", meta, e.getMessage());
            }
        }
        return new Sprite(texture, scaling);
    }

    private static Scaling scaling(JsonObject json) {
        String type = GsonHelper.getAsString(json, "type", "stretch").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "stretch" -> new Stretch();
            case "tile" -> new Tile(positive(json, "width"), positive(json, "height"));
            case "nine_slice" -> {
                int width = positive(json, "width");
                int height = positive(json, "height");
                JsonElement border = json.get("border");
                int left, top, right, bottom;
                if (border == null) throw new IllegalArgumentException("A nine_slice scaling needs a border");
                if (border.isJsonPrimitive()) {
                    left = top = right = bottom = border.getAsInt();
                } else {
                    JsonObject sides = border.getAsJsonObject();
                    left = GsonHelper.getAsInt(sides, "left");
                    top = GsonHelper.getAsInt(sides, "top");
                    right = GsonHelper.getAsInt(sides, "right");
                    bottom = GsonHelper.getAsInt(sides, "bottom");
                }
                yield new NineSlice(width, height, left, top, right, bottom, GsonHelper.getAsBoolean(json, "stretch_inner", false));
            }
            default -> throw new IllegalArgumentException("Unknown scaling type " + type);
        };
    }

    private static int positive(JsonObject json, String key) {
        int value = GsonHelper.getAsInt(json, key);
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    private interface Scaling {
        void draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int color);
    }

    // The whole texture over the rectangle.
    private record Stretch() implements Scaling {
        @Override
        public void draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int color) {
            ConfigStyle.blit(graphics, texture, x, y, 0, 0, width, height, 1, 1, 1, 1, color);
        }
    }

    // The texture repeated at its size from the top-left corner, the last row and column cut.
    private record Tile(int width, int height) implements Scaling {
        @Override
        public void draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int color) {
            tiled(graphics, texture, x, y, width, height, 0, 0, this.width, this.height, this.width, this.height, color);
        }
    }

    // Corners as they are, edges and the middle repeated (or stretched, with stretch_inner) between them.
    private record NineSlice(int width, int height, int left, int top, int right, int bottom, boolean stretchInner) implements Scaling {
        @Override
        public void draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int color) {
            int sw = this.width;
            int sh = this.height;
            // Borders never take more than half the rectangle.
            int l = Math.min(this.left, width / 2);
            int r = Math.min(this.right, width / 2);
            int t = Math.min(this.top, height / 2);
            int b = Math.min(this.bottom, height / 2);
            int innerWidth = width - l - r;
            int innerHeight = height - t - b;
            int regionWidth = sw - this.left - this.right;
            int regionHeight = sh - this.top - this.bottom;
            // Corners.
            part(graphics, texture, x, y, l, t, 0, 0, l, t, color);
            part(graphics, texture, x + width - r, y, r, t, sw - r, 0, r, t, color);
            part(graphics, texture, x, y + height - b, l, b, 0, sh - b, l, b, color);
            part(graphics, texture, x + width - r, y + height - b, r, b, sw - r, sh - b, r, b, color);
            // Edges and the middle.
            this.inner(graphics, texture, x + l, y, innerWidth, t, this.left, 0, regionWidth, t, color);
            this.inner(graphics, texture, x + l, y + height - b, innerWidth, b, this.left, sh - b, regionWidth, b, color);
            this.inner(graphics, texture, x, y + t, l, innerHeight, 0, this.top, l, regionHeight, color);
            this.inner(graphics, texture, x + width - r, y + t, r, innerHeight, sw - r, this.top, r, regionHeight, color);
            this.inner(graphics, texture, x + l, y + t, innerWidth, innerHeight, this.left, this.top, regionWidth, regionHeight, color);
        }

        private void part(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int u, int v,
                          int regionWidth, int regionHeight, int color) {
            if (width <= 0 || height <= 0) return;
            ConfigStyle.blit(graphics, texture, x, y, u, v, width, height, regionWidth, regionHeight, this.width, this.height, color);
        }

        private void inner(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int u, int v,
                           int regionWidth, int regionHeight, int color) {
            if (width <= 0 || height <= 0 || regionWidth <= 0 || regionHeight <= 0) return;
            if (this.stretchInner) {
                this.part(graphics, texture, x, y, width, height, u, v, regionWidth, regionHeight, color);
            } else {
                tiled(graphics, texture, x, y, width, height, u, v, regionWidth, regionHeight, this.width, this.height, color);
            }
        }
    }

    // Repeats a region of the sprite over a rectangle, at the region's size.
    private static void tiled(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int u, int v,
                              int regionWidth, int regionHeight, int spriteWidth, int spriteHeight, int color) {
        for (int dy = 0; dy < height; dy += regionHeight) {
            int h = Math.min(regionHeight, height - dy);
            for (int dx = 0; dx < width; dx += regionWidth) {
                int w = Math.min(regionWidth, width - dx);
                ConfigStyle.blit(graphics, texture, x + dx, y + dy, u, v, w, h, w, h, spriteWidth, spriteHeight, color);
            }
        }
    }
}
