package net.ixdarklord.coolcat_lib.common.item;

import net.ixdarklord.coolcat_lib.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ComponentItem extends Item {
    private final ResourceLocation itemID;
    private final ComponentType componentType;
    public ComponentItem(Properties properties, ComponentType componentType) {
        super(properties);
        this.itemID = Services.PLATFORM.getItemLocation(this);
        this.componentType = componentType;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag isAdvanced) {
        if (componentType.get() != null) {
            if (appendToName()) {
                MutableComponent name = tooltipComponents.get(0).copy().append(new TextComponent(" | ").withStyle(ChatFormatting.DARK_GRAY).append(componentType.get()));
                tooltipComponents.set(0, name);
            } else tooltipComponents.add(new TextComponent("| ").withStyle(ChatFormatting.DARK_GRAY).append(componentType.get()));
        }
    }

    public boolean appendToName() {
        return false;
    }

    public int getSplitterLength() {
        return Math.max(200, itemID.toString().length());
    }

    public boolean isShiftButtonNotPressed(@Nullable List<Component> tooltipComponents) {
        if (!Screen.hasShiftDown()) {
            if (tooltipComponents != null)
                tooltipComponents.add(new TextComponent("➤ ").withStyle(ChatFormatting.DARK_GRAY).append(new TranslatableComponent("tooltip.coolcat_lib.press.shift").withStyle(ChatFormatting.GRAY)));
            return true;
        }
        return false;
    }

    public static class ComponentType {
        public static ComponentType CRAFTING = new ComponentType(new TranslatableComponent("tooltip.coolcat_lib.component.crafting").withStyle(ChatFormatting.DARK_PURPLE));
        public static ComponentType TOOLS = new ComponentType(new TranslatableComponent("tooltip.coolcat_lib.component.tools").withStyle(ChatFormatting.DARK_PURPLE));
        public static ComponentType ABILITY = new ComponentType(new TranslatableComponent("tooltip.coolcat_lib.component.ability").withStyle(ChatFormatting.DARK_PURPLE));

        private final Component component;
        public ComponentType(Component component) {
            this.component = component;
        }
        public Component get() {
            return component;
        }
    }
}
