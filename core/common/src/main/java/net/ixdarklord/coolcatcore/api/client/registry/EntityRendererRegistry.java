package net.ixdarklord.coolcatcore.api.client.registry;

import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/**
 * The renderers entities are drawn with. Register while the client initialises.
 * <pre>{@code
 * EntityRendererRegistry.register(MyEntities.WISP, WispRenderer::new);
 * }</pre>
 */
public final class EntityRendererRegistry {
    private EntityRendererRegistry() {}

    /**
     * @param type the entity type, supplied so it can be a {@code RegistryEntry} not yet registered
     */
    public static <T extends Entity> void register(Supplier<? extends EntityType<? extends T>> type, EntityRendererProvider<T> provider) {
        ClientServices.get().registerEntityRenderer(type, provider);
    }
}
