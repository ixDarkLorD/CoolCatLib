package net.ixdarklord.coolcatcore.internal.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The tracked entities by id, to find who sees an entity (values are ChunkMap.TrackedEntity; see TrackedEntityAccessor).
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("entityMap")
    Int2ObjectMap<?> coolcatcore$getEntityMap();
}
