package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Draws a living entity with an armature. Everything vanilla does for mobs still happens: the hurt flash, the death topple
 * (unless turned off for a death clip), sleeping, freezing shakes, invisibility, the glowing outline, name tags,
 * leashes, and any {@link RenderLayer}s added. Each entity gets its own {@link Animator}.
 * <pre>{@code
 * EntityRendererRegistry.register(MyEntities.WISP, ArmatureMobRenderer.of(Armature.of("mymod", "wisp"), 0.4F, renderer -> renderer
 *         .animate(animator -> {
 *             animator.layer("base").drive(ctx -> ctx.moving() ? WALK : IDLE);
 *             animator.lookAt("head");
 *         })
 *         .holdItem("right_hand", EquipmentSlot.MAINHAND)));
 * }</pre>
 * Or subclass it and override {@link #setupAnimator}, for typed access to the entity.
 */
public class ArmatureMobRenderer<T extends LivingEntity> extends LivingEntityRenderer<T, ArmatureMobRenderer.State, ArmatureMobRenderer.LayerModel> {
    private final ArmatureRenderSettings<T> settings;
    private @Nullable GlowStrength<T> glow;
    private @Nullable Tint<T> tint;
    private @Nullable Translucency<T> translucent;
    private final List<ItemSocket<T>> sockets = new ArrayList<>();
    private boolean deathFlip = true;

    public ArmatureMobRenderer(EntityRendererProvider.Context context, Armature armature, float shadowRadius) {
        super(context, new LayerModel(), shadowRadius);
        this.settings = new ArmatureRenderSettings<>(armature);
    }

    /** A renderer provider, for registering without a class of your own. */
    public static <T extends LivingEntity> EntityRendererProvider<T> of(Armature armature, float shadowRadius, Consumer<ArmatureMobRenderer<T>> configure) {
        return context -> {
            ArmatureMobRenderer<T> renderer = new ArmatureMobRenderer<>(context, armature, shadowRadius);
            configure.accept(renderer);
            return renderer;
        };
    }

    public static <T extends LivingEntity> EntityRendererProvider<T> of(Armature armature, float shadowRadius) {
        return of(armature, shadowRadius, renderer -> {});
    }

    public Armature armature() {
        return this.settings.armature;
    }

    // ---- Setup ----

    /** Sets up each entity's animator when it is made. Several setups run in the order given. */
    public ArmatureMobRenderer<T> animate(Consumer<Animator> setup) {
        this.settings.setups.add(setup);
        return this;
    }

    /** Swaps the armature's first texture per entity (variants, colours); null keeps the armature's. */
    public ArmatureMobRenderer<T> texture(Function<T, @Nullable Identifier> texture) {
        this.settings.texture = texture;
        return this;
    }

    /**
     * Picks the armature per entity, for variants whose bodies differ (not only their textures, see {@link #texture}).
     * Each entity's animator is made for the armature it is drawn with, and made afresh, set up again, if that changes.
     * Null keeps the renderer's own armature, which is also the one {@link #armature()} gives.
     */
    public ArmatureMobRenderer<T> armature(Function<T, @Nullable Armature> pick) {
        this.settings.picker = pick;
        return this;
    }

    /**
     * Tints the whole model per entity, asked each frame with the partial tick: an ARGB colour multiplied into every face,
     * on top of vanilla's own tint. Its alpha shows only while the entity is drawn {@link #translucent}.
     */
    public ArmatureMobRenderer<T> tint(Tint<T> tint) {
        this.tint = tint;
        return this;
    }

    /**
     * Whether an entity is drawn see-through this frame (a ghost, a hallucination, something fading in): its lit parts
     * blended with what is behind them, so the alpha of its {@link #tint} shows. Glowing parts are drawn as before.
     */
    public ArmatureMobRenderer<T> translucent(Translucency<T> translucent) {
        this.translucent = translucent;
        return this;
    }

    /** An entity's tint this frame (see {@link #tint}). */
    @FunctionalInterface
    public interface Tint<T> {
        int argb(T entity, float partialTicks);
    }

    /** Whether an entity is see-through this frame (see {@link #translucent}). */
    @FunctionalInterface
    public interface Translucency<T> {
        boolean translucent(T entity, float partialTicks);
    }

    /** Scales the model, on top of the entity's own scale. */
    public ArmatureMobRenderer<T> scale(float scale) {
        this.settings.scale = scale;
        return this;
    }

    /** Whether a dying entity topples over as vanilla mobs do; turn it off when a clip shows the death. */
    public ArmatureMobRenderer<T> deathFlip(boolean flip) {
        this.deathFlip = flip;
        return this;
    }

    /** Draws something at a point of the armature. */
    public ArmatureMobRenderer<T> attach(String point, ArmatureAttachment attachment) {
        this.settings.attachments.add(new ArmatureRenderSettings.Attached(point, attachment));
        return this;
    }

    /**
     * Shows what the entity holds or wears in a slot at a point, held as a vanilla hand holds it: put the point (a
     * locator) at the hand, with the model at rest pointing the arm down.
     */
    public ArmatureMobRenderer<T> holdItem(String point, EquipmentSlot slot) {
        ItemDisplayContext display = slot == EquipmentSlot.OFFHAND ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        return this.socket(new ItemSocket<T>(point, entity -> entity.getItemBySlot(slot), display).held());
    }

    /** Shows an item at a point. */
    public ArmatureMobRenderer<T> socket(ItemSocket<T> socket) {
        this.sockets.add(socket);
        return this;
    }

    /** Sets up a new animator, after the {@link #animate} setups; for subclasses. */
    /**
     * How bright its {@code _glow} textures are, asked each frame with the partial tick: 1 as they are, 0 dark. Eyes
     * that open, a lantern lit and put out.
     */
    public ArmatureMobRenderer<T> glow(GlowStrength<T> glow) {
        this.glow = glow;
        return this;
    }

    /** How bright an entity's glow textures are drawn (see {@link #glow}). */
    @FunctionalInterface
    public interface GlowStrength<T> {
        float strength(T entity, float partialTicks);
    }

    protected void setupAnimator(Animator animator, T entity) {
    }

    // ---- Vanilla's side ----

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        Armature drawn = this.settings.armatureFor(entity);
        Animator animator = this.settings.animator(entity, drawn, a -> this.setupAnimator(a, entity));
        BakedArmature armature = drawn.get();
        float scale = entity.getScale() * this.settings.scale;
        Matrix4f modelToWorld = new Matrix4f()
                .translation((float) state.x, (float) state.y, (float) state.z)
                .rotateY((180.0F - state.bodyRot) * Mth.DEG_TO_RAD)
                .scale(scale)
                .mul(ArmatureDraw.modelTransform(armature));
        state.frame = animator.update(GameClock.seconds(), partialTicks, modelToWorld);
        state.texture = this.settings.texture != null ? this.settings.texture.apply(entity) : null;
        state.modelScale = this.settings.scale;
        state.glow = this.glow != null ? this.glow.strength(entity, partialTicks) : 1.0F;
        state.tint = this.tint != null ? this.tint.argb(entity, partialTicks) : -1;
        state.translucent = this.translucent != null && this.translucent.translucent(entity, partialTicks);
        while (state.items.size() < this.sockets.size()) state.items.add(new ItemStackRenderState());
        for (int i = 0; i < this.sockets.size(); i++) {
            ItemSocket<T> socket = this.sockets.get(i);
            ItemStackRenderState item = state.items.get(i);
            ItemStack stack = socket.stack().apply(entity);
            if (stack.isEmpty()) item.clear();
            else this.itemModelResolver.updateForLiving(item, stack, socket.display(), entity);
        }
    }

    @Override
    public Identifier getTextureLocation(State state) {
        if (state.texture != null) return state.texture;
        return state.frame != null ? state.frame.armature().texture(0) : this.settings.armature.get().texture(0);
    }

    @Override
    protected float getFlipDegrees() {
        return this.deathFlip ? 90.0F : 0.0F;
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity) {
        AABB box = super.getBoundingBoxForCulling(entity);
        BakedArmature armature = this.settings.armatureFor(entity).get();
        if (armature.isEmpty()) return box;
        // The model may reach past the hitbox (wings, tails); turning with the entity, it may reach any way around.
        AABB bounds = armature.bounds();
        float scale = armature.scale() / 16.0F * this.settings.scale * entity.getScale();
        double reach = Math.max(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)), Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ))) * scale;
        AABB model = new AABB(entity.getX() - reach, entity.getY() + bounds.minY * scale, entity.getZ() - reach,
                entity.getX() + reach, entity.getY() + bounds.maxY * scale, entity.getZ() + reach);
        return box.minmax(model);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        if (state.hasPose(Pose.SLEEPING)) {
            Direction bed = state.bedOrientation;
            if (bed != null) {
                float headOffset = state.eyeHeight - 0.1F;
                poseStack.translate(-bed.getStepX() * headOffset, 0.0F, -bed.getStepZ() * headOffset);
            }
        }
        float scale = state.scale;
        poseStack.scale(scale, scale, scale);
        this.setupRotations(state, poseStack, state.bodyRot, scale);
        poseStack.scale(state.modelScale, state.modelScale, state.modelScale);

        boolean bodyVisible = this.isBodyVisible(state);
        boolean ghost = !bodyVisible && !state.isInvisibleToPlayer;
        if (state.frame != null && (bodyVisible || ghost || state.appearsGlowing())) {
            Identifier texture = state.texture;
            ArmatureDraw.Style style = ArmatureDraw.Style.lit(state.lightCoords)
                    .overlay(getOverlayCoords(state, this.getWhiteOverlayProgress(state)))
                    .outline(state.outlineColor)
                    .color(state.tint == -1 ? this.getModelTint(state) : ARGB.multiply(this.getModelTint(state), state.tint))
                    .glow(state.glow)
                    .texture(texture == null ? null : slot -> slot == 0 ? texture : null);
            if (state.translucent) style = style.translucent();
            if (ghost) style = style.ghost().color(0x26FFFFFF);
            else if (!bodyVisible) style = style.outlineOnly();
            ArmatureDraw.submit(state.frame, poseStack, collector, style);
            if (bodyVisible || ghost) {
                this.settings.submitAttachments(state.frame, poseStack, collector, state.lightCoords);
                for (int i = 0; i < this.sockets.size() && i < state.items.size(); i++) {
                    ItemStackRenderState item = state.items.get(i);
                    if (item.isEmpty()) continue;
                    poseStack.pushPose();
                    if (ArmaturePoints.moveTo(state.frame, this.sockets.get(i).point(), poseStack)) {
                        this.sockets.get(i).transform(poseStack);
                        item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
                    }
                    poseStack.popPose();
                }
            }
        }

        if (!this.layers.isEmpty()) {
            // Vanilla layers work in vanilla model space: upside down, a pixel and a half up.
            poseStack.pushPose();
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            for (RenderLayer<State, LayerModel> layer : this.layers) {
                layer.submit(poseStack, collector, state.lightCoords, state, state.yRot, state.xRot);
            }
            poseStack.popPose();
        }
        poseStack.popPose();

        if (state.leashStates != null) {
            for (EntityRenderState.LeashState leash : state.leashStates) collector.submitLeash(poseStack, leash);
        }
        this.submitNameDisplay(state, poseStack, collector, camera);
    }

    /** What an armature-driven mob is drawn from, each frame. */
    public static class State extends LivingEntityRenderState {
        public @Nullable ArmatureFrame frame;
        public @Nullable Identifier texture;
        public float modelScale = 1.0F;
        public float glow = 1.0F;
        /** Its {@linkplain ArmatureMobRenderer#tint tint} this frame, -1 for none. */
        public int tint = -1;
        /** Whether it is drawn {@linkplain ArmatureMobRenderer#translucent see-through} this frame. */
        public boolean translucent;
        public final List<ItemStackRenderState> items = new ArrayList<>();
    }

    /** The model {@link RenderLayer}s see: it has no parts of its own (the armature draws itself). */
    public static final class LayerModel extends EntityModel<State> {
        public LayerModel() {
            super(new ModelPart(List.of(), Map.of()));
        }
    }

    static void handTransform(PoseStack poseStack) {
        // From the armature's axes to a vanilla arm's (upside down), then as vanilla's held-item layer turns it.
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
    }
}
