package net.ixdarklord.coolcatcore.api.handler;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * An entity or block entity that hands out its own handlers; {@link HandlerType#find} asks it before any registered
 * provider. {@link net.ixdarklord.coolcatcore.api.block.ExtendedBlockEntity} implements it with a {@link HandlerMap}.
 */
public interface HandlerProvider {
    /**
     * @param side the face asked through, or {@code null} for the holder as a whole (always {@code null} for most
     *             entity lookups)
     * @return the handler, or {@code null} if this holder has none of that type on that side
     */
    <T> @Nullable T getHandler(HandlerType<T> type, @Nullable Direction side);
}
