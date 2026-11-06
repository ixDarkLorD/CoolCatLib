package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.LivingEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

// LivingEvents.EQUIPMENT_CHANGE: the slots that changed since the last tick, told before the game applies the change
// (attributes, the packets), so a listener taking an item off again leaves nothing behind. This version keeps the last
// hand and armor items apart, and compares them the way collectEquipmentChanges does.
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    public abstract ItemStack getItemBySlot(EquipmentSlot slot);

    @Shadow
    public abstract boolean equipmentHasChanged(ItemStack previous, ItemStack current);

    @Shadow
    private ItemStack getLastHandItem(EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Shadow
    private ItemStack getLastArmorItem(EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Inject(method = "collectEquipmentChanges", at = @At("HEAD"))
    private void coolcatlib$equipmentChanges(CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !LivingEvents.EQUIPMENT_CHANGE.hasListeners()) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack previous;
            if (slot.getType() == EquipmentSlot.Type.HAND) previous = this.getLastHandItem(slot);
            else if (slot.getType() == EquipmentSlot.Type.ARMOR) previous = this.getLastArmorItem(slot);
            else continue;
            ItemStack current = this.getItemBySlot(slot);
            if (this.equipmentHasChanged(previous, current)) LivingEvents.EQUIPMENT_CHANGE.invoker().onEquipmentChange(self, slot, previous, current);
        }
    }

    // LivingEvents.EFFECT_APPLICABLE, both ways an effect is given on the server.
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$effectApplicable(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (this.coolcatlib$refusesEffect(effect, source)) cir.setReturnValue(false);
    }

    @Inject(method = "forceAddEffect", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$effectForced(MobEffectInstance effect, @Nullable Entity source, CallbackInfo ci) {
        if (this.coolcatlib$refusesEffect(effect, source)) ci.cancel();
    }

    @Unique
    private boolean coolcatlib$refusesEffect(MobEffectInstance effect, @Nullable Entity source) {
        LivingEntity self = (LivingEntity) (Object) this;
        return !self.level().isClientSide() && LivingEvents.EFFECT_APPLICABLE.hasListeners()
                && LivingEvents.EFFECT_APPLICABLE.invoker().onEffectApplicable(self, effect, source).isInterrupt();
    }
}
