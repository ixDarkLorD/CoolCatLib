package net.ixdarklord.coolcatlib.api.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ComponentItem extends Item {
    private final ResourceLocation itemID;
    private final ComponentType componentType;
    public ComponentItem(Properties properties, ComponentType componentType) {
        super(properties);
        this.itemID = BuiltInRegistries.ITEM.getKey(this);
        this.componentType = componentType;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        if (componentType.get() != null) {
            if (appendToName()) {
                MutableComponent name = tooltipComponents.getFirst().copy().append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY).append(componentType.get()));
                tooltipComponents.set(0, name);
            } else tooltipComponents.add(Component.literal("| ").withStyle(ChatFormatting.DARK_GRAY).append(componentType.get()));
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
                tooltipComponents.add(Component.literal("➤ ").withStyle(ChatFormatting.DARK_GRAY).append(Component.translatable("tooltip.coolcat_lib.press.shift").withStyle(ChatFormatting.GRAY)));
            return true;
        }
        return false;
    }

    public static class ComponentType {
        public static ComponentType CRAFTING = new ComponentType(Component.translatable("tooltip.coolcat_lib.component.crafting").withStyle(ChatFormatting.DARK_PURPLE));
        public static ComponentType TOOLS = new ComponentType(Component.translatable("tooltip.coolcat_lib.component.tools").withStyle(ChatFormatting.DARK_PURPLE));
        public static ComponentType ABILITY = new ComponentType(Component.translatable("tooltip.coolcat_lib.component.ability").withStyle(ChatFormatting.DARK_PURPLE));

        private final Component component;
        public ComponentType(Component component) {
            this.component = component;
        }
        public Component get() {
            return component;
        }
    }
}
