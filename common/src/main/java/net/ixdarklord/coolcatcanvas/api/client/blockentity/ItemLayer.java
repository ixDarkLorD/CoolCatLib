package net.ixdarklord.coolcatcanvas.api.client.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaternionf;

import java.util.function.Function;

/**
 * An item shown on a block entity: on a pedestal, in a display case, floating over an altar. It can spin and bob.
 * <pre>{@code
 * this.layer(ItemLayer.<PedestalBlockEntity, State>of(PedestalBlockEntity::display)
 *         .at(0.5F, 1.25F, 0.5F).scale(0.6F).spin(2.0F).bob(0.05F).fullBright());
 * }</pre>
 * Placed in its renderer's pose (turned to its facing, if it turns). The item's model is updated into one
 * {@link ItemStackRenderState} kept with each state, so showing it allocates nothing per frame.
 */
public final class ItemLayer<T extends BlockEntity, S extends BlockRenderState> implements BlockRenderLayer<T, S, ItemLayer.Shown> {
    private final Function<? super T, ItemStack> item;
    private final Quaternionf turn = new Quaternionf();
    private float x = 0.5F, y = 1.0F, z = 0.5F, scale = 0.5F, spin, bob, bobPeriod = 40.0F;
    private ItemDisplayContext display = ItemDisplayContext.GROUND;
    private boolean fullBright;
    private int detail = Detail.MINIMAL;

    private ItemLayer(Function<? super T, ItemStack> item) {
        this.item = item;
    }

    /** Shows the item this gives (an empty stack shows nothing). Give it the stack the block entity holds, not a copy. */
    public static <T extends BlockEntity, S extends BlockRenderState> ItemLayer<T, S> of(Function<? super T, ItemStack> item) {
        return new ItemLayer<>(item);
    }

    /** Where its middle is, in blocks from the block's corner; over the block's middle (0.5, 1, 0.5) by default. */
    public ItemLayer<T, S> at(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /** How large it's drawn; 0.5 by default. */
    public ItemLayer<T, S> scale(float scale) {
        this.scale = scale;
        return this;
    }

    /** Turns it about its upright axis this many degrees a tick. */
    public ItemLayer<T, S> spin(float degreesPerTick) {
        this.spin = degreesPerTick;
        return this;
    }

    /** Floats it up and down this far, in blocks, once every 40 ticks. */
    public ItemLayer<T, S> bob(float height) {
        return this.bob(height, 40.0F);
    }

    public ItemLayer<T, S> bob(float height, float periodTicks) {
        this.bob = height;
        this.bobPeriod = Math.max(1.0F, periodTicks);
        return this;
    }

    /** How the item's model is held; {@code GROUND} (as a dropped item) by default. */
    public ItemLayer<T, S> display(ItemDisplayContext display) {
        this.display = display;
        return this;
    }

    /** Draws it fully lit, as a glowing display, rather than in its block's light. */
    public ItemLayer<T, S> fullBright() {
        this.fullBright = true;
        return this;
    }

    /** The least detail it's still drawn at; {@link Detail#MINIMAL} by default. */
    public ItemLayer<T, S> upTo(int detail) {
        this.detail = detail;
        return this;
    }

    @Override
    public int detail() {
        return this.detail;
    }

    @Override
    public Shown newState() {
        return new Shown();
    }

    @Override
    public void extract(T blockEntity, S state, Shown shown, BlockRenderContext context) {
        ItemStack stack = this.item.apply(blockEntity);
        context.items().updateForTopItem(shown.item, stack == null ? ItemStack.EMPTY : stack, this.display, context.level(), null, (int) state.blockPos.asLong());
        shown.angle = this.spin == 0.0F ? 0.0F : (float) (state.time * this.spin % 360.0) * Mth.DEG_TO_RAD;
        shown.lift = this.bob == 0.0F ? 0.0F : Mth.sin(state.time(this.bobPeriod) / this.bobPeriod * Mth.TWO_PI) * this.bob;
    }

    @Override
    public void submit(S state, Shown shown, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (shown.item.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(this.x, this.y + shown.lift, this.z);
        if (shown.angle != 0.0F) poseStack.mulPose(this.turn.rotationY(shown.angle));
        poseStack.scale(this.scale, this.scale, this.scale);
        shown.item.submit(poseStack, collector, this.fullBright ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** What an {@link ItemLayer} draws from. */
    public static final class Shown {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float angle, lift;
    }
}
