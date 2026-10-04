---
icon: material/star-four-points-outline
description: Custom drawing over items in GUIs
---

# Item Decorators

Custom drawing over an item wherever the game draws it in a GUI: inventory slots, the hotbar, a stack held on the cursor. Charges on a flask, pips on a card, a badge on a tool: anything vanilla's durability bar and count can't say. It works the same on every loader. Packages: `net.ixdarklord.coolcatcore.api.item` and `api.client.gui`.

!!! info "Since"
    Core **26.1.2-3**, **2100.2.0.1** (1.21.1) and **2001.2.0.1** (1.20.1).

## Decorating an item

Implement `DecoratedItem` on the item's class and hand over its decorator. There is nothing else to register: Core takes each such item's decorators by itself, on the client, the first time the item is drawn.

```java title="common: the item"
public class FlaskItem extends Item implements DecoratedItem {
    public FlaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void registerDecorators(Consumer<ItemDecorator> registrar) {
        registrar.accept(new FlaskChargesDecorator()); // (1)!
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false; // (2)!
    }
}
```

1.  A client class. `registerDecorators` never runs on a dedicated server, so naming one here is safe.
2.  Vanilla's durability bar still follows the item's own `isBarVisible`. Return false to show only your decoration.

```java title="common: the decorator (client only)"
public final class FlaskChargesDecorator implements ItemDecorator {
    @Override
    public void extract(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        int charges = stack.getOrDefault(MyComponents.CHARGES, 0);
        for (int i = 0; i < charges; i++) {
            graphics.fill(x + 2 + i * 3, y + 13, x + 4 + i * 3, y + 15, 0xFF55FFFF);
        }
    }
}
```

The item is 16×16 GUI pixels at (`x`, `y`). Decorators draw after vanilla's own decorations (the durability bar, the cooldown overlay and the count), in the order the item handed them over. The pose is saved and restored around each one, so a decorator can move or scale it freely.

Every item of the class gets the decorator, so one class registered under several ids (a card per tool, say) needs no extra work.

!!! tip "Drawing at the texture's resolution"
    An item texture larger than 16×16 has pixels smaller than a GUI pixel. To line a decoration up with a 32×32 texture, scale the pose by half and work in the texture's pixels:

    ```java
    var pose = graphics.pose();
    pose.translate(x, y);
    pose.scale(0.5F, 0.5F);
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PIP, 8, 25, 4, 3);   // texture pixels now
    ```

## Reference

| Type | Description |
|---|---|
| `api.item.DecoratedItem` | Implement on an item's class. `registerDecorators(Consumer<ItemDecorator> registrar)`: hand over the item's decorators, in drawing order. Called once per item, on the client only. |
| `api.client.gui.ItemDecorator` | `extract(graphics, font, stack, x, y)`: draws over the item. A functional interface, so a lambda works too. On 1.21.1 and 1.20.1 `graphics` is a `GuiGraphics`. |
