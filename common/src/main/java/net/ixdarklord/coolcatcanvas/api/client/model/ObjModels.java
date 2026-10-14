package net.ixdarklord.coolcatcanvas.api.client.model;

import net.ixdarklord.coolcatcanvas.internal.client.model.ObjModelCache;
import net.minecraft.resources.Identifier;

/**
 * {@code .obj} models as resources. A model is any file under {@code assets/}, named by its whole path:
 * {@code ObjModels.get(Identifier.fromNamespaceAndPath("mymod", "models/obj/crystal.obj"))} reads
 * {@code assets/mymod/models/obj/crystal.obj}. It is read the first time it is asked for, kept, and read again after
 * resource packs reload, so a resource pack can replace it; ask each frame and do not keep the result.
 *
 * <p>A file that is missing or cannot be read is logged once and gives {@link ObjModel#EMPTY}, which draws nothing.</p>
 */
public final class ObjModels {
    private ObjModels() {
    }

    /** The model at this path under {@code assets/<namespace>/}; never null. Client thread only. */
    public static ObjModel get(Identifier file) {
        return ObjModelCache.get(file);
    }
}
