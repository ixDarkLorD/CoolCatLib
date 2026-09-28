package net.ixdarklord.coolcatcore.api.network.codec;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(final StreamEncoder<B, V> encoder, final StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            public V decode(B object) {
                return decoder.decode(object);
            }

            public void encode(B object, V object2) {
                encoder.encode(object, object2);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(final StreamMemberEncoder<B, V> encoder, final StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            public V decode(B object) {
                return decoder.decode(object);
            }

            public void encode(B object, V object2) {
                encoder.encode(object2, object);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(final V expectedValue) {
        return new StreamCodec<>() {
            public V decode(B object) {
                return expectedValue;
            }

            public void encode(B object, V object2) {
                if (!object2.equals(expectedValue)) {
                    String var10002 = String.valueOf(object2);
                    throw new IllegalStateException("Can't encode '" + var10002 + "', expected '" + expectedValue + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(final Function<? super V, ? extends O> factory, final Function<? super O, ? extends V> getter) {
        return new StreamCodec<>() {
            public O decode(B object) {
                return factory.apply(StreamCodec.this.decode(object));
            }

            public void encode(B object, O object2) {
                StreamCodec.this.encode(object, getter.apply(object2));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(final Function<O, ? extends B> bufferFactory) {
        return new StreamCodec<>() {
            public V decode(O byteBuf) {
                B object = bufferFactory.apply(byteBuf);
                return StreamCodec.this.decode(object);
            }

            public void encode(O byteBuf, V object) {
                B object2 = bufferFactory.apply(byteBuf);
                StreamCodec.this.encode(object2, object);
            }
        };
    }

    default <U> StreamCodec<B, U> dispatch(final Function<? super U, ? extends V> keyGetter, final Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codecGetter) {
        return new StreamCodec<>() {
            public U decode(B object) {
                V object2 = StreamCodec.this.decode(object);
                StreamCodec<? super B, ? extends U> streamCodec = codecGetter.apply(object2);
                return streamCodec.decode(object);
            }

            public void encode(B object, U object2) {
                V object3 = keyGetter.apply(object2);
                StreamCodec<B, U> streamCodec = (StreamCodec<B, U>) codecGetter.apply(object3);
                StreamCodec.this.encode(object, object3);
                streamCodec.encode(object, object2);
            }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec, final Function<C, T1> getter, final Function<T1, C> factory) {
        return new StreamCodec<>() {
            public C decode(B object) {
                T1 object2 = codec.decode(object);
                return factory.apply(object2);
            }

            public void encode(B object, C object2) {
                codec.encode(object, getter.apply(object2));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final BiFunction<T1, T2, C> factory) {
        return new StreamCodec<>() {
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                return factory.apply(object2, object3);
            }

            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
            }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final Function3<T1, T2, T3, C> factory) {
        return new StreamCodec<>() {
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                T3 object4 = codec3.decode(object);
                return factory.apply(object2, object3, object4);
            }

            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
                codec3.encode(object, getter3.apply(object2));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final Function4<T1, T2, T3, T4, C> factory) {
        return new StreamCodec<>() {
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                T3 object4 = codec3.decode(object);
                T4 object5 = codec4.decode(object);
                return factory.apply(object2, object3, object4, object5);
            }

            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
                codec3.encode(object, getter3.apply(object2));
                codec4.encode(object, getter4.apply(object2));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final Function5<T1, T2, T3, T4, T5, C> factory) {
        return new StreamCodec<>() {
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                T3 object4 = codec3.decode(object);
                T4 object5 = codec4.decode(object);
                T5 object6 = codec5.decode(object);
                return factory.apply(object2, object3, object4, object5, object6);
            }

            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
                codec3.encode(object, getter3.apply(object2));
                codec4.encode(object, getter4.apply(object2));
                codec5.encode(object, getter5.apply(object2));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6, final Function6<T1, T2, T3, T4, T5, T6, C> factory) {
        return new StreamCodec<>() {
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                T3 object4 = codec3.decode(object);
                T4 object5 = codec4.decode(object);
                T5 object6 = codec5.decode(object);
                T6 object7 = codec6.decode(object);
                return factory.apply(object2, object3, object4, object5, object6, object7);
            }

            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
                codec3.encode(object, getter3.apply(object2));
                codec4.encode(object, getter4.apply(object2));
                codec5.encode(object, getter5.apply(object2));
                codec6.encode(object, getter6.apply(object2));
            }
        };
    }

    static <B, T> StreamCodec<B, T> recursive(final UnaryOperator<StreamCodec<B, T>> modifier) {
        return new StreamCodec<>() {
            private final Supplier<StreamCodec<B, T>> inner = Suppliers.memoize(() -> modifier.apply(this));

            public T decode(B object) {
                return (this.inner.get()).decode(object);
            }

            public void encode(B object, T object2) {
                this.inner.get().encode(object, object2);
            }
        };
    }

    default <S extends B> StreamCodec<S, V> cast() {
        return (StreamCodec<S, V>) this;
    }

    @FunctionalInterface
    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> var1);
    }
}