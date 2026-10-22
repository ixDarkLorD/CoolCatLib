package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What a world effect is drawn with: a texture, how it blends, and how softly it meets solid things. Everything drawn
 * with equal materials in a frame goes out in one draw call.
 *
 * @param texture  a full texture path, such as {@code mymod:textures/vfx/ember.png}
 * @param softness over how many blocks the effect fades out as it nears whatever is behind it, so it never ends in a
 *                 hard line where it passes through a wall or the ground; 0 for a hard edge
 * @param shader   the fragment shader the material is drawn with, such as {@code mymod:vfx/portal} for
 *                 {@code assets/mymod/shaders/vfx/portal.fsh}; null for the built-in textured one. See
 *                 {@link #withShader}
 * @param smooth   whether the texture is filtered smoothly (linear) or drawn pixel for pixel (nearest), as blocks and
 *                 items are; smooth by default. See {@link #withSmoothing}
 */
public record VfxMaterial(Identifier texture, VfxBlend blend, float softness, @Nullable Identifier shader, boolean smooth) {
    /** The procedural rift shader CoolCatLib ships ({@code WorldRifts} draws with it). */
    public static final Identifier RIFT_SHADER = CoolCatCanvas.rl("vfx/rift");

    public VfxMaterial(Identifier texture, VfxBlend blend, float softness) {
        this(texture, blend, softness, null);
    }

    public VfxMaterial(Identifier texture, VfxBlend blend, float softness, @Nullable Identifier shader) {
        this(texture, blend, softness, shader, true);
    }

    /** A round, soft glow: the bread and butter of light effects. */
    public static final Identifier GLOW = CoolCatCanvas.rl("textures/vfx/glow.png");
    /** A small bright core with a wide faint halo. */
    public static final Identifier SPARKLE = CoolCatCanvas.rl("textures/vfx/sparkle.png");
    /** A four-pointed star with thin rays. */
    public static final Identifier STAR = CoolCatCanvas.rl("textures/vfx/star.png");
    /** A thin ring, for shockwaves and pulses. */
    public static final Identifier RING = CoolCatCanvas.rl("textures/vfx/ring.png");
    /** A cloudy puff, for smoke and mist. */
    public static final Identifier SMOKE = CoolCatCanvas.rl("textures/vfx/smoke.png");
    /** A streak that is bright along its middle and fades to both ends: for sparks, beams and trails. */
    public static final Identifier STREAK = CoolCatCanvas.rl("textures/vfx/streak.png");

    public static final float DEFAULT_SOFTNESS = 0.6F;

    public VfxMaterial {
        if (softness < 0.0F) throw new IllegalArgumentException("Softness can't be negative: " + softness);
    }

    /** An additive material with the default softness. */
    public static VfxMaterial additive(Identifier texture) {
        return new VfxMaterial(texture, VfxBlend.ADDITIVE, DEFAULT_SOFTNESS);
    }

    public static VfxMaterial alpha(Identifier texture) {
        return new VfxMaterial(texture, VfxBlend.ALPHA, DEFAULT_SOFTNESS);
    }

    public static VfxMaterial luminous(Identifier texture) {
        return new VfxMaterial(texture, VfxBlend.LUMINOUS, DEFAULT_SOFTNESS);
    }

    public VfxMaterial withSoftness(float softness) {
        return new VfxMaterial(this.texture, this.blend, softness, this.shader, this.smooth);
    }

    /**
     * The same material with its texture filtered smoothly ({@code true}, the default: soft glows and smoke) or drawn
     * pixel for pixel ({@code false}: pixel art, such as block and item textures, stays crisp up close).
     */
    public VfxMaterial withSmoothing(boolean smooth) {
        return new VfxMaterial(this.texture, this.blend, this.softness, this.shader, smooth);
    }

    /**
     * The same material drawn with a fragment shader of the mod's own, for what a texture cannot do: portals, rifts,
     * heat haze, anything procedural. The shader takes the place of {@code coolcatlib:vfx/quad}:
     * <pre>{@code
     * #version 330
     * #moj_import <coolcatlib:vfx.glsl>      // VfxTime.x (seconds), vfx_depth_fade(...)
     * uniform sampler2D Sampler0;               // the material's texture
     * uniform sampler2D DepthSampler;           // the scene's depth
     * in vec2 texCoord;                         // the quad's UVs, as given to VfxDraw
     * in vec4 vertexColor;                      // the colour given to VfxDraw
     * out vec4 fragColor;
     * }</pre>
     * It should multiply its alpha by {@code vfx_depth_fade(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r,
     * gl_FragCoord.z)} to hide behind solid things. A shader has no uniforms of its own; what changes from one
     * shape to the next travels in the colour and in the UVs ({@link VfxDraw#quad(net.minecraft.world.phys.Vec3,
     * net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3, float, float,
     * float, float, int)} takes any UV range).
     */
    public VfxMaterial withShader(Identifier fragmentShader) {
        return new VfxMaterial(this.texture, this.blend, this.softness, fragmentShader, this.smooth);
    }
}
