package net.ixdarklord.coolcatcanvas.internal.client.blockentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// A block renderer's render states, reused from frame to frame: the game asks for a new state for every block entity
// every frame. A state handed out in one frame is handed out again from two frames later, once everything that was
// submitted from it has been drawn. It grows to the most of its block entities seen in view at once. Client thread only.
public final class BlockStatePool<S> {
    private static final int BUCKETS = 3;

    private final Supplier<S> factory;
    private final ArrayDeque<S> free = new ArrayDeque<>();
    @SuppressWarnings("unchecked")
    private final List<S>[] handedOut = new List[BUCKETS];
    private final int[] bucketFrames = new int[BUCKETS];
    private int frame = Integer.MIN_VALUE;

    public BlockStatePool(Supplier<S> factory) {
        this.factory = factory;
        for (int i = 0; i < BUCKETS; i++) {
            this.handedOut[i] = new ArrayList<>();
            this.bucketFrames[i] = Integer.MIN_VALUE;
        }
    }

    public S take() {
        int now = BlockRenderFrames.current();
        if (now != this.frame) {
            this.frame = now;
            for (int i = 0; i < BUCKETS; i++) {
                // (Handed out two or more frames ago: drawn, and free again.)
                if (this.bucketFrames[i] != Integer.MIN_VALUE && now - this.bucketFrames[i] >= 2) {
                    this.free.addAll(this.handedOut[i]);
                    this.handedOut[i].clear();
                    this.bucketFrames[i] = Integer.MIN_VALUE;
                }
            }
        }
        S state = this.free.poll();
        if (state == null) state = this.factory.get();
        int bucket = Math.floorMod(now, BUCKETS);
        this.bucketFrames[bucket] = now;
        this.handedOut[bucket].add(state);
        return state;
    }
}
