package net.ixdarklord.coolcatcanvas.api.client.viewport;

import net.ixdarklord.coolcatcanvas.internal.client.viewport.SceneViewportManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A small 3D scene drawn to a texture of its own: blocks, whole structures, entities and items' icons, seen from a camera that
 * circles them. Because it is a texture, what it shows is always kept inside the rectangle it is drawn in, wherever
 * that is: a GUI ({@link net.ixdarklord.coolcatcanvas.api.client.gui.components.SceneViewportWidget}), or a face in
 * the world (use {@link #texture()} with any textured render type).
 * <pre>{@code
 * SceneViewport view = SceneViewport.create(MyMod.id("altar_view"), 256, 256);
 * view.block(BlockPos.ZERO, Blocks.BEACON.defaultBlockState()).camera(35, 28).spin(16);
 * // every frame it is on screen:
 * view.use();
 * }</pre>
 * It is drawn only when it has to be: when its scene or camera has changed, or, while it spins or holds entities
 * (which move), a set number of times a second ({@link #refreshRate}). A viewport nobody has {@linkplain #use() used}
 * for a couple of seconds is not drawn at all. Everything in it is lit as the world lights it: a block's top brightest, each side its own shade,
 * whichever way the scene is turned. The texture is upside down, as every texture drawn to is: show it with its V flipped (the widget
 * does). Client code only; make and change viewports on the render thread.
 */
@ApiStatus.Experimental
public final class SceneViewport implements AutoCloseable {
    /** A block of the scene. */
    public record PlacedBlock(BlockPos pos, BlockState state) {
    }

    /** An entity of the scene: never in a world, only drawn. {@code yaw} turns it about itself. */
    public record PlacedEntity(Entity entity, Vec3 pos, float yaw) {
    }

    /**
     * An item of the scene, drawn as its icon is in an inventory: {@code size} across, its middle at a place, flat to
     * the camera when the camera looks straight on ({@code camera(0, 0)}).
     */
    public record PlacedItem(ItemStack stack, Vec3 pos, float size) {
    }

    private final Identifier texture;
    private final int width, height;
    private final List<PlacedBlock> blocks = new ArrayList<>();
    private final List<PlacedEntity> entities = new ArrayList<>();
    private final List<PlacedItem> items = new ArrayList<>();
    private double frameSpan;
    private Vec3 frameCentre = Vec3.ZERO;
    private float yaw = 35.0F, pitch = 28.0F, zoom = 1.0F, spin;
    private int refreshRate = 60;
    private boolean dirty = true, closed;
    private long lastUsed;

    private SceneViewport(Identifier texture, int width, int height) {
        this.texture = texture;
        this.width = width;
        this.height = height;
    }

    /**
     * @param texture the id its texture is registered under, for drawing it
     * @param width   the texture's size in pixels: what it is drawn at is up to whoever draws it
     * @throws IllegalArgumentException if a viewport already has that id
     */
    public static SceneViewport create(Identifier texture, int width, int height) {
        SceneViewport viewport = new SceneViewport(texture, Mth.clamp(width, 16, 2048), Mth.clamp(height, 16, 2048));
        SceneViewportManager.add(viewport);
        return viewport;
    }

    public Identifier texture() {
        return texture;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // ---- The scene ----

    public SceneViewport clear() {
        blocks.clear();
        entities.clear();
        items.clear();
        dirty = true;
        return this;
    }

    public SceneViewport block(BlockPos pos, BlockState state) {
        if (!state.isAir()) {
            blocks.add(new PlacedBlock(pos.immutable(), state));
            dirty = true;
        }
        return this;
    }

    /** A structure, or any build: every block of it. */
    public SceneViewport blocks(Map<BlockPos, BlockState> all) {
        all.forEach(this::block);
        return this;
    }

    /** An entity standing with its feet at a place. It is drawn as it is each time the viewport is: animate it by ticking it. */
    public SceneViewport entity(Entity entity, double x, double y, double z, float yaw) {
        entities.add(new PlacedEntity(entity, new Vec3(x, y, z), yaw));
        dirty = true;
        return this;
    }

    /**
     * An item's icon. A scene of icons is a picture of them: look straight on with {@code camera(0, 0)} and no spin,
     * and it is drawn once. They are lit as the game lights icons in its screens.
     */
    public SceneViewport item(ItemStack stack, double x, double y, double z, float size) {
        if (!stack.isEmpty()) {
            items.add(new PlacedItem(stack.copy(), new Vec3(x, y, z), size));
            dirty = true;
        }
        return this;
    }

    public List<PlacedItem> items() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Fixes what the picture shows, in place of fitting the scene to it: {@code span} of the scene's own units across
     * the picture's width, with a point of the scene at its middle. For laying a scene out to match something drawn
     * about it (a grid's frames, say). A span of 0 goes back to fitting.
     */
    public SceneViewport frame(double span, double x, double y, double z) {
        this.frameSpan = Math.max(0.0, span);
        this.frameCentre = new Vec3(x, y, z);
        dirty = true;
        return this;
    }

    @ApiStatus.Internal
    public double frameSpan() {
        return frameSpan;
    }

    @ApiStatus.Internal
    public Vec3 frameCentre() {
        return frameCentre;
    }

    public List<PlacedBlock> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public List<PlacedEntity> entities() {
        return Collections.unmodifiableList(entities);
    }

    /** The box the scene fills: what the camera circles and fits in view. */
    public AABB bounds() {
        AABB box = null;
        for (PlacedBlock block : blocks) {
            AABB one = new AABB(block.pos());
            box = box == null ? one : box.minmax(one);
        }
        for (PlacedEntity placed : entities) {
            double half = placed.entity().getBbWidth() / 2.0;
            AABB one = new AABB(placed.pos().x - half, placed.pos().y, placed.pos().z - half, placed.pos().x + half, placed.pos().y + placed.entity().getBbHeight(),
                    placed.pos().z + half);
            box = box == null ? one : box.minmax(one);
        }
        for (PlacedItem placed : items) {
            double half = placed.size() / 2.0;
            AABB one = new AABB(placed.pos().x - half, placed.pos().y - half, placed.pos().z - 0.01, placed.pos().x + half, placed.pos().y + half, placed.pos().z + 0.01);
            box = box == null ? one : box.minmax(one);
        }
        return box == null ? new AABB(0, 0, 0, 1, 1, 1) : box;
    }

    // ---- The camera ----

    /** Where the camera stands about the scene, in degrees: {@code yaw} round it, {@code pitch} above it (0 level, 90 overhead). */
    public SceneViewport camera(float yaw, float pitch) {
        if (this.yaw != yaw || this.pitch != pitch) {
            this.yaw = yaw;
            this.pitch = Mth.clamp(pitch, -90.0F, 90.0F);
            dirty = true;
        }
        return this;
    }

    /** How large the scene is drawn: 1 fits all of it in view from any side. */
    public SceneViewport zoom(float zoom) {
        zoom = Mth.clamp(zoom, 0.1F, 16.0F);
        if (this.zoom != zoom) {
            this.zoom = zoom;
            dirty = true;
        }
        return this;
    }

    /** Turns the camera round the scene by itself, this many degrees a second; 0 to stop. */
    public SceneViewport spin(float degreesPerSecond) {
        this.spin = degreesPerSecond;
        return this;
    }

    /** How many times a second a viewport that moves (it spins, or has entities) is drawn again. 60 by default, which turns smoothly; lower it for a large scene that need not. */
    public SceneViewport refreshRate(int timesPerSecond) {
        this.refreshRate = Mth.clamp(timesPerSecond, 1, 240);
        return this;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float zoom() {
        return zoom;
    }

    public float spin() {
        return spin;
    }

    public int refreshRate() {
        return refreshRate;
    }

    // ---- Drawing ----

    /** Says the viewport is being shown this frame. Without it for a couple of seconds, the viewport stops being drawn. */
    public void use() {
        lastUsed = System.currentTimeMillis();
    }

    /** Whether the viewport has been drawn at least once, so that its texture exists: it is, from the frame after it is first used. */
    public boolean isReady() {
        return !closed && SceneViewportManager.view(this) != null;
    }

    /** Has the viewport drawn again though nothing of its own changed: a block's model, or an entity, did. */
    public void refresh() {
        dirty = true;
    }

    /** Frees the texture. The viewport draws nothing after this. */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            SceneViewportManager.remove(this);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    @ApiStatus.Internal
    public long lastUsed() {
        return lastUsed;
    }

    /** Whether the scene or camera has changed since it was last drawn; asking clears it. */
    @ApiStatus.Internal
    public boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }
}
