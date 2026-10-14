package net.ixdarklord.coolcatcore.api.registry;

import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.function.Supplier;

/**
 * The default attributes (health, speed, ...) of a mod's living entities, which every living entity type needs.
 * Register from the mod constructor, after the entity types' {@code DeferredRegister}:
 * <pre>{@code
 * EntityAttributes.register(MyEntities.WISP, Wisp::createAttributes);
 * }</pre>
 */
public final class EntityAttributes {
    private EntityAttributes() {}

    /**
     * @param type       the entity type, supplied so it can be a {@code RegistryEntry}
     * @param attributes builds the attributes, usually the entity class's static {@code createAttributes} method
     */
    public static void register(Supplier<? extends EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> attributes) {
        CommonServices.get().registerEntityAttributes(type, attributes);
    }
}
