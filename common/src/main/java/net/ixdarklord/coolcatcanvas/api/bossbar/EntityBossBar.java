package net.ixdarklord.coolcatcanvas.api.bossbar;

import net.ixdarklord.coolcatcanvas.internal.bossbar.EntityBossBars;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * A boss bar that belongs to a living entity and keeps itself: its name is the entity's, its progress the entity's
 * health, and it shows to the players near it while it is to be shown. Make one from the server, {@link #show()} it,
 * and it runs by itself every tick until the entity is removed, or {@link #hide()} is called.
 * <pre>{@code
 * EntityBossBar bar = EntityBossBar.of(this)
 *         .style(MyMod.id("warden"))          // drawn by a renderer of that id on the client
 *         .color(BossBarColor.RED).overlay(BossBarOverlay.NOTCHED_10)
 *         .range(48).darkenScreen(true)
 *         .showWhen(boss -> !((MyBoss) boss).isAsleep())
 *         .show();
 * }</pre>
 * For a bar an entity drives itself (its own progress, phases, names), give it a {@link ServerBossEvent} of its own
 * and style it with {@link CustomBossBars#style}.
 */
public final class EntityBossBar {
    private final LivingEntity entity;
    private final ServerBossEvent event;
    private @Nullable Identifier style;
    private double range = 48.0;
    private Predicate<LivingEntity> showWhen = entity -> true;
    private boolean running;

    private EntityBossBar(LivingEntity entity) {
        this.entity = entity;
        this.event = new ServerBossEvent(entity.getUUID(), entity.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    }

    public static EntityBossBar of(LivingEntity entity) {
        return new EntityBossBar(entity);
    }

    /** The style it is drawn in on the client ({@code BossBarRenderers}); null for the game's own bar. */
    public EntityBossBar style(@Nullable Identifier style) {
        this.style = style;
        CustomBossBars.style(this.event, style, this.entity);
        return this;
    }

    public EntityBossBar color(BossEvent.BossBarColor color) {
        this.event.setColor(color);
        return this;
    }

    public EntityBossBar overlay(BossEvent.BossBarOverlay overlay) {
        this.event.setOverlay(overlay);
        return this;
    }

    public EntityBossBar darkenScreen(boolean darken) {
        this.event.setDarkenScreen(darken);
        return this;
    }

    public EntityBossBar playBossMusic(boolean music) {
        this.event.setPlayBossMusic(music);
        return this;
    }

    public EntityBossBar createWorldFog(boolean fog) {
        this.event.setCreateWorldFog(fog);
        return this;
    }

    /** How near, in blocks, a player must be to see it. 48 by default. */
    public EntityBossBar range(double blocks) {
        this.range = blocks;
        return this;
    }

    /** When it shows at all, asked every tick: a boss asleep, or not yet woken, need not show its bar. */
    public EntityBossBar showWhen(Predicate<LivingEntity> condition) {
        this.showWhen = condition;
        return this;
    }

    /** Starts it: from now on it keeps itself every tick, until the entity is gone or {@link #hide()}. */
    public EntityBossBar show() {
        if (!this.running) {
            this.running = true;
            EntityBossBars.add(this);
        }
        return this;
    }

    /** Stops it, and takes it off every screen. */
    public void hide() {
        this.running = false;
        this.event.removeAllPlayers();
    }

    public LivingEntity entity() {
        return this.entity;
    }

    /** The bar itself, for anything not covered here. */
    public ServerBossEvent event() {
        return this.event;
    }

    public @Nullable Identifier style() {
        return this.style;
    }

    /** One server tick of it; false once it is done. Called by CoolCatLib. */
    public boolean tick() {
        if (!this.running) return false;
        if (this.entity.isRemoved()) {
            this.hide();
            return false;
        }
        this.event.setName(this.entity.getDisplayName());
        this.event.setProgress(this.entity.getMaxHealth() > 0.0F ? this.entity.getHealth() / this.entity.getMaxHealth() : 0.0F);
        boolean shown = this.entity.isAlive() && this.showWhen.test(this.entity);
        for (ServerPlayer player : new ArrayList<>(this.event.getPlayers())) {
            if (!shown || player.level() != this.entity.level() || player.distanceTo(this.entity) > this.range || !player.isAlive()) {
                this.event.removePlayer(player);
            }
        }
        if (shown && this.entity.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceTo(this.entity) <= this.range) this.event.addPlayer(player);
            }
        }
        return true;
    }
}
