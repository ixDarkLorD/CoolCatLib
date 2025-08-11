package net.ixdarklord.coolcatlib.api.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Handles applying brewing recipes to a brewing stand's inventory.
 * <p>
 * This method:
 * <ul>
 *   <li>Brews all specified input slots using the current ingredient.</li>
 *   <li>Consumes one ingredient item, handling any remaining container item.</li>
 *   <li>Drops leftover containers into the world if they cannot fit in the ingredient slot.</li>
 *   <li>Triggers the vanilla brewing completion event (ID 1035).</li>
 * </ul>
 * Assumes slot {@code 3} is the ingredient slot and other indices come from {@code inputIndexes}.
 */
public final class BrewingHandler {

    /**
     * Performs a brewing operation for the given brewing stand inventory.
     *
     * @param level        World containing the brewing stand
     * @param blockPos     Position of the brewing stand
     * @param contents     Brewing stand inventory (slot 3 = ingredient)
     * @param inputIndexes Slot indices for brewing inputs (e.g., {0, 1, 2})
     */
    public static void doBrew(Level level, BlockPos blockPos, NonNullList<ItemStack> contents, int[] inputIndexes) {
        ItemStack itemstack = contents.get(3);

        BrewingRecipeRegistry.getInstance().brewPotions(contents, itemstack, inputIndexes);

        if (itemstack.getItem().hasCraftingRemainingItem()) {
            ItemStack itemStack = Objects.requireNonNull(itemstack.getItem().getCraftingRemainingItem()).getDefaultInstance();
            itemstack.shrink(1);
            if (itemstack.isEmpty()) {
                itemstack = itemStack;
            } else {
                Containers.dropItemStack(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), itemStack);
            }
        } else {
            itemstack.shrink(1);
        }

        contents.set(3, itemstack);
        level.levelEvent(1035, blockPos, 0);
    }
}