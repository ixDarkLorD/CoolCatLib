package net.ixdarklord.coolcat_lib.mixin.fabric;

import com.google.gson.JsonObject;
import net.ixdarklord.coolcat_lib.common.crafting.ConditionalAdvancement;
import net.ixdarklord.coolcat_lib.common.crafting.ICondition;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.critereon.DeserializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Advancement.Builder.class)
public class Advancement$BuilderMixin {
    @Inject(method = "fromJson", at = @At("HEAD"), cancellable = true)
    private static void fromJson$Inject(JsonObject json, DeserializationContext context, CallbackInfoReturnable<Advancement.Builder> cir) {
        if (ConditionalAdvancement.processConditional(json, ICondition.IContext.EMPTY) == null)
            cir.setReturnValue(null);
    }

    @ModifyVariable(method = "fromJson", at = @At("HEAD"), argsOnly = true, index = 0)
    private static JsonObject fromJson$Modify(JsonObject json) {
        return ConditionalAdvancement.processConditional(json, ICondition.IContext.EMPTY);
    }
}
