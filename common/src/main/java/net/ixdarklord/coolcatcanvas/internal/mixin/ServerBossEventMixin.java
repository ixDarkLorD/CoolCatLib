package net.ixdarklord.coolcatcanvas.internal.mixin;

import net.ixdarklord.coolcatcanvas.internal.bossbar.BossBarNetwork;
import net.ixdarklord.coolcatcanvas.internal.bossbar.BossBarStylePayload;
import net.ixdarklord.coolcatcanvas.internal.bossbar.StyledBossEvent;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.UUID;

/**
 * A boss bar's style ({@code CustomBossBars}): kept on the bar, and sent to each player right after the bar itself,
 * whenever they are shown it.
 */
@Mixin(ServerBossEvent.class)
public abstract class ServerBossEventMixin extends BossEvent implements StyledBossEvent {
    @Unique
    private @Nullable Identifier coolcatlib$style;
    @Unique
    private int coolcatlib$entityId = -1;

    @Shadow
    private boolean visible;

    @Shadow
    public abstract Collection<ServerPlayer> getPlayers();

    private ServerBossEventMixin(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) {
        super(id, name, color, overlay);
    }

    @Override
    public @Nullable Identifier coolcatlib$style() {
        return this.coolcatlib$style;
    }

    @Override
    public int coolcatlib$entityId() {
        return this.coolcatlib$entityId;
    }

    @Override
    public void coolcatlib$setStyle(@Nullable Identifier style, int entityId) {
        this.coolcatlib$style = style;
        this.coolcatlib$entityId = entityId;
        if (!this.visible) return;
        for (ServerPlayer player : this.getPlayers()) {
            if (Network.canPlayerReceive(player, BossBarStylePayload.TYPE)) Network.sendToPlayer(player, new BossBarStylePayload(this.getId(), style, entityId));
        }
    }

    @Inject(method = "addPlayer", at = @At("TAIL"))
    private void coolcatlib$sendStyle(ServerPlayer player, CallbackInfo ci) {
        if (this.visible) BossBarNetwork.send((ServerBossEvent) (Object) this, player);
    }

    @Inject(method = "setVisible", at = @At("TAIL"))
    private void coolcatlib$sendStyleAgain(boolean visible, CallbackInfo ci) {
        if (visible) for (ServerPlayer player : this.getPlayers()) BossBarNetwork.send((ServerBossEvent) (Object) this, player);
    }
}
