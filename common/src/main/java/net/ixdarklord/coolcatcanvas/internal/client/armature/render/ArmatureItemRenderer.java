package net.ixdarklord.coolcatcanvas.internal.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.api.client.armature.render.ArmatureItems;
import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureInstances;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureLibrary;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The {@code coolcatlib:armature} special item model: an item drawn with an armature, each stack animated on its own. It sits
 * in the item's model space as a block model does, standing on the bottom middle of the 0-16 cube.
 */
public final class ArmatureItemRenderer implements SpecialModelRenderer<ArmatureFrame> {
    public static final Identifier TYPE = CoolCatCanvas.rl("armature");

    private final Armature armature;
    private final @Nullable Motion animation;
    private final @Nullable Identifier texture;

    private ArmatureItemRenderer(Armature armature, @Nullable Motion animation, @Nullable Identifier texture) {
        this.armature = armature;
        this.animation = animation;
        this.texture = texture;
    }

    /** True for this renderer, so the item is redrawn every frame in slots (see SpecialModelWrapperMixin). */
    public static boolean animates(SpecialModelRenderer<?> renderer) {
        return renderer instanceof ArmatureItemRenderer;
    }

    @Override
    public @Nullable ArmatureFrame extractArgument(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Consumer<Animator> setup = ArmatureItems.setup(stack.getItem());
        Animator animator = ArmatureInstances.get(stack, this.armature, a -> {
            if (this.animation != null) a.play(this.animation);
            if (setup != null) setup.accept(a);
        });
        return animator.update(GameClock.seconds(), 0.0F, placement(this.armature.get()));
    }

    @Override
    public void submit(@Nullable ArmatureFrame frame, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        if (frame == null) return;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        Identifier texture = this.texture;
        ArmatureDraw.submit(frame, poseStack, collector, ArmatureDraw.Style.lit(lightCoords)
                .overlay(overlayCoords)
                .outline(outlineColor)
                .foil(hasFoil)
                .texture(texture == null ? null : slot -> slot == 0 ? texture : null));
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        // Asked for while models bake, maybe off the render thread: only an armature already read is used.
        BakedArmature armature = ArmatureLibrary.getIfLoaded(this.armature.id());
        AABB bounds = armature != null ? armature.bounds() : new AABB(-8, 0, -8, 8, 16, 8);
        if (armature == null) armature = BakedArmature.EMPTY;
        Matrix4f placement = placement(armature);
        for (int corner = 0; corner < 8; corner++) {
            float x = (float) ((corner & 1) == 0 ? bounds.minX : bounds.maxX);
            float y = (float) ((corner & 2) == 0 ? bounds.minY : bounds.maxY);
            float z = (float) ((corner & 4) == 0 ? bounds.minZ : bounds.maxZ);
            output.accept(placement.transformPosition(new Vector3f(x, y, z)));
        }
    }

    // Items have no place in the world to speak of; this only sets the model's size for springs and pointing.
    private static Matrix4f placement(BakedArmature armature) {
        return new Matrix4f().translation(0.5F, 0.0F, 0.5F).mul(ArmatureDraw.modelTransform(armature));
    }

    public record Unbaked(Identifier armature, Optional<String> animation, Optional<Identifier> texture) implements SpecialModelRenderer.Unbaked<ArmatureFrame> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("armature").forGetter(Unbaked::armature),
                Codec.STRING.optionalFieldOf("animation").forGetter(Unbaked::animation),
                Identifier.CODEC.optionalFieldOf("texture").forGetter(Unbaked::texture)
        ).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<ArmatureFrame> bake(SpecialModelRenderer.BakingContext context) {
            return new ArmatureItemRenderer(Armature.of(this.armature), this.animation.map(Motion::loop).orElse(null), this.texture.orElse(null));
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
