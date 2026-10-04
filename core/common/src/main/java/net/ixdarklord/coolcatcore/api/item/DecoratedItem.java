package net.ixdarklord.coolcatcore.api.item;

import net.ixdarklord.coolcatcore.api.client.gui.ItemDecorator;

import java.util.function.Consumer;

/**
 * An item that draws over itself in GUIs. Implement it on the item's class and hand over the decorator: Core registers
 * it for every item of that class by itself, on the client only, the first time the item is drawn.
 * <pre>{@code
 * public class FlaskItem extends Item implements DecoratedItem {
 *     @Override
 *     public void registerDecorators(Consumer<ItemDecorator> registrar) {
 *         registrar.accept(new FlaskChargesDecorator());   // a client class: this only runs on a client
 *     }
 * }
 * }</pre>
 * There is nothing else to register. The method is never called on a dedicated server, so it may name client-only
 * classes.
 */
public interface DecoratedItem {
    /**
     * Hands the item's decorators to {@code registrar}, in the order they draw. Called once per item, on the client.
     */
    void registerDecorators(Consumer<ItemDecorator> registrar);
}
