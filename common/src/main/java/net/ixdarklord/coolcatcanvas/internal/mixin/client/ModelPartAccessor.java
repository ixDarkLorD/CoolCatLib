package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// A part's children by name, to find vanilla model parts by name (armature clips and attachments on vanilla mobs).
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("children")
    Map<String, ModelPart> coolcatlib$children();
}
