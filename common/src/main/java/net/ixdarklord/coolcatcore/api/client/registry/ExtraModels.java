package net.ixdarklord.coolcatcore.api.client.registry;

import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Models baked with the game's though no item or block uses them, for drawing in code (an item renderer's parts, say).
 * Register while the client initialises; get them once the game's models are loaded.
 * <pre>{@code
 * ExtraModels.register(MyMod.id("item/orb_glow"));
 * BakedModel glow = ExtraModels.get(MyMod.id("item/orb_glow"));
 * }</pre>
 */
public final class ExtraModels {
    private ExtraModels() {}

    /** A model under {@code assets/<namespace>/models/<path>.json} to bake. */
    public static void register(ResourceLocation model) {
        register(resources -> List.of(model));
    }

    /**
     * Models found in the resources as they load (named in a mod's own data files, say): the provider is asked at
     * every load, with the resources being loaded.
     */
    public static void register(Function<ResourceManager, Collection<ResourceLocation>> provider) {
        ClientServices.get().registerExtraModels(provider);
    }

    /** The baked model, or the missing model if it wasn't registered or failed to load. */
    public static BakedModel get(ResourceLocation model) {
        BakedModel baked = ClientServices.get().getExtraModel(model);
        return baked != null ? baked : Minecraft.getInstance().getModelManager().getMissingModel();
    }
}
