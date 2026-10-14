package net.ixdarklord.coolcatcore.api.network;


/**
 * Handles a received payload. Called on the game's main thread; {@link PacketContext#queue} defers work to the next
 * tick if needed.
 */
@FunctionalInterface
public interface PayloadReceiver<T extends CustomPacketPayload> {
    void receive(T payload, PacketContext context);
}
