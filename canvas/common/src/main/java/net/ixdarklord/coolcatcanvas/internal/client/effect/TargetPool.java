package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Render targets effects borrow for a frame, by size, so effects that aren't drawn hold none. Targets unused for a few
// frames are freed.
final class TargetPool implements AutoCloseable {
    private final int keepFrames;
    private final List<Entry> free = new ArrayList<>();
    private int frame;

    TargetPool(int keepFrames) {
        this.keepFrames = keepFrames;
    }

    RenderTarget acquire(int width, int height) {
        for (Iterator<Entry> iterator = this.free.iterator(); iterator.hasNext(); ) {
            Entry entry = iterator.next();
            if (entry.target.width == width && entry.target.height == height) {
                iterator.remove();
                return entry.target;
            }
        }
        return new TextureTarget(width, height, true, Minecraft.ON_OSX);
    }

    void release(RenderTarget target) {
        this.free.add(new Entry(target, this.frame));
    }

    void endFrame() {
        this.frame++;
        this.free.removeIf(entry -> {
            if (this.frame - entry.lastUsed <= this.keepFrames) return false;
            entry.target.destroyBuffers();
            return true;
        });
    }

    /** Frees every target, e.g. once the window's size changed. */
    void clear() {
        this.free.forEach(entry -> entry.target.destroyBuffers());
        this.free.clear();
    }

    @Override
    public void close() {
        this.clear();
    }

    private record Entry(RenderTarget target, int lastUsed) {}
}
