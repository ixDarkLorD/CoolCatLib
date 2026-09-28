package net.ixdarklord.coolcatcanvas.api.sky;

import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcanvas.internal.sky.SkyboxPayload;
import net.ixdarklord.coolcatcanvas.internal.sky.SkyboxPayload.Action;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Drives players' client-side skyboxes from the server, by the id they were registered under with
 * {@code Skyboxes.register} (or a resource pack skybox's file id). Clients without that skybox ignore the command.
 * <p>
 * The static methods address one player; for several, pick them first:
 * <pre>{@code
 * SkyboxControl.toggle(player, MyMod.id("blood_moon"));
 * SkyboxControl.to(players).enableFor(MyMod.id("blood_moon"), 1200);
 * SkyboxControl.inLevel(serverLevel).setEnabled(MyMod.id("aurora"), true);
 * SkyboxControl.everyone(server).disable(MyMod.id("aurora"));
 * }</pre>
 * Server state isn't kept: a client that relogs starts over, so resend what should persist (on join, for instance).
 * A skybox that enables itself in its dimensions keeps doing so; these commands are for skyboxes left to code.
 */
public final class SkyboxControl {
    private SkyboxControl() {}

    // ---- Choosing players ----

    public static Target to(ServerPlayer... players) {
        List<ServerPlayer> list = List.of(players);
        return new Target(() -> list);
    }

    /** The given players, as they are when each command is sent. */
    public static Target to(Collection<? extends ServerPlayer> players) {
        return new Target(() -> players);
    }

    /** Everyone in a dimension when each command is sent. */
    public static Target inLevel(ServerLevel level) {
        return new Target(level::players);
    }

    /** Everyone online when each command is sent. */
    public static Target everyone(MinecraftServer server) {
        return new Target(() -> server.getPlayerList().getPlayers());
    }

    // ---- One player ----

    public static void enable(ServerPlayer player, ResourceLocation skybox) {
        to(player).enable(skybox);
    }

    public static void disable(ServerPlayer player, ResourceLocation skybox) {
        to(player).disable(skybox);
    }

    /** Turns it on if the player's client has it off, and off if on. */
    public static void toggle(ServerPlayer player, ResourceLocation skybox) {
        to(player).toggle(skybox);
    }

    public static void setEnabled(ServerPlayer player, ResourceLocation skybox, boolean enabled) {
        to(player).setEnabled(skybox, enabled);
    }

    /** Fades in, stays for {@code ticks}, then fades out. */
    public static void enableFor(ServerPlayer player, ResourceLocation skybox, int ticks) {
        to(player).enableFor(skybox, ticks);
    }

    public static void enableInstantly(ServerPlayer player, ResourceLocation skybox) {
        to(player).enableInstantly(skybox);
    }

    public static void disableInstantly(ServerPlayer player, ResourceLocation skybox) {
        to(player).disableInstantly(skybox);
    }

    public static void setLayerVisible(ServerPlayer player, ResourceLocation skybox, String layer, boolean visible) {
        to(player).setLayerVisible(skybox, layer, visible);
    }

    /** Moves a layer's opacity multiplier to {@code alpha} over {@code ticks}, 0 for instantly. */
    public static void setLayerAlpha(ServerPlayer player, ResourceLocation skybox, String layer, float alpha, int ticks) {
        to(player).setLayerAlpha(skybox, layer, alpha, ticks);
    }

    /** Moves a layer's {@code SkyParams[index]} to {@code values} (at most 4) over {@code ticks}, 0 for instantly. */
    public static void setLayerParam(ServerPlayer player, ResourceLocation skybox, String layer, int index, int ticks, float... values) {
        to(player).setLayerParam(skybox, layer, index, ticks, values);
    }

    /** Undoes what code or commands changed on a layer: visible, full opacity, the definition's tint and params. */
    public static void resetLayer(ServerPlayer player, ResourceLocation skybox, String layer) {
        to(player).resetLayer(skybox, layer);
    }

    /**
     * Players to send skybox commands to: the same commands as the static methods, each sent to all of them. Players
     * are looked up again for every command, so a target can be kept and reused.
     */
    public static final class Target {
        private final Supplier<? extends Collection<? extends ServerPlayer>> players;

        private Target(Supplier<? extends Collection<? extends ServerPlayer>> players) {
            this.players = players;
        }

        public Target enable(ResourceLocation skybox) {
            return this.send(skybox, Action.ENABLE, 0, "", 0);
        }

        public Target disable(ResourceLocation skybox) {
            return this.send(skybox, Action.DISABLE, 0, "", 0);
        }

        /** Each client turns it on if it's off there, and off if it's on: players can end up differing. */
        public Target toggle(ResourceLocation skybox) {
            return this.send(skybox, Action.TOGGLE, 0, "", 0);
        }

        public Target setEnabled(ResourceLocation skybox, boolean enabled) {
            return this.send(skybox, Action.SET_ENABLED, 0, "", 0, enabled ? 1.0F : 0.0F);
        }

        /** Fades in, stays for {@code ticks}, then fades out. */
        public Target enableFor(ResourceLocation skybox, int ticks) {
            return this.send(skybox, Action.ENABLE_FOR, ticks, "", 0);
        }

        public Target enableInstantly(ResourceLocation skybox) {
            return this.send(skybox, Action.ENABLE_INSTANTLY, 0, "", 0);
        }

        public Target disableInstantly(ResourceLocation skybox) {
            return this.send(skybox, Action.DISABLE_INSTANTLY, 0, "", 0);
        }

        public Target setLayerVisible(ResourceLocation skybox, String layer, boolean visible) {
            return this.send(skybox, Action.SET_LAYER_VISIBLE, 0, layer, 0, visible ? 1.0F : 0.0F);
        }

        /** Moves a layer's opacity multiplier to {@code alpha} over {@code ticks}, 0 for instantly. */
        public Target setLayerAlpha(ResourceLocation skybox, String layer, float alpha, int ticks) {
            return this.send(skybox, Action.SET_LAYER_ALPHA, ticks, layer, 0, alpha);
        }

        /** Moves a layer's {@code SkyParams[index]} to {@code values} (at most 4) over {@code ticks}, 0 for instantly. */
        public Target setLayerParam(ResourceLocation skybox, String layer, int index, int ticks, float... values) {
            if (index < 0 || index > 3) throw new IllegalArgumentException("Sky layer params go from 0 to 3, got " + index);
            return this.send(skybox, Action.SET_LAYER_PARAM, ticks, layer, index, values);
        }

        /** Undoes what code or commands changed on a layer: visible, full opacity, the definition's tint and params. */
        public Target resetLayer(ResourceLocation skybox, String layer) {
            return this.send(skybox, Action.RESET_LAYER, 0, layer, 0);
        }

        // One payload, sent to each player.
        private Target send(ResourceLocation skybox, Action action, int ticks, String layer, int index, float... values) {
            SkyboxPayload payload = new SkyboxPayload(skybox, action, Math.max(0, ticks), layer, index, values);
            for (ServerPlayer player : this.players.get()) Network.sendToPlayer(player, payload);
            return this;
        }
    }
}
