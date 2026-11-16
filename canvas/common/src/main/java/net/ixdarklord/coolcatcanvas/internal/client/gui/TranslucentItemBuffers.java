package net.ixdarklord.coolcatcanvas.internal.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.HashMap;
import java.util.Map;

/**
 * The see-through items of {@code ItemOpacityRenderer}: while the shader color's alpha is under 1, a GUI item draws
 * into these buffers, which hand it a translucent render type on the same texture in place of a solid or cut-out one,
 * so the alpha shows ({@code GuiGraphicsMixin}; the render type's texture is reached through the access widener).
 */
public final class TranslucentItemBuffers {
    // Each render type an item asked for, and what it draws with instead; render types are made once and kept.
    private static final Map<RenderType, RenderType> TRANSLUCENT = new HashMap<>();

    private TranslucentItemBuffers() {}

    /** The buffers an item is drawn into: see-through ones while the shader color is faded, else these. */
    public static MultiBufferSource wrap(MultiBufferSource buffers) {
        if (RenderSystem.getShaderColor()[3] >= 1.0F) return buffers;
        return type -> buffers.getBuffer(translucent(type));
    }

    private static RenderType translucent(RenderType type) {
        return TRANSLUCENT.computeIfAbsent(type, TranslucentItemBuffers::find);
    }

    // The translucent render type on a type's own texture; glints, and types with no texture, as they are.
    private static RenderType find(RenderType type) {
        if (!(type instanceof RenderType.CompositeRenderType composite) || type.toString().contains("glint")) return type;
        return composite.state().textureState.cutoutTexture().map(RenderType::entityTranslucentCull).orElse(type);
    }
}
