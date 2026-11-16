package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import org.jetbrains.annotations.Nullable;

/** Added to every living entity on the client (LivingEntityMixin): what it is drawn with this frame. */
public interface VanillaStateHolder {
    @Nullable VanillaState coolcatlib$vanilla();

    void coolcatlib$setVanilla(@Nullable VanillaState state);
}
