package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaState;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaStateHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Carries a vanilla mob's armature pose and attachments from extraction to drawing.
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements VanillaStateHolder {
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
