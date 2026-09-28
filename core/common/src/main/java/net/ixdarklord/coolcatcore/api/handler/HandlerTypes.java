package net.ixdarklord.coolcatcore.api.handler;

import net.ixdarklord.coolcatcore.api.container.ContainerItem;
import net.ixdarklord.coolcatcore.api.container.ItemContainers;
import net.ixdarklord.coolcatcore.api.container.SidedContainerView;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The handler types CoolCatLib: Core provides.
 */
public final class HandlerTypes {
    /**
     * An inventory. Through a face, only what that face reaches (a {@link SidedContainerView}). Found for:
     * <ul>
     *     <li>{@link net.ixdarklord.coolcatcore.api.block.ExtendedContainerBlockEntity}s and other providers,</li>
     *     <li>any block entity that's a vanilla {@link Container} (double chests as one), and blocks that are
     *     {@link WorldlyContainerHolder}s (composters),</li>
     *     <li>entities that are containers (chest minecarts),</li>
     *     <li>stacks of {@link ContainerItem}s.</li>
     * </ul>
     */
    public static final HandlerType<Container> CONTAINER = HandlerType.create(CoolCatCore.rl("container"), Container.class)
            .registerBlockFallback(HandlerTypes::blockContainer)
            .registerEntityFallback((entity, side) -> entity instanceof Container container ? container : null)
            .registerItemFallback(stack -> stack.getItem() instanceof ContainerItem ? ItemContainers.of(stack) : null);

    private HandlerTypes() {}

    private static @Nullable Container blockContainer(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        Container container;
        if (state.getBlock() instanceof WorldlyContainerHolder holder) {
            container = holder.getContainer(state, level, pos);
        } else if (blockEntity instanceof Container found) {
            container = found instanceof ChestBlockEntity && state.getBlock() instanceof ChestBlock chest
                    ? ChestBlock.getContainer(chest, state, level, pos, true) : found;
        } else {
            return null;
        }
        if (container == null) return null;
        return side != null && container instanceof WorldlyContainer worldly ? new SidedContainerView(worldly, side) : container;
    }
}
