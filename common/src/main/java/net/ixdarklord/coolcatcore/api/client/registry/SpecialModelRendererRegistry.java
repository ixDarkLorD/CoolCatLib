package net.ixdarklord.coolcatcore.api.client.registry;

import com.mojang.serialization.MapCodec;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.SpecialModelRenderersAccessor;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;

/**
 * Special item model renderer types: what an item model's {@code "type": "minecraft:special"} entry names in its
 * {@code "model": {"type": ...}}, for items drawn by code (a chest's lid, a shield's pattern, an icon floating over a
 * block). Register while the client initialises, before resources load.
 * <pre>{@code
 * SpecialModelRendererRegistry.register(MyMod.id("trophy_icon"), TrophyIconRenderer.Unbaked.MAP_CODEC);
 * }</pre>
 */
public final class SpecialModelRendererRegistry {
    private SpecialModelRendererRegistry() {}

    /**
     * @param id    the type's id, as item model JSON names it
     * @param codec reads the type's unbaked renderer from the item model JSON
     */
    public static void register(Identifier id, MapCodec<? extends SpecialModelRenderer.Unbaked<?>> codec) {
        SpecialModelRenderersAccessor.coolcatcanvas$idMapper().put(id, codec);
    }
}
