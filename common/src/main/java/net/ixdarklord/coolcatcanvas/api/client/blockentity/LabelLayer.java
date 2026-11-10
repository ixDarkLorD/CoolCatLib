package net.ixdarklord.coolcatcanvas.api.client.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Lines of text floating over a block entity, facing the camera: a name, an owner, a count. Shown always, only while
 * the player looks at its block, or never, and only so far off.
 * <pre>{@code
 * this.layer(LabelLayer.<ShrineBlockEntity, State>of(2, (shrine, lines) -> {
 *     lines[0] = shrine.title();
 *     lines[1] = shrine.ownerName();
 * }).at(0.5F, 1.6F, 0.5F).show(shrine -> LabelLayer.Show.LOOKED_AT).range(12.0));
 * }</pre>
 * The lines go into an array kept with each state, so give it components you hold (they keep their own laid-out text)
 * rather than new ones each frame.
 */
public final class LabelLayer<T extends BlockEntity, S extends BlockRenderState> implements BlockRenderLayer<T, S, LabelLayer.Lines> {
    private static final int LINE_HEIGHT = 10;

    private final int maxLines;
    private final LineSource<? super T> source;
    private Function<? super T, Show> show = blockEntity -> Show.ALWAYS;
    private float x = 0.5F, y = 1.5F, z = 0.5F, scale = 0.025F;
    private double range = 16.0;
    private int color = 0xFFFFFFFF, background = 0x40000000;
    private boolean shadow, fullBright = true;
    private int detail = Detail.MINIMAL;

    private LabelLayer(int maxLines, LineSource<? super T> source) {
        this.maxLines = maxLines;
        this.source = source;
    }

    /** Up to {@code maxLines} lines, which {@code source} fills in from the top (a line left empty is skipped). */
    public static <T extends BlockEntity, S extends BlockRenderState> LabelLayer<T, S> of(int maxLines, LineSource<? super T> source) {
        return new LabelLayer<>(maxLines, source);
    }

    /** One line. */
    public static <T extends BlockEntity, S extends BlockRenderState> LabelLayer<T, S> of(Function<? super T, @Nullable Component> line) {
        return new LabelLayer<>(1, (blockEntity, lines) -> lines[0] = line.apply(blockEntity));
    }

    /** Where its bottom middle floats, in blocks from the block's corner; (0.5, 1.5, 0.5) by default. */
    public LabelLayer<T, S> at(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /** How large its text is, in blocks a pixel of it; 0.025 by default (a name tag's). */
    public LabelLayer<T, S> scale(float scale) {
        this.scale = scale;
        return this;
    }

    /** When it's shown, for each block entity; always by default. */
    public LabelLayer<T, S> show(Function<? super T, Show> show) {
        this.show = show;
        return this;
    }

    /** How far off, in blocks, it's still shown (farther for one with a {@link BlockRenderer#reach}); 16 by default. */
    public LabelLayer<T, S> range(double blocks) {
        this.range = blocks;
        return this;
    }

    /** Its text's color and its background's (ARGB; a background of 0 has none). White on dark see-through by default. */
    public LabelLayer<T, S> colors(int text, int background) {
        this.color = text;
        this.background = background;
        return this;
    }

    public LabelLayer<T, S> shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    /** Draws it in its block's light rather than fully lit. */
    public LabelLayer<T, S> lit() {
        this.fullBright = false;
        return this;
    }

    /** The least detail it's still drawn at; {@link Detail#MINIMAL} by default ({@link #range} limits it too). */
    public LabelLayer<T, S> upTo(int detail) {
        this.detail = detail;
        return this;
    }

    @Override
    public int detail() {
        return this.detail;
    }

    @Override
    public Lines newState() {
        return new Lines(this.maxLines);
    }

    @Override
    public void extract(T blockEntity, S state, Lines lines, BlockRenderContext context) {
        Arrays.fill(lines.lines, null);
        lines.count = 0;
        double range = this.range * state.reach;
        if (state.distanceSq > range * range) return;
        boolean shown = switch (this.show.apply(blockEntity)) {
            case ALWAYS -> true;
            case LOOKED_AT -> state.lookedAt || lookedAt(state);
            case NEVER -> false;
        };
        if (!shown) return;
        this.source.lines(blockEntity, lines.lines);
        for (Component line : lines.lines) if (line != null) lines.count++;
    }

    @Override
    public void submit(S state, Lines lines, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (lines.count == 0) return;
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(this.x, this.y, this.z);
        BlockRenderer.unturn(state, poseStack);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(this.scale, -this.scale, this.scale);
        int light = this.fullBright ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        float top = -lines.count * LINE_HEIGHT;
        for (Component line : lines.lines) {
            if (line == null) continue;
            collector.submitText(poseStack, -font.width(line) / 2.0F, top, line.getVisualOrderText(), this.shadow, Font.DisplayMode.NORMAL,
                    light, this.color, this.background, 0);
            top += LINE_HEIGHT;
        }
        poseStack.popPose();
    }

    private static boolean lookedAt(BlockRenderState state) {
        return Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(state.blockPos);
    }

    /** When a {@link LabelLayer} is shown. */
    public enum Show {
        ALWAYS,
        LOOKED_AT,
        NEVER
    }

    /** Fills in a block entity's lines; {@code lines} comes empty, and a line left empty is skipped. */
    @FunctionalInterface
    public interface LineSource<T> {
        void lines(T blockEntity, @Nullable Component[] lines);
    }

    /** What a {@link LabelLayer} draws from. */
    public static final class Lines {
        public final @Nullable Component[] lines;
        public int count;

        Lines(int maxLines) {
            this.lines = new Component[maxLines];
        }
    }
}
