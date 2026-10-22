package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import org.jetbrains.annotations.Nullable;

/** Added to every entity render state (EntityRenderStateMixin). */
public interface VanillaStateHolder {
    @Nullable VanillaState coolcatlib$vanilla();

    void coolcatlib$setVanilla(@Nullable VanillaState state);
}
