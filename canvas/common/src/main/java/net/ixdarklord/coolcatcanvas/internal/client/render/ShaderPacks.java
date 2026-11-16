package net.ixdarklord.coolcatcanvas.internal.client.render;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Whether an Iris (Fabric) or Oculus (Forge) shader pack is in use, through Iris's API when either is installed. Shader
 * packs render the sky with their own programs, into their own buffers, so skybox layers stand aside while one is on.
 */
public final class ShaderPacks {
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static @Nullable Object api;
    private static @Nullable Method inUse;
    private static boolean resolved;

    private ShaderPacks() {}

    public static boolean inUse() {
        if (!resolved) resolve();
        if (api == null || inUse == null) return false;
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CoolCatCanvas.LOGGER.warn("Couldn't ask Iris whether a shader pack is in use; assuming none from now on", e);
            api = null;
            return false;
        }
    }

    private static void resolve() {
        resolved = true;
        try {
            Class<?> type = Class.forName(IRIS_API);
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
        } catch (ClassNotFoundException e) {
            // Neither Iris nor Oculus.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CoolCatCanvas.LOGGER.warn("Found Iris's API but couldn't use it", e);
            api = null;
            inUse = null;
        }
    }
}
