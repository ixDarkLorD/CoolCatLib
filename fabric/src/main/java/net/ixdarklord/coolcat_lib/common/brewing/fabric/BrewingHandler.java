package net.ixdarklord.coolcat_lib.common.brewing.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BrewingHandler {
    public static void doBrew(Level level, BlockPos blockPos, NonNullList<ItemStack> contents, int[] inputIndexes) {
        ItemStack itemstack = contents.get(3);

        BrewingRecipeRegistry.brewPotions(contents, itemstack, inputIndexes);
        if (itemstack.getItem().hasCraftingRemainingItem()) {
            //noinspection DataFlowIssue
            ItemStack itemStack = itemstack.getItem().getCraftingRemainingItem().getDefaultInstance();
            itemstack.shrink(1);
            if (itemstack.isEmpty()) {
                itemstack = itemStack;
            } else {
                Containers.dropItemStack(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), itemStack);
            }
        }
        else itemstack.shrink(1);

        contents.set(3, itemstack);
        level.levelEvent(1035, blockPos, 0);
    }
}
