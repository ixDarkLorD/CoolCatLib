package net.ixdarklord.coolcatcore.api.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class ComponentItem extends Item {
    private final Identifier itemID;
    private final ComponentType componentType;
    public ComponentItem(Properties properties, ComponentType componentType) {
        super(properties);
        this.itemID = BuiltInRegistries.ITEM.getKey(this);
        this.componentType = componentType;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
        if (componentType.get() != null && !appendToName()) {
            tooltipComponents.accept(Component.literal("| ").withStyle(ChatFormatting.DARK_GRAY).append(componentType.get()));
        }
    }

    /**
     * Appends the component type to the tooltip's name line when {@link #appendToName()} is set.
     * The name line can no longer be edited from {@link #appendHoverText}, so each loader's client
     * tooltip event calls this with the full tooltip line list.
     */
    public static void onTooltip(ItemStack stack, List<Component> tooltipComponents) {
        if (!(stack.getItem() instanceof ComponentItem item) || tooltipComponents.isEmpty()) return;
        if (item.componentType.get() != null && item.appendToName()) {
            MutableComponent name = tooltipComponents.getFirst().copy().append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY).append(item.componentType.get()));
            tooltipComponents.set(0, name);
        }
    }

    public boolean appendToName() {
        return false;
    }

    public int getSplitterLength() {
        return Math.max(200, itemID.toString().length());
    }

    public boolean isShiftButtonNotPressed(@Nullable Consumer<Component> tooltipComponents) {
        if (!Minecraft.getInstance().hasShiftDown()) {
            if (tooltipComponents != null)
                tooltipComponents.accept(Component.literal("➤ ").withStyle(ChatFormatting.DARK_GRAY).append(Component.translatable("tooltip.coolcatcore.press.shift").withStyle(ChatFormatting.GRAY)));
            return true;
        }
        return false;
    }

    public static class ComponentType {
        public static ComponentType CRAFTING = new ComponentType(Component.translatable("tooltip.coolcatcore.component.crafting").withStyle(ChatFormatting.DARK_PURPLE));
        public static ComponentType TOOLS = new ComponentType(Component.translatable("tooltip.coolcatcore.component.tools").withStyle(ChatFormatting.DARK_PURPLE));
        public static ComponentType ABILITY = new ComponentType(Component.translatable("tooltip.coolcatcore.component.ability").withStyle(ChatFormatting.DARK_PURPLE));

        private final Component component;
        public ComponentType(Component component) {
            this.component = component;
        }
        public Component get() {
            return component;
        }
    }
}
