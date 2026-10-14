package net.ixdarklord.coolcatcore.api.config;

import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.api.config.type.NumberType;
import net.ixdarklord.coolcatcore.internal.config.ConfigGroupImpl;

/**
 * A number entry: optionally ranged, optionally shown as a slider.
 */
public final class NumberEntryBuilder<N extends Number & Comparable<N>> extends AbstractEntryBuilder<N, NumberEntryBuilder<N>> {
    private NumberType<N> type;
    private boolean slider;

    NumberEntryBuilder(ConfigBuilder owner, ConfigGroupImpl group, String key, NumberType<N> type, N defaultValue) {
        super(owner, group, key, defaultValue);
        this.type = type;
    }

    /** Values outside the range are clamped when read from a file, and rejected elsewhere. */
    public NumberEntryBuilder<N> range(N min, N max) {
        this.type = this.type.withRange(min, max);
        return this;
    }

    public NumberEntryBuilder<N> min(N min) {
        return this.range(min, this.type.max());
    }

    public NumberEntryBuilder<N> max(N max) {
        return this.range(this.type.min(), max);
    }

    /** Shows a slider in the config screen; needs a {@link #range}. */
    public NumberEntryBuilder<N> slider() {
        this.slider = true;
        return this;
    }

    @Override
    protected ConfigType<N> type() {
        return this.type.withSlider(this.slider);
    }
}
