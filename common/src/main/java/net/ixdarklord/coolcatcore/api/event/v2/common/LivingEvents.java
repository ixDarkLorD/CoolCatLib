package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Living entities (players too) being hurt, dying and changing what they wear or hold, on the server.
 */
public final class LivingEvents {
    public static final EventInvoker<Damage> DAMAGE = EventInvoker.create(Damage.class, listeners -> (entity, source, amount) -> {
        for (Damage listener : listeners) {
            EventResult result = listener.onDamage(entity, source, amount);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    public static final EventInvoker<Death> DEATH = EventInvoker.create(Death.class, listeners -> (entity, source) -> {
        for (Death listener : listeners) {
            EventResult result = listener.onDeath(entity, source);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });

    public static final EventInvoker<EquipmentChange> EQUIPMENT_CHANGE = EventInvoker.create(EquipmentChange.class, listeners -> (entity, slot, from, to) -> {
        for (EquipmentChange listener : listeners) listener.onEquipmentChange(entity, slot, from, to);
    });

    private LivingEvents() {}

    /**
     * An entity is about to take damage, before armor and effects reduce it; interrupting stops the damage (no hurt
     * flash, no knockback). Not fired for damage the entity is immune to anyway.
     */
    @FunctionalInterface
    public interface Damage {
        EventResult onDamage(LivingEntity entity, DamageSource source, float amount);
    }

    /**
     * An entity is about to die; interrupting keeps it alive. A listener that does should give it some health back,
     * or the next blow kills it.
     */
    @FunctionalInterface
    public interface Death {
        EventResult onDeath(LivingEntity entity, DamageSource source);
    }

    /**
     * What an entity wears or holds in a slot changed (armor, hands, body, saddle), noticed on its next tick. The entity
     * already has the new item; a listener may still take it off again, for example back into the player's inventory.
     *
     * @param from what was in the slot (don't change it)
     * @param to   what is in it now
     */
    @FunctionalInterface
    public interface EquipmentChange {
        void onEquipmentChange(LivingEntity entity, EquipmentSlot slot, ItemStack from, ItemStack to);
    }
}
