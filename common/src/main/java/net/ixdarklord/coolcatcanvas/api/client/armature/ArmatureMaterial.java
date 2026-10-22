package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * How a part of an armature is drawn. Each joint has one (set in its {@code .armature.json}; children take their parent's), so a
 * single model can mix a solid body, see-through wings and a glowing halo.
 */
public enum ArmatureMaterial {
    /** Lit, with fully clear pixels cut out, both sides drawn: right for most models, and the default. */
    CUTOUT(RenderTypes::entityCutout),
    /** As {@link #CUTOUT}, but faces seen from behind are skipped. */
    CUTOUT_CULL(RenderTypes::entityCutoutCull),
    /** Lit and fully opaque. */
    SOLID(RenderTypes::entitySolid),
    /** Lit, with partly clear pixels blended: glass, ghosts, water. */
    TRANSLUCENT(RenderTypes::entityTranslucent),
    /** Blended and unaffected by light: glowing translucent parts. */
    EMISSIVE(RenderTypes::entityTranslucentEmissive),
    /** Added onto what is behind it, at full brightness: eyes and energy, as an enderman's eyes. */
    GLOW(RenderTypes::eyes);

    private final Function<Identifier, RenderType> renderType;

    ArmatureMaterial(Function<Identifier, RenderType> renderType) {
        this.renderType = renderType;
    }

    public RenderType renderType(Identifier texture) {
        return this.renderType.apply(texture);
    }

    /** Whether it lets light from the world change it; lights coming in have no say on the ones that don't. */
    public boolean lit() {
        return this != EMISSIVE && this != GLOW;
    }

    /** By name, as in {@code .armature.json} files ({@code "translucent"}); null if unknown. */
    public static @Nullable ArmatureMaterial byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
