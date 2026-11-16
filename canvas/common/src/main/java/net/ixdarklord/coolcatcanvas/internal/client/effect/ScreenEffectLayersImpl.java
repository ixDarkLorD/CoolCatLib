package net.ixdarklord.coolcatcanvas.internal.client.effect;

import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffect;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectLayers;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectScope;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.ScreenEffectEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Mutations are synchronized (effects may register off-thread); the renderer reads an immutable snapshot.
final class ScreenEffectLayersImpl implements ScreenEffectLayers {
    private static final Comparator<ScreenEffectImpl> BY_PRIORITY = Comparator.comparingInt((ScreenEffectImpl effect) -> effect.priority()).thenComparingInt(effect -> effect.serial);

    private final List<ScreenEffectImpl> order = new ArrayList<>();
    private volatile List<ScreenEffectImpl> snapshot = List.of();
    private boolean customized;

    /** Bottom to top, for drawing. */
    List<ScreenEffectImpl> view() {
        return this.snapshot;
    }

    synchronized void add(ScreenEffectImpl effect) {
        this.order.add(effect);
        if (!this.customized) this.order.sort(BY_PRIORITY);
        this.publish(false);
    }

    synchronized void remove(ScreenEffectImpl effect) {
        if (this.order.remove(effect)) this.publish(true);
    }

    synchronized void onPriorityChanged() {
        if (this.customized) return;
        this.order.sort(BY_PRIORITY);
        this.publish(true);
    }

    /** Restores a saved order: the listed effects in that order, then the rest by priority. */
    synchronized void restore(List<Identifier> saved) {
        Map<Identifier, Integer> index = new HashMap<>();
        for (int i = 0; i < saved.size(); i++) index.putIfAbsent(saved.get(i), i);
        this.order.sort(Comparator.<ScreenEffectImpl>comparingInt(effect -> index.getOrDefault(effect.id(), Integer.MAX_VALUE)).thenComparing(BY_PRIORITY));
        this.customized = true;
        this.publish(true);
    }

    @Override
    public List<ScreenEffect> order() {
        return List.copyOf(this.snapshot);
    }

    @Override
    public List<ScreenEffect> visible(ScreenEffectScope scope) {
        List<ScreenEffect> visible = new ArrayList<>();
        for (ScreenEffectImpl effect : this.snapshot) {
            if (effect.scope() == scope && effect.isVisible()) visible.add(effect);
        }
        return visible;
    }

    @Override
    public int indexOf(ScreenEffect effect) {
        return this.snapshot.indexOf(effect);
    }

    @Override
    public synchronized void moveTo(ScreenEffect effect, int index) {
        int from = this.order.indexOf(effect);
        if (from < 0) throw new IllegalArgumentException("Not a registered screen effect: " + effect.id());
        int to = Mth.clamp(index, 0, this.order.size() - 1);
        this.customized = true;
        if (from == to) return;
        this.order.add(to, this.order.remove(from));
        this.publish(true);
    }

    @Override
    public synchronized void moveUp(ScreenEffect effect) {
        int from = this.order.indexOf(effect);
        for (int i = from + 1; i < this.order.size() && from >= 0; i++) {
            if (this.order.get(i).scope() == effect.scope()) {
                this.moveTo(effect, i);
                return;
            }
        }
    }

    @Override
    public synchronized void moveDown(ScreenEffect effect) {
        int from = this.order.indexOf(effect);
        for (int i = from - 1; i >= 0; i--) {
            if (this.order.get(i).scope() == effect.scope()) {
                this.moveTo(effect, i);
                return;
            }
        }
    }

    @Override
    public synchronized boolean isCustomized() {
        return this.customized;
    }

    @Override
    public synchronized void resetOrder() {
        this.customized = false;
        this.order.sort(BY_PRIORITY);
        this.publish(true);
    }

    private void publish(boolean notify) {
        this.snapshot = List.copyOf(this.order);
        if (notify) ScreenEffectEvents.LAYERS_CHANGED.invoker().onLayersChanged(List.copyOf(this.snapshot));
    }
}
