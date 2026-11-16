package net.ixdarklord.coolcatcanvas.internal.mixin.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaState;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaStateHolder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Carries a vanilla mob's armature pose and attachments from the start of its draw to its posing and layers (1.21.1
// draws an entity straight from itself, with no render state to hold them).
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements VanillaStateHolder {
    @Unique
    private @Nullable VanillaState coolcatlib$vanilla;

    @Override
    public @Nullable VanillaState coolcatlib$vanilla() {
        return this.coolcatlib$vanilla;
    }

    @Override
    public void coolcatlib$setVanilla(@Nullable VanillaState state) {
        this.coolcatlib$vanilla = state;
    }
}
