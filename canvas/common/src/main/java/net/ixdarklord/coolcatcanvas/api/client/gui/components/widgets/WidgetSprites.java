package net.ixdarklord.coolcatcanvas.api.client.gui.components.widgets;

import net.ixdarklord.coolcatcanvas.api.client.utils.NineSliceInfo;
import net.ixdarklord.coolcatcanvas.api.client.utils.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The textures of a widget for each of its states; a backport of the vanilla class added in Minecraft 1.20.2.
 * <p>
 * Minecraft 1.20.1 has no GUI sprite atlas, so the locations are full texture paths
 * (e.g. {@code modid:textures/gui/button.png}). By default a texture is stretched over the widget; use
 * {@link #nineSliced} for textures whose borders must keep their size (the equivalent of a {@code nine_slice}
 * GUI scaling on newer versions).
 */
public record WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation enabledFocused,
                            ResourceLocation disabledFocused, @Nullable NineSlice nineSlice) {
    public WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation enabledFocused, ResourceLocation disabledFocused) {
        this(enabled, disabled, enabledFocused, disabledFocused, null);
    }

    public WidgetSprites(ResourceLocation enabled, ResourceLocation focused) {
        this(enabled, enabled, focused, focused);
    }

    public WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation enabledFocused) {
        this(enabled, disabled, enabledFocused, disabled);
    }

    public ResourceLocation get(boolean enabled, boolean focused) {
        if (enabled) {
            return focused ? this.enabledFocused : this.enabled;
        } else {
            return focused ? this.disabledFocused : this.disabled;
        }
    }

    /** These sprites drawn nine-sliced: every texture is {@code textureWidth x textureHeight} with the given borders. */
    public WidgetSprites nineSliced(int textureWidth, int textureHeight, NineSliceInfo.SliceBounds border) {
        return new WidgetSprites(this.enabled, this.disabled, this.enabledFocused, this.disabledFocused, new NineSlice(textureWidth, textureHeight, border));
    }

    /** These sprites drawn nine-sliced with the same border on every side. */
    public WidgetSprites nineSliced(int textureWidth, int textureHeight, int border) {
        return this.nineSliced(textureWidth, textureHeight, NineSliceInfo.SliceBounds.uniform(border));
    }

    /** Draws the sprite for the given state over the given area. */
    public void blit(GuiGraphics graphics, boolean enabled, boolean focused, int x, int y, int width, int height) {
        blit(graphics, this.get(enabled, focused), x, y, width, height);
    }

    /** Draws the sprite for the given state over the given area, tinted by an ARGB color. */
    public void blit(GuiGraphics graphics, boolean enabled, boolean focused, int x, int y, int width, int height, int color) {
        RenderUtils.withTint(color, () -> this.blit(graphics, enabled, focused, x, y, width, height));
    }

    /** Draws one of these sprites (as returned by {@link #get}) over the given area. */
    public void blit(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (this.nineSlice == null) {
            RenderUtils.blitSprite(graphics, sprite, x, y, width, height);
            return;
        }
        RenderUtils.blitNineSliced(graphics,
                NineSliceInfo.TextureInfo.of(sprite, this.nineSlice.textureWidth(), this.nineSlice.textureHeight()),
                x, y, width, height, this.nineSlice.border(),
                NineSliceInfo.TextureRegion.region(this.nineSlice.textureWidth(), this.nineSlice.textureHeight()));
    }

    /** How a nine-sliced sprite is cut: the size of its textures and the borders that keep their size. */
    public record NineSlice(int textureWidth, int textureHeight, NineSliceInfo.SliceBounds border) {
    }
}
