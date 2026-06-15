package net.ixdarklord.coolcatlib.api.network.v1.codec;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.ixdarklord.coolcatlib.api.network.VarInt;
import net.ixdarklord.coolcatlib.api.network.VarLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public interface ByteBufCodecs {
    int MAX_INITIAL_COLLECTION_SIZE = 65536;
    StreamCodec<ByteBuf, Boolean> BOOL = new StreamCodec<>() {
        public Boolean decode(ByteBuf buf) {
            return buf.readBoolean();
        }

        public void encode(ByteBuf buf, Boolean value) {
            buf.writeBoolean(value);
        }
    };
    StreamCodec<ByteBuf, Byte> BYTE = new StreamCodec<>() {
        public Byte decode(ByteBuf buf) {
            return buf.readByte();
        }

        public void encode(ByteBuf buf, Byte value) {
            buf.writeByte(value);
        }
    };
    StreamCodec<ByteBuf, Short> SHORT = new StreamCodec<>() {
        public Short decode(ByteBuf buf) {
            return buf.readShort();
        }

        public void encode(ByteBuf buf, Short value) {
            buf.writeShort(value);
        }
    };
    StreamCodec<ByteBuf, Integer> INT = new StreamCodec<>() {
        public Integer decode(ByteBuf buf) {
            return buf.readInt();
        }

        public void encode(ByteBuf buf, Integer value) {
            buf.writeInt(value);
        }
    };
    StreamCodec<ByteBuf, Integer> VAR_INT = new StreamCodec<>() {
        public Integer decode(ByteBuf buf) {
            return VarInt.read(buf);
        }

        public void encode(ByteBuf buf, Integer value) {
            VarInt.write(buf, value);
        }
    };
    StreamCodec<ByteBuf, Long> VAR_LONG = new StreamCodec<>() {
        public Long decode(ByteBuf buf) {
            return VarLong.read(buf);
        }

        public void encode(ByteBuf buf, Long value) {
            VarLong.write(buf, value);
        }
    };
    StreamCodec<ByteBuf, Float> FLOAT = new StreamCodec<>() {
        public Float decode(ByteBuf buf) {
            return buf.readFloat();
        }

        public void encode(ByteBuf buf, Float value) {
            buf.writeFloat(value);
        }
    };
    StreamCodec<ByteBuf, Double> DOUBLE = new StreamCodec<>() {
        public Double decode(ByteBuf buf) {
            return buf.readDouble();
        }

        public void encode(ByteBuf buf, Double value) {
            buf.writeDouble(value);
        }
    };
    StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = new StreamCodec<>() {
        public byte[] decode(ByteBuf buf) {
            return new FriendlyByteBuf(buf).readByteArray();
        }

        public void encode(ByteBuf buf, byte[] value) {
            new FriendlyByteBuf(buf).writeByteArray(value);
        }
    };
    StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    StreamCodec<ByteBuf, Tag> TAG = tagCodec(() -> new NbtAccounter(2097152L));
    StreamCodec<ByteBuf, Tag> TRUSTED_TAG = tagCodec(() -> NbtAccounter.UNLIMITED);
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = compoundTagCodec(() -> new NbtAccounter(2097152L));
    StreamCodec<ByteBuf, CompoundTag> TRUSTED_COMPOUND_TAG = compoundTagCodec(() -> NbtAccounter.UNLIMITED);
    StreamCodec<ByteBuf, Optional<CompoundTag>> OPTIONAL_COMPOUND_TAG = new StreamCodec<>() {
        public Optional<CompoundTag> decode(ByteBuf buf) {
            return Optional.ofNullable(new FriendlyByteBuf(buf).readNbt());
        }

        public void encode(ByteBuf buf, Optional<CompoundTag> optional) {
            new FriendlyByteBuf(buf).writeNbt(optional.orElse(null));
        }
    };
    StreamCodec<ByteBuf, Vector3f> VECTOR3F = new StreamCodec<>() {
        public Vector3f decode(ByteBuf buf) {
            return new FriendlyByteBuf(buf).readVector3f();
        }

        public void encode(ByteBuf buf, Vector3f value) {
            new FriendlyByteBuf(buf).writeVector3f(value);
        }
    };
    StreamCodec<ByteBuf, Quaternionf> QUATERNIONF = new StreamCodec<>() {
        public Quaternionf decode(ByteBuf buf) {
            return new FriendlyByteBuf(buf).readQuaternion();
        }

        public void encode(ByteBuf buf, Quaternionf value) {
            new FriendlyByteBuf(buf).writeQuaternion(value);
        }
    };
    StreamCodec<ByteBuf, PropertyMap> GAME_PROFILE_PROPERTIES = new StreamCodec<>() {
        private static final int MAX_NAME = 64;
        private static final int MAX_VALUE = 32767;
        private static final int MAX_SIGNATURE = 1024;
        private static final int MAX_PROPERTIES = 16;

        public PropertyMap decode(ByteBuf buf) {
            FriendlyByteBuf fbb = new FriendlyByteBuf(buf);
            int count = VarInt.read(buf);
            if (count > 16) {
                throw new DecoderException("Too many properties");
            } else {
                PropertyMap map = new PropertyMap();

                for (int i = 0; i < count; ++i) {
                    String name = fbb.readUtf(64);
                    String value = fbb.readUtf(32767);
                    String sig = fbb.readNullable(b -> b.readUtf(1024));
                    map.put(name, new Property(name, value, sig));
                }

                return map;
            }
        }

        public void encode(ByteBuf buf, PropertyMap map) {
            FriendlyByteBuf fbb = new FriendlyByteBuf(buf);
            VarInt.write(buf, map.size());

            for (Property prop : map.values()) {
                assert prop != null;

                fbb.writeUtf(prop.getName(), 64);
                fbb.writeUtf(prop.getValue(), 32767);
                fbb.writeNullable(prop.getSignature(), (b, s) -> b.writeUtf(s, 1024));
            }

        }
    };
    StreamCodec<ByteBuf, GameProfile> GAME_PROFILE = new StreamCodec<>() {
        public GameProfile decode(ByteBuf buf) {
            FriendlyByteBuf fbb = new FriendlyByteBuf(buf);
            UUID uuid = fbb.readUUID();
            String name = fbb.readUtf(16);
            GameProfile profile = new GameProfile(uuid, name);
            profile.getProperties().putAll(ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(buf));
            return profile;
        }

        public void encode(ByteBuf buf, GameProfile profile) {
            FriendlyByteBuf fbb = new FriendlyByteBuf(buf);
            fbb.writeUUID(profile.getId());
            fbb.writeUtf(profile.getName(), 16);
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(buf, profile.getProperties());
        }
    };

    static StreamCodec<ByteBuf, String> stringUtf8(final int maxLength) {
        return new StreamCodec<>() {
            public String decode(ByteBuf buf) {
                return new FriendlyByteBuf(buf).readUtf(maxLength);
            }

            public void encode(ByteBuf buf, String s) {
                new FriendlyByteBuf(buf).writeUtf(s, maxLength);
            }
        };
    }

    static StreamCodec<ByteBuf, Tag> tagCodec(final Supplier<NbtAccounter> accounter) {
        return new StreamCodec<>() {
            public Tag decode(ByteBuf buf) {
                Tag tag = new FriendlyByteBuf(buf).readNbt(accounter.get());
                if (tag == null) {
                    throw new DecoderException("Expected non-null tag");
                } else {
                    return tag;
                }
            }

            public void encode(ByteBuf buf, Tag tag) {
                if (tag == EndTag.INSTANCE) {
                    throw new EncoderException("Expected non-null compound tag");
                } else {
                    new FriendlyByteBuf(buf).writeNbt((CompoundTag) tag);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, CompoundTag> compoundTagCodec(Supplier<NbtAccounter> accounter) {
        return tagCodec(accounter).map((tag) -> {
            if (tag instanceof CompoundTag c) {
                return c;
            } else {
                throw new DecoderException("Not a compound tag: " + tag);
            }
        }, (c) -> c);
    }

    static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(final StreamCodec<B, V> codec) {
        return new StreamCodec<>() {
            public Optional<V> decode(B buf) {
                return buf.readBoolean() ? Optional.of(codec.decode(buf)) : Optional.empty();
            }

            public void encode(B buf, Optional<V> value) {
                if (value.isPresent()) {
                    buf.writeBoolean(true);
                    codec.encode(buf, value.get());
                } else {
                    buf.writeBoolean(false);
                }

            }
        };
    }

    static int readCount(ByteBuf buf, int max) {
        int i = VarInt.read(buf);
        if (i > max) {
            throw new DecoderException(i + " > max " + max);
        } else {
            return i;
        }
    }

    static void writeCount(ByteBuf buf, int count, int max) {
        if (count > max) {
            throw new EncoderException(count + " > max " + max);
        } else {
            VarInt.write(buf, count);
        }
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(final IntFunction<C> factory, final StreamCodec<? super B, V> codec, final int max) {
        return new StreamCodec<>() {
            public C decode(B buf) {
                int i = ByteBufCodecs.readCount(buf, max);
                C list = factory.apply(Math.min(i, 65536));

                for (int j = 0; j < i; ++j) {
                    list.add(codec.decode(buf));
                }

                return list;
            }

            public void encode(B buf, C coll) {
                ByteBufCodecs.writeCount(buf, coll.size(), max);

                for (V v : coll) {
                    codec.encode(buf, v);
                }

            }
        };
    }
}
