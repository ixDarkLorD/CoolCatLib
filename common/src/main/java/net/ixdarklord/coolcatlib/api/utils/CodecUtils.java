package net.ixdarklord.coolcatlib.api.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.handler.codec.CodecException;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Utility class providing helper methods for working with Mojang {@link Codec} objects.
 * <p>
 * This class simplifies encoding and decoding between Java objects and NBT ({@link Tag}) structures
 * such as {@link CompoundTag} and {@link ListTag}. It also includes predefined codecs for common
 * Minecraft types like {@link Item} and {@link Color}.
 * <p>
 * All methods are static and thread-safe.
 */
public final class CodecUtils {
    /**
     * A codec for serializing and deserializing {@link Item} instances using their
     * {@link ResourceLocation} identifiers in the {@link BuiltInRegistries#ITEM} registry.
     */
    public static final Codec<Item> ITEM_CODEC = ResourceLocation.CODEC.comapFlatMap(location -> {
        try {
            return DataResult.success(BuiltInRegistries.ITEM.get(location));
        } catch (CodecException e) {
            return DataResult.error(() -> location + " is not a registered item.");
        }
    }, BuiltInRegistries.ITEM::getKey).stable();

    /**
     * A codec for serializing and deserializing {@link Color} objects to and from
     * a 3-element integer array ({@code [red, green, blue]}).
     * <p>
     * The encoded form is a list of three integers between {@code 0–255}.
     */
    public static final Codec<Color> COLOR_CODEC = Codec.INT_STREAM.comapFlatMap(intStream -> {
        try {
            return Util.fixedSize(intStream, 3).map(intArray ->
                    new Color(intArray[0], intArray[1], intArray[2]));
        } catch (CodecException e) {
            int[] values = intStream.toArray();
            return DataResult.error(() -> "Invalid Color Format. [r: %s, g: %s, b: %s]"
                    .formatted(values[0], values[1], values[2]));
        }
    }, color -> IntStream.of(color.getRed(), color.getGreen(), color.getBlue())).stable();

    /**
     * Encodes an object into an NBT {@link Tag} using the provided {@link Codec}.
     * <p>
     * If encoding fails, this method throws a {@link CodecException}.
     *
     * @param codec the codec used for encoding
     * @param value the object to encode
     * @param <T>   the type of the object
     * @return the resulting NBT tag (maybe a {@link CompoundTag}, {@link ListTag}, or primitive)
     * @throws CodecException if encoding fails
     */
    public static <T> Tag encode(Codec<T> codec, T value) {
        return codec.encodeStart(NbtOps.INSTANCE, value)
                .getOrThrow(error -> {
                    throw new CodecException("Failed to encode: " + error);
                });
    }

    /**
     * Decodes an object from the given NBT {@link Tag} using the provided {@link Codec}.
     * <p>
     * If decoding fails, this method throws a {@link CodecException}.
     *
     * @param codec the codec used for decoding
     * @param tag   the NBT tag to decode
     * @param <T>   the target object type
     * @return the decoded object
     * @throws CodecException if decoding fails
     */
    public static <T> T decode(Codec<T> codec, Tag tag) {
        return codec.parse(NbtOps.INSTANCE, tag)
                .getOrThrow(error -> {
                    throw new CodecException("Failed to decode: " + error);
                });
    }

    /**
     * Decodes an object from NBT without throwing on failure.
     * <p>
     * Returns an {@link Optional#empty()} if decoding fails.
     *
     * @param codec the codec used for decoding
     * @param tag   the NBT tag to decode
     * @param <T>   the target object type
     * @return an {@link Optional} containing the decoded object if successful
     */
    public static <T> Optional<T> decodeSafe(Codec<T> codec, Tag tag) {
        return codec.parse(NbtOps.INSTANCE, tag).result();
    }

    /**
     * Encodes a value into a {@link CompoundTag}. If the encoded result is not already a compound,
     * it is wrapped in one under the key {@code "value"}.
     *
     * @param codec the codec used for encoding
     * @param value the object to encode
     * @param <T>   the object type
     * @return a compound tag containing the encoded data
     */
    public static <T> CompoundTag toTag(Codec<T> codec, T value) {
        Tag tag = encode(codec, value);
        if (tag instanceof CompoundTag compound) {
            return compound;
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("value", tag);
        return wrapper;
    }

    /**
     * Decodes a value from a {@link CompoundTag} created by {@link #toTag(Codec, Object)}.
     * <p>
     * Automatically unwraps the {@code "value"} field if present.
     *
     * @param codec the codec used for decoding
     * @param tag   the compound tag to decode
     * @param <T>   the target object type
     * @return the decoded object
     */
    public static <T> T fromTag(Codec<T> codec, CompoundTag tag) {
        Tag inner = tag.contains("value") ? tag.get("value") : tag;
        return decode(codec, inner);
    }

    /**
     * Safely decodes a value from a {@link CompoundTag}, returning an {@link Optional}.
     * <p>
     * Automatically unwraps the {@code "value"} field if present.
     *
     * @param codec the codec used for decoding
     * @param tag   the compound tag to decode
     * @param <T>   the target object type
     * @return an {@link Optional} containing the decoded value if successful
     */
    public static <T> Optional<T> fromTagSafe(Codec<T> codec, CompoundTag tag) {
        Tag inner = tag.contains("value") ? tag.get("value") : tag;
        return decodeSafe(codec, inner);
    }

    /**
     * Encodes a {@link List} of elements into a {@link ListTag} using the provided element codec.
     *
     * @param elementCodec the codec for list elements
     * @param list         the list of elements to encode
     * @param <T>          the element type
     * @return the encoded list tag
     */
    public static <T> ListTag toListTag(Codec<T> elementCodec, List<T> list) {
        return (ListTag) encode(elementCodec.listOf(), list);
    }

    /**
     * Decodes a {@link ListTag} into a Java {@link List} using the provided element codec.
     *
     * @param elementCodec the codec for list elements
     * @param listTag      the list tag to decode
     * @param <T>          the element type
     * @return the decoded list
     * @throws CodecException if decoding fails
     */
    public static <T> List<T> fromListTag(Codec<T> elementCodec, ListTag listTag) {
        return decode(elementCodec.listOf(), listTag);
    }

    /**
     * Safely decodes a {@link ListTag} into a Java {@link List}, returning an {@link Optional}.
     *
     * @param elementCodec the codec for list elements
     * @param listTag      the list tag to decode
     * @param <T>          the element type
     * @return an {@link Optional} containing the decoded list if successful
     */
    public static <T> Optional<List<T>> fromListTagSafe(Codec<T> elementCodec, ListTag listTag) {
        return decodeSafe(elementCodec.listOf(), listTag);
    }

    /**
     * Encodes a {@link Map} into a {@link CompoundTag} using codecs for both keys and values.
     *
     * @param keyCodec   the codec used to encode keys
     * @param valueCodec the codec used to encode values
     * @param map        the map to encode
     * @param <K>        the key type
     * @param <V>        the value type
     * @return a compound tag representing the encoded map
     */
    public static <K, V> CompoundTag toMapTag(Codec<K> keyCodec, Codec<V> valueCodec, Map<K, V> map) {
        return (CompoundTag) encode(Codec.unboundedMap(keyCodec, valueCodec), map);
    }

    /**
     * Decodes a {@link CompoundTag} into a {@link Map} using codecs for both keys and values.
     *
     * @param keyCodec   the codec used to decode keys
     * @param valueCodec the codec used to decode values
     * @param tag        the compound tag to decode
     * @param <K>        the key type
     * @param <V>        the value type
     * @return the decoded map
     * @throws CodecException if decoding fails
     */
    public static <K, V> Map<K, V> fromMapTag(Codec<K> keyCodec, Codec<V> valueCodec, CompoundTag tag) {
        return decode(Codec.unboundedMap(keyCodec, valueCodec), tag);
    }

    /**
     * Safely decodes a {@link CompoundTag} into a {@link Map}, returning an {@link Optional}.
     *
     * @param keyCodec   the codec used to decode keys
     * @param valueCodec the codec used to decode values
     * @param tag        the compound tag to decode
     * @param <K>        the key type
     * @param <V>        the value type
     * @return an {@link Optional} containing the decoded map if successful
     */
    public static <K, V> Optional<Map<K, V>> fromMapTagSafe(Codec<K> keyCodec, Codec<V> valueCodec, CompoundTag tag) {
        return decodeSafe(Codec.unboundedMap(keyCodec, valueCodec), tag);
    }
}
