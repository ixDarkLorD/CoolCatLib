package net.ixdarklord.coolcatcanvas.internal.client.cutscene.editor;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxDraw;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxFrame;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxMaterial;
import net.ixdarklord.coolcatcanvas.api.client.vfx.WorldVfx;
import net.ixdarklord.coolcatcanvas.api.cutscene.CameraKeyframe;
import net.ixdarklord.coolcatcanvas.api.cutscene.CameraPose;
import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.api.cutscene.Interpolation;
import net.ixdarklord.coolcatcanvas.api.cutscene.ValueTrack;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneFiles;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.PlaybackImpl;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.DropdownScreen;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcore.api.client.registry.KeyMappingRegistry;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// The in-game cutscene editor's working state: the cutscene being made, as keyframes that can change, and the things
// around it. It lives on while the editor's screen is closed, so the player can fly to the next spot and drop a
// keyframe there (K) with the path drawn in the world around them, then open the timeline again. The screen
// (CutsceneEditorScreen) previews through a paused playback that is scrubbed and, for "play", let run.
public final class CutsceneEditor {
    private static final KeyMapping OPEN = new KeyMapping("key.coolcatlib.cutscene_editor", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);
    private static final Identifier PATH_RENDERER = CoolCatCanvas.rl("cutscene_editor_path");
    private static final VfxMaterial DOT = VfxMaterial.alpha(VfxMaterial.GLOW).withSoftness(0.1F);
    private static final VfxMaterial LINE = VfxMaterial.alpha(VfxMaterial.STREAK).withSoftness(0.1F);
    private static final VfxMaterial RING = VfxMaterial.alpha(VfxMaterial.RING).withSoftness(0.1F);
    /** How far after the last keyframe a new one is put, in ticks. */
    static final float NEW_KEY_GAP = 40.0F;
    private static final int PATH_COLOR = 0xD07FD4FF;
    private static final int KEY_COLOR = 0xFFFFC857;
    private static final int SELECTED_COLOR = 0xFFFFFFFF;

    /** A keyframe as the editor holds it: every part can be set on its own. Positions are in the cutscene's space. */
    static final class Key {
        float time;
        double x, y, z;
        float yaw, pitch, roll, fov;
        Easing easing = Easing.SINE_IN_OUT;
        Interpolation interpolation = Interpolation.SMOOTH;

        CameraKeyframe toKeyframe() {
            return new CameraKeyframe(this.time, new Vec3(this.x, this.y, this.z), this.yaw, this.pitch, this.roll, Mth.clamp(this.fov, 0.0F, 170.0F), this.easing, this.interpolation);
        }

        static Key of(CameraKeyframe keyframe) {
            Key key = new Key();
            key.time = keyframe.time();
            key.x = keyframe.position().x;
            key.y = keyframe.position().y;
            key.z = keyframe.position().z;
            key.yaw = keyframe.yaw();
            key.pitch = keyframe.pitch();
            key.roll = keyframe.roll();
            key.fov = keyframe.fov();
            key.easing = keyframe.easing();
            key.interpolation = keyframe.interpolation();
            return key;
        }
    }

    // ---- The session ----
    static boolean active;
    static String name = "untitled";
    static final List<Key> keys = new ArrayList<>();
    static @Nullable Key selected;
    static float playhead;
    // What the cutscene has that the editor doesn't change: focuses, subtitles, markers, tracks.
    private static @Nullable Cutscene template;
    static float letterbox = Cutscene.CINEMATIC_ASPECT;
    static boolean hideHud = true;
    static float blendIn, blendOut, fadeIn, fadeOut, endHold;
    private static boolean fadeEdited;
    // Where the cutscene's space sits in the world while it is edited (a player-anchored one: where they stood on loading it).
    static Vec3 origin = Vec3.ZERO;
    static float originYaw;
    /** Whether the screen looks through the cutscene's camera, or leaves the player's own eyes to look at the path. */
    static boolean cameraView = true;
    static boolean dirty;
    private static @Nullable PlaybackImpl preview;
    private static @Nullable Cutscene built;
    private static boolean builtStale = true;
    private static boolean addKeyWasDown;

    private CutsceneEditor() {}

    public static void init() {
        KeyMappingRegistry.register(OPEN);
        WorldVfx.register(PATH_RENDERER, CutsceneEditor::renderPath);
    }

    /** Opens the editor's screen, starting a session if there is none. */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        active = true;
        minecraft.setScreen(new CutsceneEditorScreen());
    }

    /** Opens it once the chat a command was typed in has closed. */
    public static void openLater() {
        if (Minecraft.getInstance().player == null) return;
        active = true;
        ClientConfigManager.openLater(CutsceneEditorScreen::new);
    }

    /** Ends the session: the path is no longer drawn. What isn't saved stays until a new one is started. */
    static void exit() {
        active = false;
        dropPreview();
    }

    static void startNew() {
        keys.clear();
        selected = null;
        playhead = 0.0F;
        template = null;
        name = "untitled";
        letterbox = Cutscene.CINEMATIC_ASPECT;
        hideHud = true;
        blendIn = blendOut = fadeIn = fadeOut = endHold = 0.0F;
        fadeEdited = false;
        origin = Vec3.ZERO;
        originYaw = 0.0F;
        dirty = false;
        changed(false);
    }

    /** Takes a cutscene to edit. A player-anchored one is placed where the player stands now. */
    static void load(String newName, Cutscene cutscene) {
        LocalPlayer player = Minecraft.getInstance().player;
        startNew();
        name = newName;
        template = cutscene;
        for (CameraKeyframe keyframe : cutscene.keyframes()) keys.add(Key.of(keyframe));
        letterbox = cutscene.letterbox();
        hideHud = cutscene.hideHud();
        blendIn = cutscene.blendIn();
        blendOut = cutscene.blendOut();
        endHold = Math.max(0.0F, cutscene.duration() - lastKeyTime());
        if (player != null) {
            switch (cutscene.anchor()) {
                case WORLD -> {}
                case PLAYER -> origin = player.position();
                case PLAYER_FACING -> {
                    origin = player.position();
                    originYaw = player.getYRot();
                }
            }
        }
        selected = keys.isEmpty() ? null : keys.getFirst();
        dirty = false;
        changed(false);
    }

    /** Saves under the session's name; the message says where, or what went wrong. */
    static Component save() {
        Cutscene cutscene = build();
        if (cutscene == null) return Component.translatableWithFallback("cutscene.coolcatlib.editor.save.empty", "Add a keyframe first");
        if (!CutsceneFiles.isValidName(name)) return Component.translatableWithFallback("cutscene.coolcatlib.editor.save.bad_name", "Name it with a-z, 0-9, _ and - only");
        try {
            Path file = CutsceneFiles.save(name, cutscene);
            dirty = false;
            return Component.translatableWithFallback("cutscene.coolcatlib.editor.save.done", "Saved to %s", "config/coolcatlib/cutscenes/" + file.getFileName());
        } catch (IOException | RuntimeException e) {
            CoolCatCanvas.LOGGER.warn("Couldn't save the cutscene {}", name, e);
            return Component.translatableWithFallback("cutscene.coolcatlib.editor.save.failed", "Couldn't save: %s", String.valueOf(e.getMessage()));
        }
    }

    // ---- Changing it ----

    static float lastKeyTime() {
        float last = 0.0F;
        for (Key key : keys) last = Math.max(last, key.time);
        return last;
    }

    static float duration() {
        float duration = lastKeyTime() + endHold;
        return duration > 0.0F ? duration : 20.0F;
    }

    /** Call after any change: orders the keyframes, keeps their times apart, and refreshes the preview. */
    static void changed() {
        changed(true);
    }

    private static void changed(boolean edit) {
        if (edit) dirty = true;
        keys.sort(Comparator.comparingDouble(key -> key.time));
        for (int i = 1; i < keys.size(); i++) {
            if (keys.get(i).time <= keys.get(i - 1).time) keys.get(i).time = keys.get(i - 1).time + 1.0F;
        }
        playhead = Mth.clamp(playhead, 0.0F, duration());
        builtStale = true;
        if (preview != null) {
            Cutscene cutscene = build();
            if (cutscene == null) dropPreview();
            else preview.setCutscene(cutscene, origin, originYaw);
        }
    }

    static void markFadeEdited() {
        fadeEdited = true;
    }

    /** The cutscene as it stands, or null with no keyframes yet. */
    static @Nullable Cutscene build() {
        if (!builtStale) return built;
        builtStale = false;
        built = null;
        if (keys.isEmpty()) return null;
        try {
            Cutscene.Builder builder = template != null ? template.toBuilder() : Cutscene.builder();
            builder.keyframes(keys.stream().map(Key::toKeyframe).toList())
                    .duration(duration())
                    .letterbox(Math.max(0.0F, letterbox))
                    .hideHud(hideHud)
                    .blendIn(Math.max(0.0F, blendIn))
                    .blendOut(Math.max(0.0F, blendOut));
            if (fadeEdited || template == null) builder.fade(ValueTrack.EMPTY).fadeIn(Math.max(0.0F, fadeIn)).fadeOut(Math.max(0.0F, fadeOut));
            built = builder.build();
        } catch (IllegalArgumentException e) {
            CoolCatCanvas.LOGGER.debug("The edited cutscene can't be built: {}", e.getMessage());
        }
        return built;
    }

    /** A new keyframe where the player's eyes are, after the last one (or at the start). */
    static @Nullable Key addKeyFromView() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        Key key = new Key();
        key.time = keys.isEmpty() ? 0.0F : Math.round(lastKeyTime() + NEW_KEY_GAP);
        setToView(key, player);
        keys.add(key);
        selected = key;
        playhead = key.time;
        changed();
        if (preview != null) preview.seek(playhead);
        return key;
    }

    /** A new keyframe at the playhead, on the path as it is there: for bending a move without flying to it. */
    static @Nullable Key addKeyAtPlayhead() {
        Cutscene cutscene = build();
        if (cutscene == null) return addKeyFromView();
        for (Key key : keys) if (Math.abs(key.time - playhead) < 0.5F) return null;
        CameraPose pose = cutscene.poseAt(playhead, 0.0F);
        Key key = new Key();
        key.time = Math.round(playhead);
        key.x = round3(pose.position().x);
        key.y = round3(pose.position().y);
        key.z = round3(pose.position().z);
        key.yaw = round1(Mth.wrapDegrees(pose.yaw()));
        key.pitch = round1(pose.pitch());
        key.roll = round1(pose.roll());
        key.fov = pose.fov() <= 1.0F ? 0.0F : round1(pose.fov());
        keys.add(key);
        selected = key;
        changed();
        return key;
    }

    /** Moves a keyframe to the player's eyes, in the cutscene's space. */
    static void setToView(Key key, LocalPlayer player) {
        Vec3 local = CameraPose.rotate(player.getEyePosition().subtract(origin), -originYaw);
        key.x = round3(local.x);
        key.y = round3(local.y);
        key.z = round3(local.z);
        key.yaw = round1(Mth.wrapDegrees(player.getYRot() - originYaw));
        key.pitch = round1(player.getXRot());
    }

    static void delete(Key key) {
        int index = keys.indexOf(key);
        if (index < 0) return;
        keys.remove(index);
        if (selected == key) selected = keys.isEmpty() ? null : keys.get(Math.min(index, keys.size() - 1));
        changed();
    }

    /** A keyframe's pose in the world. */
    static CameraPose worldPose(Key key) {
        return new CameraPose(new Vec3(key.x, key.y, key.z), key.yaw, key.pitch, key.roll, key.fov).transformed(origin, originYaw);
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static float round1(float value) {
        return Math.round(value * 10.0F) / 10.0F;
    }

    static String format(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        // 12.500 reads better as 12.5, and 12.000 as 12.
        if (text.contains(".")) text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text.equals("-0") ? "0" : text;
    }

    // ---- The preview ----

    /** The playback the screen previews through, made if there is something to show. */
    static @Nullable PlaybackImpl preview() {
        if (preview != null && !preview.isActive()) preview = null;
        if (preview == null) {
            Cutscene cutscene = build();
            if (cutscene == null) return null;
            preview = CutsceneManager.preview(cutscene, origin, originYaw, playhead);
        }
        preview.setCameraEnabled(cameraView);
        return preview;
    }

    static void dropPreview() {
        if (preview != null) preview.stop();
        preview = null;
    }

    static void setPlayhead(float time) {
        playhead = Mth.clamp(time, 0.0F, duration());
        if (preview != null) {
            preview.pause();
            preview.seek(playhead);
        }
    }

    private static boolean screenOpen(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        if (screen instanceof DropdownScreen<?> dropdown) screen = dropdown.overlayParent();
        return screen instanceof CutsceneEditorScreen;
    }

    public static void tick(Minecraft minecraft) {
        while (OPEN.consumeClick()) {
            if (minecraft.screen == null && !CutsceneManager.locksInput()) open();
        }
        if (!active) return;
        if (minecraft.level == null) {
            exit();
            return;
        }
        // The preview lasts as long as the editor's screen (or a dropdown over it) is up.
        if (!screenOpen(minecraft)) dropPreview();

        // Flying around with the screen closed: K drops a keyframe where the player is.
        boolean down = minecraft.screen == null && CutsceneManager.current() == null && minecraft.isWindowActive()
                && InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_K);
        if (down && !addKeyWasDown) {
            Key key = addKeyFromView();
            if (key != null) {
                minecraft.gui.setOverlayMessage(Component.translatableWithFallback("cutscene.coolcatlib.editor.added", "Keyframe %s added at %s ticks",
                        keys.indexOf(key) + 1, format(key.time)), false);
            }
        }
        addKeyWasDown = down;
    }

    // ---- What shows while flying around ----

    /** The reminder at the top of the screen that the editor is on, with its keys. */
    public static void extractHud(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!active || minecraft.screen != null || minecraft.options.hideGui) return;
        Font font = minecraft.font;
        // The timeline's key is named when it has one; it is unbound until the player binds it.
        Component text = OPEN.isUnbound()
                ? Component.translatableWithFallback("cutscene.coolcatlib.editor.hud", "Cutscene editor: %s keyframes  |  K: add one here", keys.size())
                : Component.translatableWithFallback("cutscene.coolcatlib.editor.hud.key", "Cutscene editor: %s keyframes  |  K: add one here  |  %s: timeline",
                keys.size(), OPEN.getTranslatedKeyMessage());
        int width = font.width(text) + 12;
        int x = (graphics.guiWidth() - width) / 2;
        graphics.fill(x, 4, x + width, 18, 0xA0101418);
        graphics.fill(x, 17, x + width, 18, PATH_COLOR);
        graphics.text(font, text, x + 6, 7, 0xFFE8EEF4, false);
    }

    // The path in the world: a dot per tick or so (so their spacing shows the camera's speed), a marker with the way it
    // looks at every keyframe, and where the playhead is. Left out while the screen looks through the camera itself.
    private static void renderPath(VfxFrame frame) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || keys.isEmpty() || CutsceneManager.current() != null) return;
        if (preview != null && cameraView) return;
        Cutscene cutscene = build();
        if (cutscene == null) return;

        VfxDraw dots = frame.draw(DOT);
        float first = keys.getFirst().time;
        float last = lastKeyTime();
        float step = Math.max(1.0F, (last - first) / 600.0F);
        for (float time = first; time < last; time += step) {
            Vec3 position = cutscene.poseAt(time, 70.0F).transformed(origin, originYaw).position();
            dots.billboard(position, 0.07F, PATH_COLOR);
        }

        VfxDraw lines = frame.draw(LINE);
        for (Key key : keys) {
            CameraPose pose = worldPose(key);
            int color = key == selected ? SELECTED_COLOR : KEY_COLOR;
            dots.billboard(pose.position(), key == selected ? 0.3F : 0.2F, color);
            lines.beam(pose.position(), pose.position().add(pose.forward().scale(1.5)), 0.12F, color, color & 0xFFFFFF);
        }

        if (preview != null) {
            CameraPose pose = cutscene.poseAt(playhead, 70.0F).transformed(origin, originYaw);
            frame.draw(RING).billboard(pose.position(), 0.35F, SELECTED_COLOR);
            lines.beam(pose.position(), pose.position().add(pose.forward().scale(2.5)), 0.1F, SELECTED_COLOR, SELECTED_COLOR & 0xFFFFFF);
        }
    }
}
