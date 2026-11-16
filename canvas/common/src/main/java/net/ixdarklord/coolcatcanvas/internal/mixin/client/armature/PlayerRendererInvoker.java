package net.ixdarklord.coolcatcanvas.internal.mixin.client.armature;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// The player's model set up as the world draw sets it (arm poses, crouching, worn layers), for the first-person arms.
@Mixin(PlayerRenderer.class)
public interface PlayerRendererInvoker {
    @Invoker("setModelProperties")
    void coolcatcanvas$setModelProperties(AbstractClientPlayer player);
}
