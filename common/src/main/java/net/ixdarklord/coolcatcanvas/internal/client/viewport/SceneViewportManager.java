package net.ixdarklord.coolcatcanvas.internal.client.viewport;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Scene viewports: each has a colour and a depth texture, drawn to at the start of a frame (GameRendererMixin), before
// the level, when nothing else is waiting to be drawn. It is the way the game draws an entity into the inventory
// screen (its picture-in-picture renderers): the output textures are overridden, an orthographic projection is set,
// the scene is submitted to the game's own feature renderers and they are run. So blocks, block entities' special
// models and entities are drawn by the game's own code, lit by its own UI lighting.
public final class SceneViewportManager {
    // How long a viewport nobody uses is still drawn, in milliseconds.
    private static final long IDLE = 2000L;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();

    // A viewport's textures, shown through the texture manager under the viewport's id.
    private static final class Target extends AbstractTexture {
        GpuTexture depth;
        GpuTextureView depthView;
        final Projection projection = new Projection();
        final ProjectionMatrixBuffer projectionBuffer;
        long lastDrawn;
        // One model for each kind of block in the scene, made when the scene changes.
        final Map<BlockState, BlockModelRenderState> models = new HashMap<>();
        float spun;
        // One drawing for each item's icon in the scene, made when the scene changes.
        final Map<SceneViewport.PlacedItem, ItemStackRenderState> icons = new HashMap<>();
        // How far the scene reaches from its middle, across and up the picture, worked out when it changes.
        double reach, rise, fitFor = -1.0;

        Target(SceneViewport viewport) {
            GpuDevice device = RenderSystem.getDevice();
            String name = viewport.texture().toString();
            // Drawn to, sampled from, and copied (13), as the game's own are; the depth is drawn to and sampled (9).
            texture = device.createTexture(() -> "Scene viewport " + name, 13, GpuFormat.RGBA8_UNORM, viewport.width(), viewport.height(), 1, 1);
            textureView = device.createTextureView(texture);
            depth = device.createTexture(() -> "Scene viewport " + name + " depth", 9, GpuFormat.D32_FLOAT, viewport.width(), viewport.height(), 1, 1);
            depthView = device.createTextureView(depth);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            projectionBuffer = new ProjectionMatrixBuffer("Scene viewport " + name);
        }

        @Override
        public void close() {
            super.close();
            depthView.close();
            depth.close();
            projectionBuffer.close();
        }
    }

    private static final Map<SceneViewport, Target> TARGETS = new LinkedHashMap<>();
    private static final Map<Identifier, SceneViewport> BY_ID = new HashMap<>();
    private static BlockModelResolver resolver;

    private SceneViewportManager() {}

    public static void add(SceneViewport viewport) {
        if (BY_ID.putIfAbsent(viewport.texture(), viewport) != null) throw new IllegalArgumentException("Scene viewport " + viewport.texture() + " already exists");
        TARGETS.put(viewport, null);
    }

    public static void remove(SceneViewport viewport) {
        BY_ID.remove(viewport.texture());
        Target target = TARGETS.remove(viewport);
        if (target != null) Minecraft.getInstance().getTextureManager().release(viewport.texture());
    }

    /** The texture view of a viewport, for drawing it in a GUI; null before it has been drawn once. */
    public static GpuTextureView view(SceneViewport viewport) {
        Target target = TARGETS.get(viewport);
        return target == null ? null : target.getTextureView();
    }

    // Start of the frame: draws every viewport that is in use and due.
    public static void beginFrame(DeltaTracker deltaTracker) {
        if (TARGETS.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<SceneViewport> failed = null;
        for (Map.Entry<SceneViewport, Target> entry : TARGETS.entrySet()) {
            SceneViewport viewport = entry.getKey();
            if (now - viewport.lastUsed() > IDLE) continue;
            Target target = entry.getValue();
            boolean changed = viewport.takeDirty();
            boolean moves = viewport.spin() != 0.0F || !viewport.entities().isEmpty();
            if (target != null && !changed && !(moves && now - target.lastDrawn >= 1000L / viewport.refreshRate())) continue;
            try {
                if (target == null) {
                    target = new Target(viewport);
                    entry.setValue(target);
                    Minecraft.getInstance().getTextureManager().register(viewport.texture(), target);
                    changed = true;
                }
                if (changed) {
                    target.models.clear();
                    target.icons.clear();
                    target.reach = 0.0;
                }
                if (target.lastDrawn != 0L) target.spun += viewport.spin() * Math.min(now - target.lastDrawn, 250L) / 1000.0F;
                target.lastDrawn = now;
                draw(viewport, target, deltaTracker.getGameTimeDeltaPartialTick(false));
            } catch (RuntimeException e) {
                CoolCatCanvas.LOGGER.error("Scene viewport {} failed and was closed", viewport.texture(), e);
                if (failed == null) failed = new ArrayList<>();
                failed.add(viewport);
            } finally {
                RenderSystem.outputColorTextureOverride = null;
                RenderSystem.outputDepthTextureOverride = null;
            }
        }
        if (failed != null) failed.forEach(SceneViewport::close);
    }

    private static void draw(SceneViewport viewport, Target target, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = viewport.width(), height = viewport.height();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getTexture(), new org.joml.Vector4f(0), target.depth, 0.0);
        target.projection.setupOrtho(-1000.0F, 1000.0F, width, height, true);
        RenderSystem.setProjectionMatrix(target.projectionBuffer.getBuffer(target.projection), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.outputColorTextureOverride = target.getTextureView();
        RenderSystem.outputDepthTextureOverride = target.depthView;

        AABB bounds = viewport.bounds();
        Vec3 centre = bounds.getCenter();
        // As large as it can be and still stay in the picture however far round it is turned. What counts is not the
        // scene's box but the scene: how far its farthest corner is from the upright line through its middle (that is
        // its half width from every side), and how far up or down the picture any corner comes at the camera's pitch.
        // So a round ruin fills the picture's width, though the box about it has empty corners.
        double pitch = Math.toRadians(Math.abs(viewport.pitch()));
        if (target.fitFor != pitch || target.reach <= 0.0) {
            double reach = 0.25, rise = 0.25;
            for (SceneViewport.PlacedBlock block : viewport.blocks()) {
                for (int corner = 0; corner < 8; corner++) {
                    double dx = block.pos().getX() + (corner & 1) - centre.x, dy = block.pos().getY() + (corner >> 1 & 1) - centre.y, dz = block.pos().getZ() + (corner >> 2) - centre.z;
                    double out = Math.sqrt(dx * dx + dz * dz);
                    reach = Math.max(reach, out);
                    rise = Math.max(rise, Math.abs(dy) * Math.cos(pitch) + out * Math.sin(pitch));
                }
            }
            for (SceneViewport.PlacedEntity placed : viewport.entities()) {
                double half = placed.entity().getBbWidth() / 2.0 * 1.42;
                double out = Math.sqrt((placed.pos().x - centre.x) * (placed.pos().x - centre.x) + (placed.pos().z - centre.z) * (placed.pos().z - centre.z)) + half;
                double up = Math.max(Math.abs(placed.pos().y - centre.y), Math.abs(placed.pos().y + placed.entity().getBbHeight() - centre.y));
                reach = Math.max(reach, out);
                rise = Math.max(rise, up * Math.cos(pitch) + out * Math.sin(pitch));
            }
            target.reach = reach;
            target.rise = rise;
            target.fitFor = pitch;
        }
        float fitted = (float) Math.min(width * 0.485 / target.reach, height * 0.485 / target.rise) * viewport.zoom();
        // Or it is framed: so much of the scene across the picture, about a point of it.
        boolean framed = viewport.frameSpan() > 0.0;
        float scale = framed ? (float) (width / viewport.frameSpan()) * viewport.zoom() : fitted;
        Vec3 middle = framed ? viewport.frameCentre() : centre;

        // Text laid flat on the picture, in its own pixels.
        if (!viewport.texts().isEmpty()) {
            PoseStack flat = new PoseStack();
            SubmitNodeStorage texts = new SubmitNodeStorage();
            for (SceneViewport.PlacedText placed : viewport.texts()) {
                flat.pushPose();
                flat.translate(placed.x(), placed.y(), 0.0F);
                flat.scale(placed.scale(), placed.scale(), 1.0F);
                texts.submitText(flat, 0.0F, 0.0F, placed.text(), false, net.minecraft.client.gui.Font.DisplayMode.NORMAL, FULL_BRIGHT, placed.color(), 0, 0);
                flat.popPose();
            }
            minecraft.gameRenderer.featureRenderDispatcher().renderAllFeatures(texts);
            if (viewport.blocks().isEmpty() && viewport.entities().isEmpty() && viewport.items().isEmpty()) return;
        }

        // Items' icons first, on their own: they are lit as icons are, flat ones from the front and blocks' from above
        // and the side, which are two lightings, so they are drawn in two goes.
        if (!viewport.items().isEmpty()) {
            for (int go = 0; go < 2; go++) {
                boolean blockLit = go == 1;
                boolean any = false;
                PoseStack icons = view(viewport, target, width, height, scale, middle);
                SubmitNodeStorage iconStorage = new SubmitNodeStorage();
                for (SceneViewport.PlacedItem placed : viewport.items()) {
                    ItemStackRenderState icon = target.icons.computeIfAbsent(placed, item -> {
                        ItemStackRenderState made = new ItemStackRenderState();
                        minecraft.getItemModelResolver().updateForTopItem(made, item.stack(), ItemDisplayContext.GUI, minecraft.level, null, 0);
                        return made;
                    });
                    if (icon.isEmpty() || icon.usesBlockLight() != blockLit) continue;
                    any = true;
                    icons.pushPose();
                    icons.translate(placed.pos().x, placed.pos().y, placed.pos().z);
                    icons.scale(placed.size(), placed.size(), placed.size());
                    icon.submit(icons, iconStorage, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                    icons.popPose();
                }
                if (any) {
                    minecraft.gameRenderer.lighting().setupFor(blockLit ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
                    minecraft.gameRenderer.featureRenderDispatcher().renderAllFeatures(iconStorage);
                }
            }
            if (viewport.blocks().isEmpty() && viewport.entities().isEmpty()) return;
        }

        PoseStack pose = view(viewport, target, width, height, scale, middle);
        // The scene is lit as the world is: by the level's own two lights, which stand still about the scene and not
        // about the camera. For that its faces' normals are left as the models give them (the camera's turning is
        // taken out of them), so a block's top is always its brightest face and each side keeps its own shade as the
        // scene turns, as blocks standing in the world do.
        pose.last().normal().identity();
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
        FeatureRenderDispatcher features = minecraft.gameRenderer.featureRenderDispatcher();
        SubmitNodeStorage storage = new SubmitNodeStorage();

        if (resolver == null) resolver = new BlockModelResolver(minecraft.getModelManager());
        for (SceneViewport.PlacedBlock block : viewport.blocks()) {
            BlockModelRenderState model = target.models.computeIfAbsent(block.state(), state -> {
                BlockModelRenderState made = new BlockModelRenderState();
                resolver.update(made, state, CONTEXT);
                return made;
            });
            if (model.isEmpty()) continue;
            pose.pushPose();
            pose.translate(block.pos().getX(), block.pos().getY(), block.pos().getZ());
            model.submit(pose, storage, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }

        if (viewport.hasGround()) ground(viewport, pose, storage);

        EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
        CameraRenderState camera = new CameraRenderState();
        // Things that face the camera (a name, a flame) face out of the picture.
        camera.orientation = new Quaternionf().rotateY((float) Math.PI);
        for (SceneViewport.PlacedEntity placed : viewport.entities()) {
            EntityRenderState state = entities.extractEntity(placed.entity(), partialTick);
            state.lightCoords = FULL_BRIGHT;
            pose.pushPose();
            pose.translate(placed.pos().x, placed.pos().y, placed.pos().z);
            pose.mulPose(Axis.YP.rotationDegrees(placed.yaw()));
            entities.submit(state, camera, 0.0, 0.0, 0.0, pose, storage);
            pose.popPose();
        }

        features.renderAllFeatures(storage);
    }

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("coolcatlib", "textures/misc/white.png");
    private static final Identifier SHADOW = Identifier.withDefaultNamespace("textures/misc/shadow.png");

    // The ground: grid lines lying on it, thin strips fading towards its edge, and a shadow under each entity.
    private static void ground(SceneViewport viewport, PoseStack pose, SubmitNodeStorage storage) {
        float y = (float) viewport.groundY();
        int margin = viewport.groundCells();
        int color = viewport.groundColor();
        // Under the scene and no further: the whole blocks its things stand over, and `margin` more each way.
        net.minecraft.world.phys.AABB box = viewport.bounds();
        float x0 = (float) Math.floor(box.minX + 1.0E-4) - margin;
        float x1 = (float) Math.ceil(box.maxX - 1.0E-4) + margin;
        float z0 = (float) Math.floor(box.minZ + 1.0E-4) - margin;
        float z1 = (float) Math.ceil(box.maxZ - 1.0E-4) + margin;
        int floor = viewport.groundFloor();
        if ((floor >>> 24) != 0) {
            storage.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(WHITE), (last, consumer) ->
                    flat(consumer, last, x0, z0, x1, z1, y, floor));
        }
        if ((color >>> 24) != 0) {
            float thick = 0.03F;
            storage.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(WHITE), (last, consumer) -> {
                // A line on every block edge, the outermost ones on the ground's own edge.
                for (float at = x0; at <= x1 + 1.0E-4F; at++) {
                    flat(consumer, last, Math.max(x0, at - thick), z0, Math.min(x1, at + thick), z1, y + 0.001F, color);
                }
                for (float at = z0; at <= z1 + 1.0E-4F; at++) {
                    flat(consumer, last, x0, Math.max(z0, at - thick), x1, Math.min(z1, at + thick), y + 0.001F, color);
                }
            });
        }
        if (viewport.groundShadows()) {
            for (SceneViewport.PlacedEntity placed : viewport.entities()) {
                float radius = Math.max(0.3F, placed.entity().getBbWidth() * 0.65F);
                float x = (float) placed.pos().x;
                float z = (float) placed.pos().z;
                storage.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(SHADOW), (last, consumer) -> {
                    int shade = 0x50FFFFFF;
                    float h = y + 0.002F;
                    consumer.addVertex(last, x - radius, h, z - radius).setColor(shade).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(last, 0, 1, 0);
                    consumer.addVertex(last, x - radius, h, z + radius).setColor(shade).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(last, 0, 1, 0);
                    consumer.addVertex(last, x + radius, h, z + radius).setColor(shade).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(last, 0, 1, 0);
                    consumer.addVertex(last, x + radius, h, z - radius).setColor(shade).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(last, 0, 1, 0);
                });
            }
        }
    }

    private static void flat(com.mojang.blaze3d.vertex.VertexConsumer consumer, PoseStack.Pose last, float x0, float z0, float x1, float z1, float y, int color) {
        consumer.addVertex(last, x0, y, z0).setColor(color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(last, 0, 1, 0);
        consumer.addVertex(last, x0, y, z1).setColor(color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(last, 0, 1, 0);
        consumer.addVertex(last, x1, y, z1).setColor(color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(last, 0, 1, 0);
        consumer.addVertex(last, x1, y, z0).setColor(color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(last, 0, 1, 0);
    }

    // The camera: from the scene's own space to the picture's.
    private static PoseStack view(SceneViewport viewport, Target target, int width, int height, float scale, Vec3 middle) {
        PoseStack pose = new PoseStack();
        pose.translate(width / 2.0F, height / 2.0F, 0.0F);
        // As the game sets a picture up: down the screen is +y there, so the scene is turned upright, and the depth
        // is turned to match.
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-viewport.pitch()));
        pose.mulPose(Axis.YP.rotationDegrees(viewport.yaw() + target.spun + 180.0F));
        pose.translate(-middle.x, -middle.y, -middle.z);
        return pose;
    }

    // The game's models were reloaded: every block's model is made again.
    public static void onResourceReload() {
        resolver = null;
        TARGETS.forEach((viewport, target) -> viewport.refresh());
    }

    public static void close() {
        TARGETS.values().forEach(target -> {
            if (target != null) target.close();
        });
        TARGETS.clear();
        BY_ID.clear();
    }
}
