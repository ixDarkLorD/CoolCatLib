package net.ixdarklord.coolcatcore.api.network.codec;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.api.utils.CodecUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.ToIntFunction;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Optional;

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

    // 1.20.1's buffers only read and write compound tags, so any other tag travels wrapped in one.
    static StreamCodec<ByteBuf, Tag> tagCodec(final Supplier<NbtAccounter> accounter) {
        StreamCodec<ByteBuf, CompoundTag> wrapper = compoundTagCodec(accounter);
        return new StreamCodec<>() {
            public Tag decode(ByteBuf buf) {
                Tag tag = wrapper.decode(buf).get("");
                if (tag == null) {
                    throw new DecoderException("Expected non-null tag");
                } else {
                    return tag;
                }
            }

            public void encode(ByteBuf buf, Tag tag) {
                if (tag == EndTag.INSTANCE) {
                    throw new EncoderException("Expected non-null tag");
                } else {
                    CompoundTag compound = new CompoundTag();
                    compound.put("", tag);
                    wrapper.encode(buf, compound);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, CompoundTag> compoundTagCodec(Supplier<NbtAccounter> accounter) {
        return new StreamCodec<>() {
            public CompoundTag decode(ByteBuf buf) {
                CompoundTag tag = new FriendlyByteBuf(buf).readNbt(accounter.get());
                if (tag == null) {
                    throw new DecoderException("Expected non-null compound tag");
                } else {
                    return tag;
                }
            }

            public void encode(ByteBuf buf, CompoundTag tag) {
                new FriendlyByteBuf(buf).writeNbt(tag);
            }
        };
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

    // ─── Added in CoolCatLib: Core for 1.20.1: the rest of what 1.20.5+'s ByteBufCodecs offers ──────────────────────

    StreamCodec<ByteBuf, long[]> LONG_ARRAY = new StreamCodec<>() {
        public long[] decode(ByteBuf buf) {
            return new FriendlyByteBuf(buf).readLongArray();
        }

        public void encode(ByteBuf buf, long[] value) {
            new FriendlyByteBuf(buf).writeLongArray(value);
        }
    };
    StreamCodec<ByteBuf, OptionalInt> OPTIONAL_VAR_INT = VAR_INT.map(
            i -> i == 0 ? OptionalInt.empty() : OptionalInt.of(i - 1),
            o -> o.isPresent() ? o.getAsInt() + 1 : 0);

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> factory, StreamCodec<? super B, V> codec) {
        return collection(factory, codec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(IntFunction<C> factory) {
        return codec -> collection(factory, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> collection(ArrayList::new, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int max) {
        return codec -> collection(ArrayList::new, codec, max);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(IntFunction<? extends M> factory, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec) {
        return map(factory, keyCodec, valueCodec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(IntFunction<? extends M> factory, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec, int max) {
        return new StreamCodec<>() {
            public void encode(B buf, M map) {
                writeCount(buf, map.size(), max);
                map.forEach((k, v) -> {
                    keyCodec.encode(buf, k);
                    valueCodec.encode(buf, v);
                });
            }

            public M decode(B buf) {
                int count = readCount(buf, max);
                M map = factory.apply(Math.min(count, MAX_INITIAL_COLLECTION_SIZE));
                for (int i = 0; i < count; i++) {
                    K k = keyCodec.decode(buf);
                    V v = valueCodec.decode(buf);
                    map.put(k, v);
                }
                return map;
            }
        };
    }

    static <B extends ByteBuf, L, R> StreamCodec<B, Either<L, R>> either(StreamCodec<? super B, L> left, StreamCodec<? super B, R> right) {
        return new StreamCodec<>() {
            public Either<L, R> decode(B buf) {
                return buf.readBoolean() ? Either.left(left.decode(buf)) : Either.right(right.decode(buf));
            }

            public void encode(B buf, Either<L, R> either) {
                either.ifLeft(l -> {
                    buf.writeBoolean(true);
                    left.encode(buf, l);
                }).ifRight(r -> {
                    buf.writeBoolean(false);
                    right.encode(buf, r);
                });
            }
        };
    }

    /** A value by its id in an {@link IdMap}, such as {@code Block.BLOCK_STATE_REGISTRY}. */
    static <T> StreamCodec<ByteBuf, T> idMapper(IdMap<T> idMap) {
        return new StreamCodec<>() {
            public T decode(ByteBuf buf) {
                int id = VarInt.read(buf);
                T value = idMap.byId(id);
                if (value == null) throw new DecoderException("Unknown id " + id);
                return value;
            }

            public void encode(ByteBuf buf, T value) {
                int id = idMap.getId(value);
                if (id == -1) throw new EncoderException("Can't find id for '" + value + "'");
                VarInt.write(buf, id);
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(IntFunction<T> byId, ToIntFunction<T> toId) {
        return new StreamCodec<>() {
            public T decode(ByteBuf buf) {
                return byId.apply(VarInt.read(buf));
            }

            public void encode(ByteBuf buf, T value) {
                VarInt.write(buf, toId.applyAsInt(value));
            }
        };
    }

    /** A value by its network id in one of the built-in registries (1.20.1's buffers carry no registry access). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> StreamCodec<ByteBuf, T> registry(ResourceKey<? extends Registry<T>> registryKey) {
        Supplier<Registry<T>> registry = Suppliers.memoize(() -> (Registry<T>) ((Registry) BuiltInRegistries.REGISTRY).getOrThrow((ResourceKey) registryKey));
        return new StreamCodec<>() {
            public T decode(ByteBuf buf) {
                int id = VarInt.read(buf);
                T value = registry.get().byId(id);
                if (value == null) throw new DecoderException("Unknown id " + id + " in " + registryKey.location());
                return value;
            }

            public void encode(ByteBuf buf, T value) {
                int id = registry.get().getId(value);
                if (id == -1) throw new EncoderException("Can't find id for '" + value + "' in " + registryKey.location());
                VarInt.write(buf, id);
            }
        };
    }

    /** A value written with its {@link Codec} as NBT. */
    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec) {
        return fromCodec(codec, () -> new NbtAccounter(2097152L));
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec, Supplier<NbtAccounter> accounter) {
        return tagCodec(accounter).map(
                tag -> codec.parse(NbtOps.INSTANCE, tag).getOrThrow(false, msg -> {}),
                value -> codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow(false, msg -> {}));
    }

    /**
     * Like {@link #fromCodec(Codec)}, with the registry access of the running server or client world when there is one
     * (1.20.1's buffers carry none).
     */
    static <T> StreamCodec<ByteBuf, T> fromCodecWithRegistries(Codec<T> codec) {
        return tagCodec(() -> new NbtAccounter(2097152L)).map(
                tag -> codec.parse(CodecUtils.registryOps(NbtOps.INSTANCE), tag).getOrThrow(false, msg -> {}),
                value -> codec.encodeStart(CodecUtils.registryOps(NbtOps.INSTANCE), value).getOrThrow(false, msg -> {}));
    }

    // What 1.20.5+ keeps on the vanilla classes themselves (ByteBufCodecs.RESOURCE_LOCATION, ByteBufCodecs.BLOCK_POS, ...).
    StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION = STRING_UTF8.map(ResourceLocation::new, ResourceLocation::toString);
    StreamCodec<ByteBuf, BlockPos> BLOCK_POS = new StreamCodec<>() {
        public BlockPos decode(ByteBuf buf) {
            return BlockPos.of(buf.readLong());
        }

        public void encode(ByteBuf buf, BlockPos pos) {
            buf.writeLong(pos.asLong());
        }
    };
    StreamCodec<ByteBuf, Vec3> VEC3 = new StreamCodec<>() {
        public Vec3 decode(ByteBuf buf) {
            return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        public void encode(ByteBuf buf, Vec3 vec) {
            buf.writeDouble(vec.x);
            buf.writeDouble(vec.y);
            buf.writeDouble(vec.z);
        }
    };
    StreamCodec<ByteBuf, ChunkPos> CHUNK_POS = new StreamCodec<>() {
        public ChunkPos decode(ByteBuf buf) {
            return new ChunkPos(buf.readLong());
        }

        public void encode(ByteBuf buf, ChunkPos pos) {
            buf.writeLong(pos.toLong());
        }
    };
    StreamCodec<ByteBuf, ResourceKey<Level>> DIMENSION = RESOURCE_LOCATION.map(id -> ResourceKey.create(Registries.DIMENSION, id), ResourceKey::location);
    StreamCodec<ByteBuf, GlobalPos> GLOBAL_POS = new StreamCodec<>() {
        public GlobalPos decode(ByteBuf buf) {
            return GlobalPos.of(DIMENSION.decode(buf), BLOCK_POS.decode(buf));
        }

        public void encode(ByteBuf buf, GlobalPos pos) {
            DIMENSION.encode(buf, pos.dimension());
            BLOCK_POS.encode(buf, pos.pos());
        }
    };
    StreamCodec<ByteBuf, Direction> DIRECTION = idMapper(Direction::from3DDataValue, Direction::get3DDataValue);
    StreamCodec<ByteBuf, UUID> UUID = new StreamCodec<>() {
        public UUID decode(ByteBuf buf) {
            return new UUID(buf.readLong(), buf.readLong());
        }

        public void encode(ByteBuf buf, UUID uuid) {
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
        }
    };
    StreamCodec<ByteBuf, Component> COMPONENT = new StreamCodec<>() {
        public Component decode(ByteBuf buf) {
            return new FriendlyByteBuf(buf).readComponent();
        }

        public void encode(ByteBuf buf, Component component) {
            new FriendlyByteBuf(buf).writeComponent(component);
        }
    };
    /** An item stack, which may be empty (1.20.5+'s ByteBufCodecs.OPTIONAL_ITEM_STACK). */
    StreamCodec<ByteBuf, ItemStack> OPTIONAL_ITEM_STACK = new StreamCodec<>() {
        public ItemStack decode(ByteBuf buf) {
            return new FriendlyByteBuf(buf).readItem();
        }

        public void encode(ByteBuf buf, ItemStack stack) {
            new FriendlyByteBuf(buf).writeItem(stack);
        }
    };
    /** A non-empty item stack (1.20.5+'s ByteBufCodecs.ITEM_STACK). */
    StreamCodec<ByteBuf, ItemStack> ITEM_STACK = new StreamCodec<>() {
        public ItemStack decode(ByteBuf buf) {
            ItemStack stack = OPTIONAL_ITEM_STACK.decode(buf);
            if (stack.isEmpty()) throw new DecoderException("Empty ItemStack not allowed");
            return stack;
        }

        public void encode(ByteBuf buf, ItemStack stack) {
            if (stack.isEmpty()) throw new EncoderException("Empty ItemStack not allowed");
            OPTIONAL_ITEM_STACK.encode(buf, stack);
        }
    };
}
