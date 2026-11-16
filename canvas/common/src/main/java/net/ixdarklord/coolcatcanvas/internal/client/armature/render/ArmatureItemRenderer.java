package net.ixdarklord.coolcatcanvas.internal.client.armature.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.armature.render.ArmatureItems;
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureInstances;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Items drawn with an armature, each stack animated on its own. 1.20.1 has no item model types, so an item names its
 * armature in its own model file, {@code models/item/<item>.json}, beside {@code "parent": "builtin/entity"} (which
 * makes the game hand it to code to draw): {@code "coolcatcanvas:armature": {"armature": ..., "animation": ...,
 * "texture": ..., "translation": [x, y, z], "scale": [x, y, z]}}. It sits in the item's model space as a block model
 * does, standing on the bottom middle of the 0-16 cube; translation (in blocks) and scale move it there first.
 */
public final class ArmatureItemRenderer {
    public static final String KEY = CoolCatCanvas.MOD_ID + ":armature";
    // Each item's armature, read from its model file once a resource load (empty for an item without one).
    private static final Map<Item, Optional<ArmatureItemRenderer>> BY_ITEM = new ConcurrentHashMap<>();

    private final Armature armature;
    private final @Nullable Motion animation;
    private final @Nullable ResourceLocation texture;
    private final Vector3f translation;
    private final Vector3f scale;

    private ArmatureItemRenderer(Armature armature, @Nullable Motion animation, @Nullable ResourceLocation texture, Vector3f translation, Vector3f scale) {
        this.armature = armature;
        this.animation = animation;
        this.texture = texture;
        this.translation = translation;
        this.scale = scale;
    }

    /** Forgets what each item's model file said, after resources load again. */
    public static void reload() {
        BY_ITEM.clear();
    }

    /**
     * Draws a stack with its armature, if its item's model names one; from the game's code-drawn item renderer
     * (BlockEntityWithoutLevelRendererMixin), the pose already in the model's display transform.
     */
    public static boolean render(ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (stack.isEmpty()) return false;
        Optional<ArmatureItemRenderer> renderer = BY_ITEM.computeIfAbsent(stack.getItem(), ArmatureItemRenderer::read);
        if (renderer.isEmpty()) return false;
        renderer.get().draw(stack, poseStack, buffers, light, overlay);
        return true;
    }

    private void draw(ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Consumer<Animator> setup = ArmatureItems.setup(stack.getItem());
        Animator animator = ArmatureInstances.get(stack, this.armature, a -> {
            if (this.animation != null) a.play(this.animation);
            if (setup != null) setup.accept(a);
        });
        ArmatureFrame frame = animator.update(GameClock.seconds(), 0.0F, placement(this.armature.get()));
        poseStack.pushPose();
        poseStack.translate(this.translation.x, this.translation.y, this.translation.z);
        poseStack.scale(this.scale.x, this.scale.y, this.scale.z);
        poseStack.translate(0.5F, 0.0F, 0.5F);
        ResourceLocation texture = this.texture;
        ArmatureDraw.submit(frame, poseStack, buffers, ArmatureDraw.Style.lit(light)
                .overlay(overlay)
                .foil(stack.hasFoil())
                .texture(texture == null ? null : slot -> slot == 0 ? texture : null));
        poseStack.popPose();
    }

    // Items have no place in the world to speak of; this only sets the model's size for springs and pointing.
    private static Matrix4f placement(BakedArmature armature) {
        return new Matrix4f().translation(0.5F, 0.0F, 0.5F).mul(ArmatureDraw.modelTransform(armature));
    }

    private static Optional<ArmatureItemRenderer> read(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        ResourceLocation file = id.withPath(path -> "models/item/" + path + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return Optional.empty();
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || !json.getAsJsonObject().has(KEY)) return Optional.empty();
            JsonObject spec = json.getAsJsonObject().getAsJsonObject(KEY);
            Armature armature = Armature.of(new ResourceLocation(spec.get("armature").getAsString()));
            Motion animation = spec.has("animation") ? Motion.loop(spec.get("animation").getAsString()) : null;
            ResourceLocation texture = spec.has("texture") ? new ResourceLocation(spec.get("texture").getAsString()) : null;
            return Optional.of(new ArmatureItemRenderer(armature, animation, texture, vector(spec, "translation", 0.0F), vector(spec, "scale", 1.0F)));
        } catch (Exception e) {
            CoolCatCanvas.LOGGER.error("Couldn't read the armature of item model {}", file, e);
            return Optional.empty();
        }
    }

    // Three numbers, or one for all three.
    private static Vector3f vector(JsonObject spec, String key, float fallback) {
        if (!spec.has(key)) return new Vector3f(fallback);
        JsonElement value = spec.get(key);
        if (value.isJsonPrimitive()) return new Vector3f(value.getAsFloat());
        JsonArray array = value.getAsJsonArray();
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
