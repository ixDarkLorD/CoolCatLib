package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.LootRolls;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

// LootEvents.MODIFY: a loaded loot table's id, which the table itself doesn't know (LootRolls).
@Mixin(LootDataManager.class)
public abstract class LootDataManagerMixin implements LootRolls.Ids {
    @Shadow
    private Map<LootDataId<?>, ?> elements;

    @Override
    public @Nullable ResourceLocation coolcatlib$idOf(LootTable table) {
        for (Map.Entry<LootDataId<?>, ?> entry : this.elements.entrySet()) {
            if (entry.getValue() == table && entry.getKey().type() == LootDataType.TABLE) return entry.getKey().location();
        }
        return null;
    }
}
