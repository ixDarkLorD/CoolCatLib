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
// (attributes, enchantment effects, the packets), so a listener taking an item off again leaves nothing behind.
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    public abstract ItemStack getItemBySlot(EquipmentSlot slot);

    @Shadow
    public abstract boolean equipmentHasChanged(ItemStack previous, ItemStack current);

    // The game hands over what each slot held last time.
    @Inject(method = "collectEquipmentChanges", at = @At("HEAD"))
    private void coolcatlib$equipmentChanges(Map<EquipmentSlot, ItemStack> lastEquipmentItems, CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !LivingEvents.EQUIPMENT_CHANGE.hasListeners()) return;
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack previous = lastEquipmentItems.get(slot);
            ItemStack current = this.getItemBySlot(slot);
            if (this.equipmentHasChanged(previous, current)) LivingEvents.EQUIPMENT_CHANGE.invoker().onEquipmentChange(self, slot, previous, current);
        }
    }
}
