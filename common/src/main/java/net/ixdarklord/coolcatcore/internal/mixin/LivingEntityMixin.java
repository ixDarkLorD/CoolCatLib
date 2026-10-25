package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.LivingEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

// LivingEvents.EQUIPMENT_CHANGE: the slots that changed since the last tick, told before the game applies the change
// (attributes, enchantment effects, the packets), so a listener taking an item off again leaves nothing behind. The
// last items are kept per slot type (hands, armor, body), read the way collectEquipmentChanges reads them.
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private ItemStack lastBodyItemStack;

    @Shadow
    private ItemStack getLastHandItem(EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Shadow
    private ItemStack getLastArmorItem(EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Shadow
    public abstract ItemStack getItemBySlot(EquipmentSlot slot);

    @Shadow
    public abstract boolean equipmentHasChanged(ItemStack previous, ItemStack current);

    @Inject(method = "collectEquipmentChanges", at = @At("HEAD"))
    private void coolcatlib$equipmentChanges(CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !LivingEvents.EQUIPMENT_CHANGE.hasListeners()) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack previous = switch (slot.getType()) {
                case HAND -> this.getLastHandItem(slot);
                case HUMANOID_ARMOR -> this.getLastArmorItem(slot);
                case ANIMAL_ARMOR -> this.lastBodyItemStack;
            };
            ItemStack current = this.getItemBySlot(slot);
            if (this.equipmentHasChanged(previous, current)) LivingEvents.EQUIPMENT_CHANGE.invoker().onEquipmentChange(self, slot, previous, current);
        }
    }
}
