package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.BendablePart;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.LimbBend;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Bending for vanilla parts (elbows, knees): a bent part draws its cubes along an arc (LimbBend) instead of straight.
@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements BendablePart {
    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Unique
    private float coolcatlib$bend;
    @Unique
    private float coolcatlib$bendDirection;
    @Unique
    private float coolcatlib$bendStart;
    @Unique
    private float coolcatlib$bendEnd;

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$drawBent(PoseStack.Pose pose, VertexConsumer buffer, int lightCoords, int overlayCoords, int color, CallbackInfo ci) {
        if (this.coolcatlib$bend == 0.0F) return;
        LimbBend.draw(this, pose, buffer, lightCoords, overlayCoords, color);
        ci.cancel();
    }

    @Override
    public void coolcatlib$setBend(float amount, float direction, float start, float end) {
        this.coolcatlib$bend = amount;
        this.coolcatlib$bendDirection = direction;
        this.coolcatlib$bendStart = start;
        this.coolcatlib$bendEnd = end;
    }

    @Override
    public float coolcatlib$bendAmount() {
        return this.coolcatlib$bend;
    }

    @Override
    public float coolcatlib$bendDirection() {
        return this.coolcatlib$bendDirection;
    }

    @Override
    public float coolcatlib$bendStart() {
        return this.coolcatlib$bendStart;
    }

    @Override
    public float coolcatlib$bendEnd() {
        return this.coolcatlib$bendEnd;
    }

    @Override
    public List<ModelPart.Cube> coolcatlib$cubes() {
        return this.cubes;
    }
}
