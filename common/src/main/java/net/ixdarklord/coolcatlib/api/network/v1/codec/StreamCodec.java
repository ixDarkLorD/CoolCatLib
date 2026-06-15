package net.ixdarklord.coolcatlib.api.network.v1.codec;

import com.google.common.base.Suppliers;
import io.netty.buffer.ByteBuf;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {

    static <B, V> StreamCodec<B, V> of(
            final StreamEncoder<B, V> encoder,
            final StreamDecoder<B, V> decoder) {

        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(
            final StreamMemberEncoder<B, V> encoder,
            final StreamDecoder<B, V> decoder) {

        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(value, buf);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(final V expectedValue) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buf) {
                return expectedValue;
            }

            @Override
            public void encode(B buf, V value) {
                if (!value.equals(expectedValue)) {
                    throw new IllegalStateException(
                            "Can't encode '" + value + "', expected '" + expectedValue + "'"
                    );
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(
            final Function<? super V, ? extends O> factory,
            final Function<? super O, ? extends V> getter) {

        return new StreamCodec<B, O>() {
            @Override
            public O decode(B buf) {
                return factory.apply(StreamCodec.this.decode(buf));
            }

            @Override
            public void encode(B buf, O value) {
                StreamCodec.this.encode(buf, getter.apply(value));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(
            final Function<O, ? extends B> bufferFactory) {

        return new StreamCodec<O, V>() {
            @Override
            public V decode(O byteBuf) {
                return StreamCodec.this.decode(bufferFactory.apply(byteBuf));
            }

            @Override
            public void encode(O byteBuf, V value) {
                StreamCodec.this.encode(bufferFactory.apply(byteBuf), value);
            }
        };
    }

    default <U> StreamCodec<B, U> dispatch(
            final Function<? super U, ? extends V> keyGetter,
            final Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codecGetter) {

        return new StreamCodec<B, U>() {

            @Override
            public U decode(B buf) {
                V key = StreamCodec.this.decode(buf);
                StreamCodec<? super B, ? extends U> codec = codecGetter.apply(key);
                return codec.decode(buf);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void encode(B buf, U value) {
                V key = keyGetter.apply(value);

                StreamCodec.this.encode(buf, key);

                StreamCodec<? super B, ? extends U> codec = codecGetter.apply(key);
                ((StreamCodec<B, U>) codec).encode(buf, value);
            }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(
            final StreamCodec<? super B, T1> codec,
            final Function<C, T1> getter,
            final Function<T1, C> factory) {

        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buf) {
                return factory.apply(codec.decode(buf));
            }

            @Override
            public void encode(B buf, C value) {
                codec.encode(buf, getter.apply(value));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(
            final StreamCodec<? super B, T1> codec1,
            final Function<C, T1> getter1,
            final StreamCodec<? super B, T2> codec2,
            final Function<C, T2> getter2,
            final BiFunction<T1, T2, C> factory) {

        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buf) {
                return factory.apply(
                        codec1.decode(buf),
                        codec2.decode(buf)
                );
            }

            @Override
            public void encode(B buf, C value) {
                codec1.encode(buf, getter1.apply(value));
                codec2.encode(buf, getter2.apply(value));
            }
        };
    }

    static <B, T> StreamCodec<B, T> recursive(
            final UnaryOperator<StreamCodec<B, T>> modifier) {

        return new StreamCodec<B, T>() {

            private final Supplier<StreamCodec<B, T>> inner =
                    Suppliers.memoize(() -> modifier.apply(this));

            @Override
            public T decode(B buf) {
                return inner.get().decode(buf);
            }

            @Override
            public void encode(B buf, T value) {
                inner.get().encode(buf, value);
            }
        };
    }

    @SuppressWarnings("unchecked")
    default <S extends B> StreamCodec<S, V> cast() {
        return (StreamCodec<S, V>) this;
    }

    @FunctionalInterface
    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }
}