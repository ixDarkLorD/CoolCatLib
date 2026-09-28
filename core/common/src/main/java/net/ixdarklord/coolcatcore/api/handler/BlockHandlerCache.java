package net.ixdarklord.coolcatcore.api.handler;

import net.ixdarklord.coolcatcore.internal.handler.HandlerCaches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One position's handler, looked up once and kept until it may have changed: when the block state there changes,
 * when the block entity is removed or replaced (chunk unloads included), or when
 * {@link HandlerType#invalidate(Level, BlockPos)} is called for the position. Made by {@link HandlerType#createCache}.
 * <p>
 * The invalidation callback runs for removed block entities and explicit invalidations; a changed block state is
 * noticed by the next {@link #get()}.
 */
public final class BlockHandlerCache<T> {
    private final HandlerType<T> type;
    private final Level level;
    private final BlockPos pos;
    private final @Nullable Direction side;
    private final @Nullable Runnable onInvalidate;
    private boolean valid;
    private boolean registered;
    private @Nullable T handler;
    private @Nullable BlockState state;
    private @Nullable BlockEntity blockEntity;

    BlockHandlerCache(HandlerType<T> type, Level level, BlockPos pos, @Nullable Direction side, @Nullable Runnable onInvalidate) {
        this.type = type;
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.onInvalidate = onInvalidate;
    }

    /** The handler, or {@code null} if there's none (or the position isn't loaded). */
    public @Nullable T get() {
        if (!this.level.isLoaded(this.pos)) {
            this.valid = false;
            this.handler = null;
            return null;
        }
        BlockState current = this.level.getBlockState(this.pos);
        if (this.valid && current == this.state && (this.blockEntity == null || !this.blockEntity.isRemoved())) return this.handler;

        this.state = current;
        this.blockEntity = current.hasBlockEntity() ? this.level.getBlockEntity(this.pos) : null;
        this.handler = this.type.find(this.level, this.pos, current, this.blockEntity, this.side);
        this.valid = true;
        if (!this.registered) {
            this.registered = true;
            HandlerCaches.register(this.level, this.pos, this);
        }
        return this.handler;
    }

    /** Drops the cached handler; the next {@link #get()} looks again. Runs the invalidation callback. */
    public void invalidate() {
        this.valid = false;
        this.registered = false;
        this.handler = null;
        this.blockEntity = null;
        if (this.onInvalidate != null) this.onInvalidate.run();
    }

    public HandlerType<T> type() {
        return this.type;
    }

    public Level level() {
        return this.level;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public @Nullable Direction side() {
        return this.side;
    }
}
