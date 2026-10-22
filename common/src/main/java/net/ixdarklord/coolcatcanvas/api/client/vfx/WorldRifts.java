package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.internal.client.vfx.RiftManager;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * Rifts: tears in the air, drawn by a shader ({@link VfxMaterial#RIFT_SHADER}) and run through three acts. A rift
 * <b>opens</b> (a flash, a jagged slit that widens, its opening sound, a shake of the camera nearby), stays <b>open</b>
 * (its edge crawls and pulses, motes are drawn into it, its ambient sound loops), and <b>closes</b> (it narrows to a
 * line and snaps shut with a last flash and its closing sound). It turns to face whoever looks at it, unless it is held
 * facing one way ({@link Rift#facing}), as a portal set in a wall is.
 * <pre>{@code
 * Rift rift = WorldRifts.open(RiftSpec.DEFAULT, pos);          // stays until closed
 * rift.close();
 *
 * WorldRifts.open(RiftSpec.DEFAULT, pos).facing(new Vec3(0, 0, 1));   // a fixed face, looking south
 *
 * WorldRifts.open(RiftSpec.DEFAULT, pos, 100);                 // closes by itself after five seconds open
 *
 * RiftSpec ember = RiftSpec.builder().size(3, 5).color(0xFF7030).timing(30, 12)
 *         .sounds(MySounds.TEAR, MySounds.HUM, MySounds.SNAP).build();
 * WorldRifts.register(MyMod.id("ember_rift"), ember);          // so the server can open it by name
 * }</pre>
 * From the server, {@code RiftControl.open(level, MyMod.id("ember_rift"), pos, ticks)} opens a registered rift for
 * everyone in range and hands back the id to close it with.
 */
public final class WorldRifts {
    /** The spec the server can always ask for: {@link RiftSpec#DEFAULT}. */
    public static final ResourceLocation DEFAULT = CoolCatCanvas.rl("rift");

    private WorldRifts() {}

    /** Names a spec, so the server can open it. Client code only. */
    public static void register(ResourceLocation id, RiftSpec spec) {
        RiftManager.register(id, spec);
    }

    public static Optional<RiftSpec> spec(ResourceLocation id) {
        return Optional.ofNullable(RiftManager.spec(id));
    }

    /** Opens a rift that stays until it is closed. */
    public static Rift open(RiftSpec spec, Vec3 position) {
        return RiftManager.open(UUID.randomUUID(), spec, position, -1);
    }

    /**
     * Opens a rift that closes by itself.
     *
     * @param openTicks how long it stays fully open before it begins to close
     */
    public static Rift open(RiftSpec spec, Vec3 position, int openTicks) {
        return RiftManager.open(UUID.randomUUID(), spec, position, openTicks);
    }

    /** The rift the server opened under an id, if it is still there. */
    public static Optional<Rift> get(UUID id) {
        return Optional.ofNullable(RiftManager.get(id));
    }

    /** Closes every rift at once, without their closing act. */
    public static void clear() {
        RiftManager.clear();
    }
}
