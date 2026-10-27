package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.common.RecipeEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * RecipeEvents.MATCH: the game's recipe lookups ask {@link #allows} (RecipeManagerMixin and the menus), and the
 * machines and menus that look recipes up say who is asking around their lookup ({@link #enter} / {@link #exit}), on
 * the server thread.
 */
public final class RecipeMatching {
    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();

    private RecipeMatching() {}

    public static void enter(@Nullable BlockEntity blockEntity, @Nullable Player player) {
        FRAME.set(new Frame(blockEntity, player, false, FRAME.get()));
    }

    /** Lookups until {@link #exit} aren't asked about: their answer is cached for everyone (the crafter's cache). */
    public static void enterUnasked() {
        FRAME.set(new Frame(null, null, true, FRAME.get()));
    }

    public static void exit() {
        Frame frame = FRAME.get();
        FRAME.set(frame == null ? null : frame.outer);
    }

    public static boolean allows(Level level, RecipeHolder<?> recipe) {
        if (!(level instanceof ServerLevel serverLevel) || !RecipeEvents.MATCH.hasListeners()) return true;
        Frame frame = FRAME.get();
        if (frame == null) return !RecipeEvents.MATCH.invoker().onMatch(serverLevel, recipe, null, null).isInterrupt();
        return frame.unasked || !RecipeEvents.MATCH.invoker().onMatch(serverLevel, recipe, frame.blockEntity, frame.player).isInterrupt();
    }

    private record Frame(@Nullable BlockEntity blockEntity, @Nullable Player player, boolean unasked, @Nullable Frame outer) {}
}
