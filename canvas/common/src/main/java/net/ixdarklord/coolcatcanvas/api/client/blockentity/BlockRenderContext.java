package net.ixdarklord.coolcatcanvas.api.client.blockentity;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@link BlockRenderer}'s {@code extract} (and its layers') can use besides the block entity: the camera, the
 * game's model resolvers, and eased transitions. One is reused for every block entity it draws, so use it during the
 * call and don't keep it.
 */
public final class BlockRenderContext {
    private final BlockEntityRendererProvider.Context provider;
    private Vec3 camera = Vec3.ZERO;
    private float partialTicks;
    private @Nullable Level level;

    BlockRenderContext(BlockEntityRendererProvider.Context provider) {
        this.provider = provider;
    }

    void set(Vec3 camera, float partialTicks, @Nullable Level level) {
        this.camera = camera;
        this.partialTicks = partialTicks;
        this.level = level;
    }

    /** What the renderer was made with: the model resolvers, entity models, font and sprites. */
    public BlockEntityRendererProvider.Context provider() {
        return this.provider;
    }

    public ItemModelResolver items() {
        return this.provider.itemModelResolver();
    }

    public Font font() {
        return this.provider.font();
    }

    public Vec3 camera() {
        return this.camera;
    }

    public float partialTicks() {
        return this.partialTicks;
    }

    public @Nullable Level level() {
        return this.level;
    }

    /** Ticks since this game time, with the partial tick (0 without a level). */
    public float ticksSince(long gameTime) {
        return this.level == null ? 0.0F : Math.max(0.0F, this.level.getGameTime() - gameTime + this.partialTicks);
    }

    /**
     * How far through a change it is that started at {@code changedAt} and takes {@code ticks}, eased in and out: 0 to 1
     * as something turns on ({@code on}), 1 to 0 as it turns off. For a light rising as a block turns on, a lid
     * opening.
     */
    public float transition(long changedAt, float ticks, boolean on) {
        float since = Mth.clamp(this.ticksSince(changedAt) / Math.max(ticks, 1.0E-4F), 0.0F, 1.0F);
        float eased = since * since * (3.0F - 2.0F * since);
        return on ? eased : 1.0F - eased;
    }
}
