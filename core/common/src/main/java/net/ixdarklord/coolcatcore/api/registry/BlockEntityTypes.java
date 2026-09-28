package net.ixdarklord.coolcatcore.api.registry;

import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity types from common code (vanilla keeps their constructor private).
 * <pre>{@code
 * public static final RegistryEntry<BlockEntityType<CrateBlockEntity>> CRATE = BLOCK_ENTITIES.register("crate",
 *         () -> BlockEntityTypes.create(CrateBlockEntity::new, MyBlocks.CRATE.get()));
 * }</pre>
 */
public final class BlockEntityTypes {
    private BlockEntityTypes() {}

    /** A block entity type for these blocks; register it in {@code Registries.BLOCK_ENTITY_TYPE}. */
    public static <T extends BlockEntity> BlockEntityType<T> create(Factory<? extends T> factory, Block... blocks) {
        return CommonServices.get().createBlockEntityType(factory, blocks);
    }

    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }
}
