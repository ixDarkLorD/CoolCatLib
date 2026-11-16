package net.ixdarklord.coolcatcanvas.internal.bossbar;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** What {@code ServerBossEventMixin} adds to every server boss bar: the style it is drawn in, and its entity. */
public interface StyledBossEvent {
    @Nullable Identifier coolcatlib$style();

    int coolcatlib$entityId();

    void coolcatlib$setStyle(@Nullable Identifier style, int entityId);
}
