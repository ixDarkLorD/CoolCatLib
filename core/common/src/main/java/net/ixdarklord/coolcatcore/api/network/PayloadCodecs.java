package net.ixdarklord.coolcatcore.api.network;

import com.mojang.datafixers.util.Either;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.ixdarklord.coolcatcore.api.network.codec.ByteBufCodecs;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stream codecs found by type, so a record payload needs no hand-written {@link StreamCodec}: each record component is
 * encoded in declaration order with the codec of its declared type.
 * <p>
 * Out of the box: primitives and their boxes, {@code String}, {@code UUID}, {@code ResourceLocation}, {@code BlockPos},
 * {@code ChunkPos}, {@code GlobalPos}, {@code Vec3}, {@code Vector3f(c)}, {@code Quaternionf(c)}, {@code Component},
 * {@code ItemStack} (may be empty), {@code CompoundTag}, {@code Tag}, {@code BlockState}, {@code Item}, {@code Block},
 * {@code EntityType}, {@code OptionalInt}, {@code byte[]}, {@code long[]}, plus, built from their parts:
 * enums (by ordinal), records, arrays, {@code Optional}, {@code List}/{@code Collection}, {@code Set}, {@code Map},
 * {@code Either} and {@code ResourceKey}.
 * <p>
 * Any other type must be {@link #register registered} before a payload using it is registered; registering such a
 * payload throws otherwise. Components may not be {@code null}: use {@code Optional}.
 */
public final class PayloadCodecs {
    private static final Object LOCK = new Object();
    private static final Map<Class<?>, StreamCodec<? super FriendlyByteBuf, ?>> CODECS = new HashMap<>();
    private static final Map<Class<?>, Factory> FACTORIES = new HashMap<>();
    // Enums, arrays and records built from their parts, cached by class.
    private static final Map<Class<?>, StreamCodec<? super FriendlyByteBuf, ?>> DERIVED = new HashMap<>();

    private PayloadCodecs() {}

    /**
     * Makes {@code type} usable in record payloads. A type can only be registered once.
     */
    public static <V> void register(Class<V> type, StreamCodec<? super FriendlyByteBuf, V> codec) {
        Objects.requireNonNull(codec, "codec");
        synchronized (LOCK) {
            Class<?> key = wrap(type);
            if (CODECS.containsKey(key) || FACTORIES.containsKey(key)) {
                throw new IllegalArgumentException("A payload codec is already registered for " + type.getName());
            }
            CODECS.put(key, codec);
        }
    }

    /**
     * Makes a generic type usable in record payloads, e.g. a container whose codec depends on its type arguments.
     * The factory receives the type arguments as declared (e.g. {@code [String, Integer]} for {@code Pair<String, Integer>})
     * and resolves the codecs it needs through the {@link Lookup}.
     */
    public static void registerFactory(Class<?> rawType, Factory factory) {
        Objects.requireNonNull(factory, "factory");
        synchronized (LOCK) {
            if (CODECS.containsKey(rawType) || FACTORIES.containsKey(rawType)) {
                throw new IllegalArgumentException("A payload codec is already registered for " + rawType.getName());
            }
            FACTORIES.put(rawType, factory);
        }
    }

    /**
     * The codec of a record, derived from its components.
     *
     * @throws IllegalArgumentException if a component's type (at any depth) has no codec
     */
    public static <R extends Record> StreamCodec<FriendlyByteBuf, R> forRecord(Class<R> recordClass) {
        return cast(get(recordClass));
    }

    /**
     * The codec of any supported type, such as {@code String.class} or a {@link ParameterizedType} from reflection.
     *
     * @throws IllegalArgumentException if the type, or a type it's built from, has no codec
     */
    public static StreamCodec<FriendlyByteBuf, ?> get(Type type) {
        synchronized (LOCK) {
            Set<Class<?>> added = new HashSet<>();
            try {
                return cast(new Resolver(added).resolve(type));
            } catch (RuntimeException e) {
                // Records derived in this call may point at a record that failed; drop them all.
                added.forEach(DERIVED::remove);
                throw e;
            }
        }
    }

    /** Whether {@link #get} would find a codec for the type. */
    public static boolean has(Type type) {
        try {
            get(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Builds the codec of a generic type from its type arguments. */
    @FunctionalInterface
    public interface Factory {
        StreamCodec<? super FriendlyByteBuf, ?> create(Type[] typeArguments, Lookup lookup);
    }

    /** Resolves the codecs of type arguments inside a {@link Factory}. */
    @FunctionalInterface
    public interface Lookup {
        StreamCodec<FriendlyByteBuf, Object> get(Type type);
    }

    private static final class Resolver implements Lookup {
        private final Set<Class<?>> added;
        private final Deque<String> path = new ArrayDeque<>();

        private Resolver(Set<Class<?>> added) {
            this.added = added;
        }

        @Override
        public StreamCodec<FriendlyByteBuf, Object> get(Type type) {
            return cast(this.resolve(type));
        }

        private StreamCodec<? super FriendlyByteBuf, ?> resolve(Type type) {
            if (type instanceof Class<?> c) return this.resolveClass(wrap(c));
            if (type instanceof ParameterizedType p) {
                Class<?> raw = (Class<?>) p.getRawType();
                Factory factory = FACTORIES.get(raw);
                if (factory != null) return factory.create(p.getActualTypeArguments(), this);
                StreamCodec<? super FriendlyByteBuf, ?> codec = CODECS.get(raw);
                if (codec != null) return codec;
                if (raw.isRecord()) throw this.fail("generic record " + typeName(type) + " isn't supported; register a codec for it");
                throw this.missing(type);
            }
            if (type instanceof GenericArrayType array) {
                Type component = array.getGenericComponentType();
                return arrayCodec(rawClass(component), this.resolve(component));
            }
            if (type instanceof WildcardType wildcard && wildcard.getLowerBounds().length == 0) {
                return this.resolve(wildcard.getUpperBounds()[0]);
            }
            throw this.fail("can't encode the unresolved type " + typeName(type) + "; use a concrete type");
        }

        private StreamCodec<? super FriendlyByteBuf, ?> resolveClass(Class<?> type) {
            StreamCodec<? super FriendlyByteBuf, ?> codec = CODECS.get(type);
            if (codec != null) return codec;
            codec = DERIVED.get(type);
            if (codec != null) return codec;
            if (FACTORIES.containsKey(type)) throw this.fail("raw type " + type.getSimpleName() + " needs its type arguments");

            if (type.isEnum()) {
                codec = enumCodec(type.getEnumConstants());
            } else if (type.isArray()) {
                codec = arrayCodec(type.getComponentType(), this.resolve(type.getComponentType()));
            } else if (type.isRecord()) {
                if (type.getTypeParameters().length > 0) throw this.fail("generic record " + type.getSimpleName() + " isn't supported; register a codec for it");
                return this.resolveRecord(type);
            } else {
                throw this.missing(type);
            }
            DERIVED.put(type, codec);
            this.added.add(type);
            return codec;
        }

        private StreamCodec<? super FriendlyByteBuf, ?> resolveRecord(Class<?> type) {
            RecordCodec<?> codec = new RecordCodec<>(type);
            // Cached before its components, so a record that contains itself resolves to this codec.
            DERIVED.put(type, codec);
            this.added.add(type);

            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            codec.accessors = new Method[components.length];
            codec.codecs = new StreamCodec[components.length];
            codec.names = new String[components.length];
            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                this.path.addLast(type.getSimpleName() + "." + component.getName() + " (" + typeName(component.getGenericType()) + ")");
                codec.codecs[i] = cast(this.resolve(component.getGenericType()));
                this.path.removeLast();
                codec.accessors[i] = this.accessible(component.getAccessor());
                codec.names[i] = component.getName();
                parameterTypes[i] = component.getType();
            }
            try {
                codec.constructor = this.accessible(type.getDeclaredConstructor(parameterTypes));
            } catch (NoSuchMethodException e) {
                throw this.fail("record " + type.getName() + " has no canonical constructor");
            }
            return codec;
        }

        private <A extends AccessibleObject & Member> A accessible(A member) {
            if (!Modifier.isPublic(member.getModifiers()) || !Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
                if (!member.trySetAccessible()) {
                    throw this.fail("can't access " + member.getDeclaringClass().getName() + "; make the record public or open its package");
                }
            }
            return member;
        }

        private IllegalArgumentException missing(Type type) {
            String name = rawClass(type).getSimpleName();
            return this.fail("no payload codec is registered for " + typeName(type)
                    + ". Register one with PayloadCodecs.register(" + name + ".class, codec) before registering the payload");
        }

        private IllegalArgumentException fail(String reason) {
            String where = this.path.isEmpty() ? "" : " at " + String.join(" > ", this.path);
            return new IllegalArgumentException("Can't build a payload codec" + where + ": " + reason);
        }
    }

    private static final class RecordCodec<R> implements StreamCodec<FriendlyByteBuf, R> {
        private final Class<R> type;
        private Constructor<?> constructor;
        private Method[] accessors;
        private StreamCodec<FriendlyByteBuf, Object>[] codecs;
        private String[] names;

        @SuppressWarnings("unchecked")
        private RecordCodec(Class<?> type) {
            this.type = (Class<R>) type;
        }

        @Override
        public R decode(FriendlyByteBuf buf) {
            Object[] args = new Object[this.codecs.length];
            for (int i = 0; i < args.length; i++) args[i] = this.codecs[i].decode(buf);
            try {
                return this.type.cast(this.constructor.newInstance(args));
            } catch (InvocationTargetException e) {
                throw new DecoderException("Invalid " + this.type.getSimpleName() + ": " + e.getCause().getMessage(), e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new DecoderException("Failed to create " + this.type.getSimpleName(), e);
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf, R value) {
            for (int i = 0; i < this.codecs.length; i++) {
                Object component;
                try {
                    component = this.accessors[i].invoke(value);
                } catch (ReflectiveOperationException e) {
                    throw new EncoderException("Failed to read " + this.type.getSimpleName() + "." + this.names[i], e);
                }
                if (component == null) {
                    throw new EncoderException(this.type.getSimpleName() + "." + this.names[i] + " is null; use Optional for missing values");
                }
                this.codecs[i].encode(buf, component);
            }
        }
    }

    private static <E> StreamCodec<FriendlyByteBuf, E> enumCodec(E[] constants) {
        return StreamCodec.of((buf, value) -> buf.writeVarInt(((Enum<?>) value).ordinal()), buf -> {
            int ordinal = buf.readVarInt();
            if (ordinal < 0 || ordinal >= constants.length) {
                throw new DecoderException("Invalid " + constants.getClass().getComponentType().getSimpleName() + " ordinal " + ordinal);
            }
            return constants[ordinal];
        });
    }

    private static StreamCodec<FriendlyByteBuf, Object> arrayCodec(Class<?> componentType, StreamCodec<? super FriendlyByteBuf, ?> element) {
        StreamCodec<FriendlyByteBuf, List<Object>> list = PayloadCodecs.<Object>cast(element).apply(ByteBufCodecs.list());
        return list.map(values -> {
            Object array = Array.newInstance(componentType, values.size());
            for (int i = 0; i < values.size(); i++) Array.set(array, i, values.get(i));
            return array;
        }, array -> {
            int length = Array.getLength(array);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) values.add(Array.get(array, i));
            return values;
        });
    }

    @SuppressWarnings("unchecked")
    private static <V> StreamCodec<FriendlyByteBuf, V> cast(StreamCodec<?, ?> codec) {
        return (StreamCodec<FriendlyByteBuf, V>) codec;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        throw new IllegalArgumentException("void has no payload codec");
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p) return (Class<?>) p.getRawType();
        if (type instanceof GenericArrayType a) return rawClass(a.getGenericComponentType()).arrayType();
        return Object.class;
    }

    private static String typeName(Type type) {
        if (type instanceof Class<?> c) return c.getSimpleName();
        if (type instanceof ParameterizedType p) {
            return ((Class<?>) p.getRawType()).getSimpleName() + Arrays.stream(p.getActualTypeArguments())
                    .map(PayloadCodecs::typeName).collect(Collectors.joining(", ", "<", ">"));
        }
        if (type instanceof GenericArrayType a) return typeName(a.getGenericComponentType()) + "[]";
        return type.getTypeName();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <V> void registerRaw(Class<?> type, StreamCodec<? super FriendlyByteBuf, ?> codec) {
        register((Class) type, (StreamCodec) codec);
    }

    static {
        register(Boolean.class, ByteBufCodecs.BOOL);
        register(Byte.class, ByteBufCodecs.BYTE);
        register(Short.class, ByteBufCodecs.SHORT);
        register(Character.class, ByteBufCodecs.VAR_INT.map(i -> (char) i.intValue(), c -> (int) c));
        register(Integer.class, ByteBufCodecs.VAR_INT);
        register(Long.class, ByteBufCodecs.VAR_LONG);
        register(Float.class, ByteBufCodecs.FLOAT);
        register(Double.class, ByteBufCodecs.DOUBLE);
        register(String.class, ByteBufCodecs.STRING_UTF8);
        register(byte[].class, ByteBufCodecs.BYTE_ARRAY);
        register(long[].class, ByteBufCodecs.LONG_ARRAY);
        register(OptionalInt.class, ByteBufCodecs.OPTIONAL_VAR_INT);
        register(UUID.class, ByteBufCodecs.UUID);
        register(ResourceLocation.class, ByteBufCodecs.RESOURCE_LOCATION);
        register(BlockPos.class, ByteBufCodecs.BLOCK_POS);
        register(ChunkPos.class, ByteBufCodecs.CHUNK_POS);
        register(GlobalPos.class, ByteBufCodecs.GLOBAL_POS);
        register(Direction.class, ByteBufCodecs.DIRECTION);
        register(Vec3.class, ByteBufCodecs.VEC3);
        register(Vector3fc.class, ByteBufCodecs.VECTOR3F.map(v -> v, Vector3f::new));
        register(Vector3f.class, ByteBufCodecs.VECTOR3F);
        register(Quaternionfc.class, ByteBufCodecs.QUATERNIONF.map(q -> q, Quaternionf::new));
        register(Quaternionf.class, ByteBufCodecs.QUATERNIONF);
        register(Component.class, ByteBufCodecs.COMPONENT);
        register(ItemStack.class, ByteBufCodecs.OPTIONAL_ITEM_STACK);
        register(CompoundTag.class, ByteBufCodecs.COMPOUND_TAG);
        register(Tag.class, ByteBufCodecs.TAG);
        register(BlockState.class, ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY));
        register(Item.class, ByteBufCodecs.registry(Registries.ITEM));
        register(Block.class, ByteBufCodecs.registry(Registries.BLOCK));
        registerRaw(EntityType.class, ByteBufCodecs.registry(Registries.ENTITY_TYPE));

        registerFactory(Optional.class, (args, lookup) -> ByteBufCodecs.optional(lookup.get(args[0])));
        registerFactory(List.class, (args, lookup) -> lookup.get(args[0]).apply(ByteBufCodecs.list()));
        registerFactory(Collection.class, (args, lookup) -> lookup.get(args[0]).apply(ByteBufCodecs.list()));
        registerFactory(Set.class, (args, lookup) -> ByteBufCodecs.collection(LinkedHashSet::new, lookup.get(args[0])));
        registerFactory(Map.class, (args, lookup) -> ByteBufCodecs.map(LinkedHashMap::new, lookup.get(args[0]), lookup.get(args[1])));
        registerFactory(Either.class, (args, lookup) -> ByteBufCodecs.either(lookup.get(args[0]), lookup.get(args[1])));
        // The registry travels with the key, so any ResourceKey works without knowing its registry up front.
        registerFactory(ResourceKey.class, (args, lookup) -> StreamCodec.<FriendlyByteBuf, ResourceKey<?>>of(
                (buf, key) -> {
                    buf.writeResourceLocation(key.registry());
                    buf.writeResourceLocation(key.location());
                },
                buf -> ResourceKey.create(ResourceKey.createRegistryKey(buf.readResourceLocation()), buf.readResourceLocation())));
    }
}
