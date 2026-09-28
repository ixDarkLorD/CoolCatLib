package net.ixdarklord.coolcatcore.internal.platform.fabric;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.ixdarklord.coolcatcore.api.container.SidedContainerView;
import net.ixdarklord.coolcatcore.api.handler.HandlerTypes;
import net.minecraft.world.Container;

// Exposes CoolCatLib: Core containers to Fabric's Transfer API, so other mods' pipes and tools reach them. Block entities
// that are containers (ExtendedContainerBlockEntity included) are already covered by Fabric's own container fallback;
// this adds containers that blocks hand out through HandlerTypes.CONTAINER. (Fabric API for 1.20.1 has no item lookup
// for item storages, so ContainerItem stacks are only reached through HandlerTypes.CONTAINER.)
public final class FabricTransferCompat {
    private FabricTransferCompat() {}

    public static void register() {
        ItemStorage.SIDED.registerFallback((level, pos, state, blockEntity, side) -> {
            if (blockEntity instanceof Container) return null;
            Container container = HandlerTypes.CONTAINER.find(level, pos, state, blockEntity, side);
            if (container == null) return null;
            if (container instanceof SidedContainerView view) return InventoryStorage.of(view.getContainer(), view.getSide());
            return InventoryStorage.of(container, side);
        });
    }
}
