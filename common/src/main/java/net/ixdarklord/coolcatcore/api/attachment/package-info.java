/**
 * Attachments: typed data any entity (players included), block entity or item stack can carry.
 * <p>
 * Each mod declares its attachments through its own {@link net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry},
 * which stores them under the mod's id ({@code <modid>:attachments}). An attachment saves with its holder, syncs by its
 * {@link net.ixdarklord.coolcatcore.api.attachment.SyncPolicy}, and is read and written straight from the attachment:
 * <pre>{@code
 * public static final AttachmentRegistry ATTACHMENTS = AttachmentRegistry.create(MOD_ID);
 * public static final Attachment<Integer> MADNESS = ATTACHMENTS.builder("madness", Codec.INT, 0)
 *         .sync(SyncPolicy.SELF).copyOnDeath().build();
 *
 * // While the mod initialises:
 * ATTACHMENTS.register();
 *
 * MADNESS.update(player, madness -> madness + 1);   // server: saved, and sent to that player at the end of the tick
 * int madness = MADNESS.get(minecraft.player);        // client: the synced value
 * }</pre>
 * Changes can be watched, changed or cancelled with
 * {@link net.ixdarklord.coolcatcore.api.event.v2.common.AttachmentEvents}. Related packages:
 * {@link net.ixdarklord.coolcatcore.api.container} (inventories, including
 * {@link net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry#container container attachments}),
 * {@link net.ixdarklord.coolcatcore.api.handler} (handlers holders hand out, cached and invalidated) and
 * {@link net.ixdarklord.coolcatcore.api.block} (block entity base classes).
 */
package net.ixdarklord.coolcatcore.api.attachment;
