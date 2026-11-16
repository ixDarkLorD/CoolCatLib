package net.ixdarklord.coolcatcanvas.internal.client.armature.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.coolcatcanvas.api.client.armature.molang.MolangExpr;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Reading the loose JSON of model files: numbers that may be strings, arrays that may be single values. */
final class JsonValues {
    private JsonValues() {
    }

    static float number(@Nullable JsonElement element, float fallback) {
        if (element == null || element.isJsonNull()) return fallback;
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) return element.getAsFloat();
            if (element.getAsJsonPrimitive().isBoolean()) return element.getAsBoolean() ? 1.0F : 0.0F;
            try {
                return Float.parseFloat(element.getAsString().trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    static float number(JsonObject object, String key, float fallback) {
        return number(object.get(key), fallback);
    }

    static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    static Vector3f vec(@Nullable JsonElement element, float fallback) {
        if (element == null || !element.isJsonArray()) {
            float single = number(element, fallback);
            return new Vector3f(single);
        }
        JsonArray array = element.getAsJsonArray();
        return new Vector3f(
                array.size() > 0 ? number(array.get(0), fallback) : fallback,
                array.size() > 1 ? number(array.get(1), fallback) : fallback,
                array.size() > 2 ? number(array.get(2), fallback) : fallback);
    }

    static float @Nullable [] floats3(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) return null;
        Vector3f v = vec(element, 0.0F);
        return new float[]{v.x, v.y, v.z};
    }

    /** A keyframe value: a number, or Molang in a string. Molang that can't be read is logged and reads as the fallback. */
    static MolangExpr expr(@Nullable JsonElement element, float fallback, String where) {
        if (element == null || element.isJsonNull()) return MolangExpr.constant(fallback);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return MolangExpr.constant(element.getAsDouble());
        String source = element.getAsString();
        if (source.isBlank()) return MolangExpr.constant(fallback);
        try {
            return MolangExpr.parse(source);
        } catch (IllegalArgumentException e) {
            CoolCatCanvas.LOGGER.warn("Couldn't read Molang in {}: {}", where, e.getMessage());
            return MolangExpr.constant(fallback);
        }
    }

    static @Nullable MolangExpr optionalExpr(JsonObject object, String key, String where) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && element.getAsString().isBlank()) return null;
        return expr(element, 0.0F, where);
    }

    /** Three values: an array of three, or one value for all three. */
    static MolangExpr[] exprs(@Nullable JsonElement element, float fallback, String where) {
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            MolangExpr[] out = new MolangExpr[3];
            for (int i = 0; i < 3; i++) out[i] = i < array.size() ? expr(array.get(i), fallback, where) : MolangExpr.constant(fallback);
            return out;
        }
        MolangExpr single = expr(element, fallback, where);
        return new MolangExpr[]{single, single, single};
    }
}
