package net.ixdarklord.coolcatcanvas.api.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.api.utils.Easings;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A number that changes over a cutscene, as keys in time: how far the letterbox bars are in, how dark the fade is.
 * Before the first key it holds the first value, after the last the last.
 * <pre>{@code
 * ValueTrack.of(0, 0.0F, 15, 1.0F)                                  // 0 at the start, 1 from tick 15 on
 * ValueTrack.constant(1.0F)
 * ValueTrack.EMPTY.then(0, 1.0F).then(20, 0.0F, Easing.SINE_OUT)    // from 1 down to 0, easing out
 * }</pre>
 */
public record ValueTrack(List<Key> keys) {
    /** No keys: 0 throughout. */
    public static final ValueTrack EMPTY = new ValueTrack(List.of());
    public static final Codec<ValueTrack> CODEC = Key.CODEC.listOf().xmap(ValueTrack::new, ValueTrack::keys);

    /**
     * @param easing how the move into this key, from the one before, is paced
     */
    public record Key(float time, float value, Easing easing) {
        public static final Codec<Key> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("time").forGetter(Key::time),
                Codec.FLOAT.fieldOf("value").forGetter(Key::value),
                Easings.CODEC.optionalFieldOf("easing", Easing.LINEAR).forGetter(Key::easing)
        ).apply(instance, Key::new));
    }

    public ValueTrack {
        List<Key> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(Key::time));
        keys = List.copyOf(sorted);
    }

    public static ValueTrack constant(float value) {
        return new ValueTrack(List.of(new Key(0.0F, value, Easing.LINEAR)));
    }

    /** Keys from pairs of a time and a value, eased gently between. */
    public static ValueTrack of(float... timesAndValues) {
        if (timesAndValues.length % 2 != 0) throw new IllegalArgumentException("Times and values come in pairs");
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < timesAndValues.length; i += 2) keys.add(new Key(timesAndValues[i], timesAndValues[i + 1], Easing.SINE_IN_OUT));
        return new ValueTrack(keys);
    }

    /** This track with one more key, eased gently. */
    public ValueTrack then(float time, float value) {
        return this.then(time, value, Easing.SINE_IN_OUT);
    }

    public ValueTrack then(float time, float value, Easing easing) {
        List<Key> keys = new ArrayList<>(this.keys);
        keys.add(new Key(time, value, easing));
        return new ValueTrack(keys);
    }

    public boolean isEmpty() {
        return this.keys.isEmpty();
    }

    /** The value at a time, in ticks. */
    public float at(float time) {
        int count = this.keys.size();
        if (count == 0) return 0.0F;
        Key first = this.keys.getFirst();
        if (time <= first.time) return first.value;
        for (int i = 1; i < count; i++) {
            Key to = this.keys.get(i);
            if (time < to.time) {
                Key from = this.keys.get(i - 1);
                return Mth.lerp(to.easing.apply((time - from.time) / (to.time - from.time)), from.value, to.value);
            }
        }
        return this.keys.getLast().value;
    }
}
