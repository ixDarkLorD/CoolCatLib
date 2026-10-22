package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Items drawn with an armature. The model comes from the item's model file in a resource pack,
 * {@code assets/<namespace>/items/<item>.json}:
 * <pre>{@code
 * {
 *   "model": {
 *     "type": "minecraft:special",
 *     "base": "mymod:item/wand",
 *     "model": { "type": "coolcatlib:armature", "armature": "mymod:wand", "animation": "idle" }
 *   }
 * }
 * }</pre>
 * {@code base} is an ordinary item model, for its display transforms (how it sits in a hand, a slot, a frame) and its
 * particle texture. {@code animation} is a clip looped for every stack; {@code texture} swaps the first texture.
 * For more, register a setup: each stack gets its own animator, whose context has the stack.
 * <pre>{@code
 * ArmatureItems.register(MyItems.WAND, animator -> animator.layer("base")
 *         .drive(ctx -> ctx.stack().has(MyComponents.CHARGED) ? GLOW : IDLE));
 * }</pre>
 */
public final class ArmatureItems {
    private static final Map<Item, Consumer<Animator>> SETUPS = new ConcurrentHashMap<>();
    private static final Map<Supplier<? extends Item>, Consumer<Animator>> PENDING = new ConcurrentHashMap<>();

    private ArmatureItems() {
    }

    /** Sets up the animator of every stack of an item. Register while the client sets up. */
    public static void register(Item item, Consumer<Animator> setup) {
        SETUPS.put(item, setup);
    }

    /** As {@link #register(Item, Consumer)}, for an item that isn't registered yet. */
    public static void register(Supplier<? extends Item> item, Consumer<Animator> setup) {
        PENDING.put(item, setup);
    }

    public static @Nullable Consumer<Animator> setup(Item item) {
        if (!PENDING.isEmpty()) {
            PENDING.forEach((supplier, setup) -> SETUPS.put(supplier.get(), setup));
            PENDING.clear();
        }
        return SETUPS.get(item);
    }
}
