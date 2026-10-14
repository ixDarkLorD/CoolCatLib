package net.ixdarklord.coolcatcore.api.attachment;

import net.minecraft.server.level.ServerPlayer;

/**
 * Which players receive a {@link Attachment}'s value. Only players who can see the holder are ever asked: those tracking
 * an entity (plus the entity itself when it's a player), or those with a block entity's chunk loaded.
 * <pre>{@code
 * .sync(SyncPolicy.SELF)                                  // a player's own HUD value
 * .sync(SyncPolicy.ALL)                                   // everyone who sees the holder, e.g. for rendering
 * .sync(SyncPolicy.ALL.and((owner, player) -> player.hasPermissions(2)))
 * }</pre>
 */
@FunctionalInterface
public interface SyncPolicy {
    /** Never sent; the value lives on the server only. */
    SyncPolicy NONE = (owner, player) -> false;
    /** Only the player the value belongs to. Nobody, for anything but a player. */
    SyncPolicy SELF = (owner, player) -> owner == player;
    /** Players who see the holder, but not the player it belongs to. */
    SyncPolicy TRACKING = (owner, player) -> owner != player;
    /** Everyone who sees the holder, the player it belongs to included. */
    SyncPolicy ALL = (owner, player) -> true;

    /**
     * @param owner  the entity or block entity holding the value
     * @param player a player who can see it
     */
    boolean shouldSync(Object owner, ServerPlayer player);

    default SyncPolicy and(SyncPolicy other) {
        return (owner, player) -> this.shouldSync(owner, player) && other.shouldSync(owner, player);
    }

    default SyncPolicy or(SyncPolicy other) {
        return (owner, player) -> this.shouldSync(owner, player) || other.shouldSync(owner, player);
    }
}
