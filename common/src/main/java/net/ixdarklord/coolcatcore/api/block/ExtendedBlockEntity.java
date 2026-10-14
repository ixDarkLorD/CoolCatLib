package net.ixdarklord.coolcatcore.api.block;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentHolder;
import net.ixdarklord.coolcatcore.api.handler.HandlerMap;
import net.ixdarklord.coolcatcore.api.handler.HandlerProvider;
import net.ixdarklord.coolcatcore.api.handler.HandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A block entity with {@link Attachment} data, handlers and ticking made simple. Its data saves and syncs on its own
 * (every block entity's does; this class adds shortcuts), so a typical subclass is only its logic:
 * <pre>{@code
 * public class GeneratorBlockEntity extends ExtendedBlockEntity {
 *     public static final Attachment<Integer> ENERGY = MyMod.ATTACHMENTS.builder("energy", Codec.INT, 0)
 *             .sync(SyncPolicy.ALL).keepOnDrop().build();
 *
 *     public GeneratorBlockEntity(BlockPos pos, BlockState state) {
 *         super(MyBlockEntities.GENERATOR.get(), pos, state);
 *         this.handlers.addSided(MyHandlers.ENERGY, side -> side == Direction.UP ? null : this.storage);
 *     }
 *
 *     @Override
 *     protected void serverTick(ServerLevel level) {
 *         this.update(ENERGY, energy -> Math.min(energy + 1, 1000));
 *     }
 * }
 *
 * // In the block: public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> type)
 * //                   { return ExtendedBlockEntity.ticker(type, MyBlockEntities.GENERATOR.get()); }
 * }</pre>
 * For an inventory, extend {@link ExtendedContainerBlockEntity}.
 */
public abstract class ExtendedBlockEntity extends BlockEntity implements HandlerProvider {
    /** What {@link #getHandler} hands out; add to it in the constructor. */
    protected final HandlerMap handlers = new HandlerMap();

    protected ExtendedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public AttachmentHolder attachments() {
        return AttachmentHolder.of(this);
    }

    public <T> T get(Attachment<T> attachment) {
        return this.attachments().get(attachment);
    }

    public <T> void set(Attachment<T> attachment, T value) {
        this.attachments().set(attachment, value);
    }

    public <T> T update(Attachment<T> attachment, UnaryOperator<T> function) {
        return this.attachments().update(attachment, function);
    }

    public <T> void modify(Attachment<T> attachment, Consumer<T> mutator) {
        this.attachments().modify(attachment, mutator);
    }

    @Override
    public <T> @Nullable T getHandler(HandlerType<T> type, @Nullable Direction side) {
        return this.handlers.get(type, side);
    }

    /** Tells handler caches at this position to look again, after changing what {@link #getHandler} hands out. */
    public void invalidateHandlers() {
        if (this.level != null) HandlerType.invalidate(this.level, this.worldPosition);
    }

    /** Runs every tick on the server, when the block uses {@link #ticker}. */
    protected void serverTick(ServerLevel level) {
    }

    /** Runs every tick on clients, when the block uses {@link #ticker}. */
    protected void clientTick(Level level) {
    }

    /**
     * The ticker for a block's {@code getTicker}: ticks {@code expected} block entities through
     * {@link #serverTick}/{@link #clientTick}, and nothing else.
     */
    public static <T extends BlockEntity> @Nullable BlockEntityTicker<T> ticker(BlockEntityType<T> type, BlockEntityType<? extends ExtendedBlockEntity> expected) {
        if (type != expected) return null;
        return (level, pos, state, blockEntity) -> {
            ExtendedBlockEntity self = (ExtendedBlockEntity) blockEntity;
            if (level instanceof ServerLevel server) self.serverTick(server);
            else self.clientTick(level);
        };
    }
}
