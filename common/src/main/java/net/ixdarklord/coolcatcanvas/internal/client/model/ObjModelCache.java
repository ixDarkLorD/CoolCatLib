package net.ixdarklord.coolcatcanvas.internal.client.model;

import net.ixdarklord.coolcatcanvas.api.client.model.ObjModel;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The .obj models read so far, by file; emptied when resource packs reload (ShaderManagerMixin). */
public final class ObjModelCache {
    private static final Map<Identifier, ObjModel> MODELS = new HashMap<>();

    private ObjModelCache() {
    }

    public static ObjModel get(Identifier file) {
        ObjModel model = MODELS.get(file);
        if (model == null) {
            model = read(file);
            MODELS.put(file, model);
        }
        return model;
    }

    public static void reload() {
        MODELS.clear();
    }

    private static ObjModel read(Identifier file) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            CoolCatCanvas.LOGGER.error("No .obj model at {}", file);
            return ObjModel.EMPTY;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            return ObjModel.parse(reader);
        } catch (Exception e) {
            CoolCatCanvas.LOGGER.error("Skipping .obj model {}: {}", file, e.getMessage());
            return ObjModel.EMPTY;
        }
    }
}
