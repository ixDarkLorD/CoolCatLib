package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.bossbar.BossBarClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * Custom boss bars ({@code BossBarRenderers}) in place of the game's, stacked with any others the game draws in its
 * own way. Only while one is up: otherwise the game draws as it always does.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Map<UUID, LerpingBossEvent> events;

    @Unique
    private long coolcatlib$tickedAt = Long.MIN_VALUE;

    @Shadow
    private void extractBar(GuiGraphicsExtractor graphics, int x, int y, BossEvent event) {
        throw new AssertionError();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$customBars(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        // Bars ease and drain once a tick, whether or not one is custom now (one may be soon).
        long now = this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
        if (now != this.coolcatlib$tickedAt) {
            this.coolcatlib$tickedAt = now;
            BossBarClient.tick(this.events.values());
        }
        if (this.events.values().stream().noneMatch(BossBarClient::custom)) return;
        ci.cancel();
        graphics.nextStratum();
        int y = 3;
        for (LerpingBossEvent event : this.events.values()) {
            if (BossBarClient.custom(event)) {
                y += BossBarClient.draw(graphics, event, y);
            } else {
                // As the game draws its own: the name, and the bar under it.
                this.extractBar(graphics, graphics.guiWidth() / 2 - 91, y + 9, event);
                Component name = event.getName();
                graphics.text(this.minecraft.font, name, graphics.guiWidth() / 2 - this.minecraft.font.width(name) / 2, y, -1);
                y += 10 + 9;
            }
            if (y >= graphics.guiHeight() / 3) break;
        }
    }
}
