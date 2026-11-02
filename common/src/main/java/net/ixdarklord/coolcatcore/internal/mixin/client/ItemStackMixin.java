package net.ixdarklord.coolcatcore.internal.mixin.client;

import net.ixdarklord.coolcatcore.api.event.v2.client.ItemTooltipEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// ItemTooltipEvents.APPEND: at the return of getTooltipLines, after Forge's tooltip event and Fabric's tooltip callback
// (both fired before it returns). The high priority puts it after other mods' injections at the same place. A list
// that isn't a plain mutable one is left alone.
@Mixin(value = ItemStack.class, priority = 2000)
public abstract class ItemStackMixin {
    @Unique
    private static final TextColor coolcatcore$DARK_GRAY = TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY);

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void coolcatcore$appendTooltip(@Nullable Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        if (ItemTooltipEvents.APPEND.hasListeners() && cir.getReturnValue() instanceof ArrayList<Component> lines) {
            // The game's advanced lines (durability, the item's id, its NBT tag count) stay at the bottom, under what
            // the listeners add, as they are under the item's own lines.
            List<Component> advanced = flag.isAdvanced() ? coolcatcore$takeAdvanced(lines) : List.of();
            ItemTooltipEvents.APPEND.invoker().append((ItemStack) (Object) this, player, flag, lines);
            lines.addAll(advanced);
        }
    }

    // Takes the advanced lines out of the tooltip: the id, a dark gray literal, with the durability just above it and
    // the NBT tag count just below, when they are there. Nothing when the id isn't found.
    @Unique
    private List<Component> coolcatcore$takeAdvanced(List<Component> lines) {
        String id = BuiltInRegistries.ITEM.getKey(((ItemStack) (Object) this).getItem()).toString();
        for (int i = lines.size() - 1; i > 0; i--) {
            Component line = lines.get(i);
            if (!(line.getContents() instanceof LiteralContents text) || !id.equals(text.text())
                    || !coolcatcore$DARK_GRAY.equals(line.getStyle().getColor())) {
                continue;
            }
            int from = coolcatcore$isKey(lines.get(i - 1), "item.durability") ? i - 1 : i;
            int to = i + 1 < lines.size() && coolcatcore$isKey(lines.get(i + 1), "item.nbt_tags") ? i + 2 : i + 1;
            List<Component> taken = lines.subList(from, to);
            List<Component> advanced = new ArrayList<>(taken);
            taken.clear();
            return advanced;
        }
        return List.of();
    }

    @Unique
    private static boolean coolcatcore$isKey(Component line, String key) {
        return line.getContents() instanceof TranslatableContents translatable && key.equals(translatable.getKey());
    }
}
