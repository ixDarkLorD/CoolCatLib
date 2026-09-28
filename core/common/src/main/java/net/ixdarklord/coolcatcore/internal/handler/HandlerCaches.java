package net.ixdarklord.coolcatcore.internal.handler;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.ixdarklord.coolcatcore.api.handler.BlockHandlerCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// The live block handler caches by level and position, told when what they cached may be gone. Caches are held
// weakly: a dropped cache needs no unregistering.
public final class HandlerCaches {
    private static final Map<Level, Long2ObjectMap<List<WeakReference<BlockHandlerCache<?>>>>> CACHES = new WeakHashMap<>();

    private HandlerCaches() {}

    public static synchronized void register(Level level, BlockPos pos, BlockHandlerCache<?> cache) {
        List<WeakReference<BlockHandlerCache<?>>> caches = CACHES.computeIfAbsent(level, key -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(pos.asLong(), key -> new ArrayList<>());
        caches.removeIf(reference -> reference.get() == null);
        caches.add(new WeakReference<>(cache));
    }

    /** Invalidates the caches at the position; they register again when next read. */
    public static void invalidate(Level level, BlockPos pos) {
        List<WeakReference<BlockHandlerCache<?>>> caches;
        synchronized (HandlerCaches.class) {
            Long2ObjectMap<List<WeakReference<BlockHandlerCache<?>>>> byPos = CACHES.get(level);
            if (byPos == null) return;
            caches = byPos.remove(pos.asLong());
            if (byPos.isEmpty()) CACHES.remove(level);
        }
        if (caches == null) return;
        for (WeakReference<BlockHandlerCache<?>> reference : caches) {
            BlockHandlerCache<?> cache = reference.get();
            if (cache != null) cache.invalidate();
        }
    }
}
