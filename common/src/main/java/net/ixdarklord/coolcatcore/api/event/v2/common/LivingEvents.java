package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Living entities (players too) changing what they wear or hold, on the server.
 */
public final class LivingEvents {
    public static final EventInvoker<EquipmentChange> EQUIPMENT_CHANGE = EventInvoker.create(EquipmentChange.class, listeners -> (entity, slot, from, to) -> {
        for (EquipmentChange listener : listeners) listener.onEquipmentChange(entity, slot, from, to);
    });

    private LivingEvents() {}

    /**
     * What an entity wears or holds in a slot changed (armor, hands), noticed on its next tick. The entity already has
     * the new item; a listener may still take it off again, for example back into the player's inventory.
     *
     * @param from what was in the slot (don't change it)
     * @param to   what is in it now
     */
    @FunctionalInterface
    public interface EquipmentChange {
        void onEquipmentChange(LivingEntity entity, EquipmentSlot slot, ItemStack from, ItemStack to);
    }
}
