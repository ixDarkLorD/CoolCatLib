package net.ixdarklord.coolcat_lib.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;

import java.util.Map;

public class JsonUtils {
    @SuppressWarnings("UnusedReturnValue")
    public static JsonObject deepMerge(JsonObject source, JsonObject target) throws JsonIOException {
        for (Map.Entry<String, JsonElement> sourceEntry : source.entrySet()) {
            String key = sourceEntry.getKey();
            JsonElement value = sourceEntry.getValue();
            if (!target.has(key)) {
                //target does not have the same key, so perhaps it should be added to target
                if (!value.isJsonNull()) //well, only add if the source value is not null
                    target.add(key, value);
            } else {
                if (!value.isJsonNull()) {
                    if (value.isJsonObject()) {
                        //source value is json object, start deep merge
                        deepMerge(value.getAsJsonObject(), target.get(key).getAsJsonObject());
                    } else {
                        target.add(key,value);
                    }
                } else {
                    target.remove(key);
                }
            }
        }
        return target;
    }
}
