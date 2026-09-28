package net.ixdarklord.coolcatcore.internal.attachment;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

// The value of a mod's <modid>:attachments item component: that mod's persistent attachment values by name,
// immutable, sorted by name so the encoding (and so the stack's hash) doesn't depend on the order values were set in.
public final class AttachmentBundle {
    private static final Comparator<Attachment<?>> ORDER = Comparator.comparing((Attachment<?> attachment) -> attachment.name());
    public static final AttachmentBundle EMPTY = new AttachmentBundle(new TreeMap<>(ORDER));

    private final Map<Attachment<?>, Object> values;

    private AttachmentBundle(TreeMap<Attachment<?>, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    /** Reads and writes one mod's bundle; names it doesn't know are dropped. */
    public static Codec<AttachmentBundle> codec(AttachmentRegistry registry) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<AttachmentBundle, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<MapLike<T>> map = ops.getMap(input);
                return map.map(entries -> Pair.of(read(registry, ops, entries), input));
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> DataResult<T> encode(AttachmentBundle bundle, DynamicOps<T> ops, T prefix) {
                RecordBuilder<T> builder = ops.mapBuilder();
                for (Map.Entry<Attachment<?>, Object> entry : bundle.values.entrySet()) {
                    Attachment<Object> attachment = (Attachment<Object>) entry.getKey();
                    builder.add(ops.createString(attachment.name()), Objects.requireNonNull(attachment.codec()).encodeStart(ops, entry.getValue()));
                }
                return builder.build(prefix);
            }
        };
    }

    public static StreamCodec<RegistryFriendlyByteBuf, AttachmentBundle> streamCodec(Codec<AttachmentBundle> codec) {
        return ByteBufCodecs.fromCodecWithRegistries(codec);
    }

    @SuppressWarnings("unchecked")
    private static <T> AttachmentBundle read(AttachmentRegistry registry, DynamicOps<T> ops, MapLike<T> map) {
        TreeMap<Attachment<?>, Object> values = new TreeMap<>(ORDER);
        map.entries().forEach(entry -> {
            String name = ops.getStringValue(entry.getFirst()).result().orElse(null);
            Attachment<Object> attachment = name == null ? null : (Attachment<Object>) registry.get(name);
            if (attachment == null || attachment.codec() == null) {
                CoolCatCore.LOGGER.debug("Dropping unknown item attachment {}:{}", registry.modId(), name);
                return;
            }
            attachment.codec().parse(ops, entry.getSecond())
                    .resultOrPartial(error -> CoolCatCore.LOGGER.error("Couldn't read item attachment {}: {}", attachment.id(), error))
                    .ifPresent(value -> values.put(attachment, value));
        });
        return values.isEmpty() ? EMPTY : new AttachmentBundle(values);
    }

    public Map<Attachment<?>, Object> values() {
        return this.values;
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(Attachment<T> attachment) {
        return (T) this.values.get(attachment);
    }

    /** A copy with the value set, or removed for {@code null}. */
    public AttachmentBundle with(Attachment<?> attachment, @Nullable Object value) {
        TreeMap<Attachment<?>, Object> values = new TreeMap<>(ORDER);
        values.putAll(this.values);
        if (value == null) values.remove(attachment);
        else values.put(attachment, value);
        return values.isEmpty() ? EMPTY : new AttachmentBundle(values);
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof AttachmentBundle other && this.values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }

    @Override
    public String toString() {
        return "AttachmentBundle" + this.values;
    }
}
