package net.ixdarklord.coolcatcanvas.internal.client.cutscene;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.ixdarklord.coolcatcanvas.api.client.cutscene.CutscenePlayback;
import net.ixdarklord.coolcatcanvas.api.cutscene.CameraPose;
import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.api.cutscene.CutsceneEndReason;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.CutsceneEvents;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.editor.CutsceneEditor;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.cutscene.CutsceneEndedPayload;
import net.ixdarklord.coolcatcanvas.internal.cutscene.CutscenePayload;
import net.ixdarklord.coolcatcore.api.client.registry.KeyMappingRegistry;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Cutscenes on the client: the ones known by id (from code, from resources, saved by the editor), and the one
// playing. While one plays, CameraMixin asks here where the camera goes each frame, GuiMixin has CutsceneOverlay draw
// its bars and subtitles, and the player is held still by releasing every key mapping before each tick (their keys
// then read as up to everything that asks) and by MouseHandlerMixin dropping mouse turns.
public final class CutsceneManager {
    /** How long the skip key is held to skip, in ticks. */
    static final int SKIP_TICKS = 20;
    private static final FileToIdConverter RESOURCES = FileToIdConverter.json("cutscene");
    private static final SkipKey SKIP = new SkipKey();

    private static final Map<Identifier, Cutscene> REGISTERED = new LinkedHashMap<>();
    private static Map<Identifier, Cutscene> fromResources = Map.of();
    private static @Nullable PlaybackImpl current;
    // This frame's camera: how much of the view is the cutscene's, and its field of view.
    private static float cameraWeight;
    private static float cameraFov;

    private CutsceneManager() {}

    public static void init() {
        KeyMappingRegistry.register(SKIP);
        ClientTickEvents.START.register(CutsceneManager::startTick);
        ClientTickEvents.END.register(CutsceneManager::endTick);
        ClientPlayerEvents.LEAVE.register(player -> {
            if (current != null) end(current, CutsceneEndReason.LEFT_WORLD);
            LetterboxManager.reset();
            CutsceneOverlay.reset();
        });
        CutsceneEditor.init();
        if (!Platform.isDevelopmentEnvironment()) return;
        // Merges into CoolCatLib's client command.
        ClientCommandEvents.REGISTER.register((dispatcher, context) -> dispatcher.register(
                LiteralArgumentBuilder.<SharedSuggestionProvider>literal("coolcatlib_client")
                        .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("cutscene")
                                .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("play")
                                        .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("id", StringArgumentType.greedyString())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids().stream().map(Identifier::toString), builder))
                                                .executes(ctx -> {
                                                    Identifier id = Identifier.tryParse(StringArgumentType.getString(ctx, "id"));
                                                    if (id == null || play(id, null, 0.0F, false) == null) {
                                                        ClientCommandEvents.sendError(Component.literal("No cutscene with that id"));
                                                        return 0;
                                                    }
                                                    return 1;
                                                })))
                                .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("stop").executes(ctx -> {
                                    if (current != null) current.stop();
                                    return 1;
                                }))
                                .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("editor").executes(ctx -> {
                                    CutsceneEditor.openLater();
                                    return 1;
                                })))));
    }

    // ---- The cutscenes known by id ----

    public static void register(Identifier id, Cutscene cutscene) {
        REGISTERED.put(id, cutscene);
    }

    public static void unregister(Identifier id) {
        REGISTERED.remove(id);
    }

    public static @Nullable Cutscene get(Identifier id) {
        Cutscene cutscene = REGISTERED.get(id);
        if (cutscene == null) cutscene = fromResources.get(id);
        return cutscene != null ? cutscene : CutsceneFiles.get(id);
    }

    public static Set<Identifier> ids() {
        Set<Identifier> ids = new LinkedHashSet<>(REGISTERED.keySet());
        ids.addAll(fromResources.keySet());
        ids.addAll(CutsceneFiles.ids());
        return ids;
    }

    /** Reads assets/<namespace>/cutscene/<path>.json from every pack; a file that fails is logged and skipped. */
    public static void reload(ResourceManager manager) {
        Map<Identifier, Cutscene> loaded = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : RESOURCES.listMatchingResources(manager).entrySet()) {
            Identifier id = RESOURCES.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                loaded.put(id, CutsceneFiles.parse(JsonParser.parseReader(reader)));
            } catch (Exception e) {
                CoolCatCanvas.LOGGER.error("Couldn't load the cutscene {} from {}: {}", id, entry.getKey(), e.getMessage());
            }
        }
        fromResources = loaded;
    }

    // ---- Playing ----

    public static @Nullable CutscenePlayback current() {
        return current != null && !current.preview ? current : null;
    }

    /** The playback on screen, a preview of the editor's included. */
    public static @Nullable PlaybackImpl currentImpl() {
        return current;
    }

    public static @Nullable CutscenePlayback play(Identifier id, @Nullable Vec3 origin, float yaw, boolean fromServer) {
        Cutscene cutscene = get(id);
        if (cutscene == null) {
            CoolCatCanvas.LOGGER.debug("Asked to play unknown cutscene {}", id);
            return null;
        }
        return play(id, cutscene, origin, yaw, fromServer);
    }

    public static @Nullable CutscenePlayback play(@Nullable Identifier id, Cutscene cutscene, @Nullable Vec3 origin, float yaw, boolean fromServer) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        try {
            if (CutsceneEvents.BEFORE_START.invoker().onBeforeStart(id, cutscene, fromServer).isInterrupt()) return null;
        } catch (RuntimeException e) {
            CoolCatCanvas.LOGGER.error("A listener of a cutscene about to start failed", e);
        }
        if (current != null) end(current, CutsceneEndReason.REPLACED);
        if (origin == null) {
            origin = switch (cutscene.anchor()) {
                case WORLD -> Vec3.ZERO;
                case PLAYER, PLAYER_FACING -> player.position();
            };
            yaw = switch (cutscene.anchor()) {
                case WORLD, PLAYER -> 0.0F;
                case PLAYER_FACING -> player.getYRot();
            };
        }
        // Whatever the player had open would sit over the scene, and keep the mouse.
        if (minecraft.gui.screen() != null) {
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            else minecraft.gui.setScreen(null);
        }
        PlaybackImpl playback = new PlaybackImpl(cutscene, id, origin, yaw, fromServer, false);
        current = playback;
        CutsceneOverlay.reset();
        try {
            CutsceneEvents.STARTED.invoker().onStarted(playback);
        } catch (RuntimeException e) {
            CoolCatCanvas.LOGGER.error("A listener of a cutscene starting failed", e);
        }
        return playback;
    }

    /** The editor's preview: a playback that starts held on a frame, fires no start or end, and locks nothing. */
    public static PlaybackImpl preview(Cutscene cutscene, Vec3 origin, float yaw, float time) {
        if (current != null) end(current, CutsceneEndReason.REPLACED);
        PlaybackImpl playback = new PlaybackImpl(cutscene, null, origin, yaw, false, true);
        playback.pause();
        playback.seek(time);
        current = playback;
        CutsceneOverlay.reset();
        return playback;
    }

    static void end(PlaybackImpl playback, CutsceneEndReason reason) {
        if (!playback.isActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (current == playback) {
            current = null;
            cameraWeight = 0.0F;
            if (reason != CutsceneEndReason.REPLACED && reason != CutsceneEndReason.LEFT_WORLD) CutsceneOverlay.leave(playback);
            // Keys held through the cutscene count as down again.
            if (playback.locksInput() && minecraft.gui.screen() == null) KeyMapping.setAll();
        }
        playback.finish(reason);
        if (playback.preview) return;
        try {
            CutsceneEvents.ENDED.invoker().onEnded(playback, reason);
        } catch (RuntimeException e) {
            CoolCatCanvas.LOGGER.error("A listener of a cutscene ending failed", e);
        }
        if (playback.fromServer && playback.id() != null && reason != CutsceneEndReason.LEFT_WORLD && minecraft.getConnection() != null) {
            Network.sendToServer(new CutsceneEndedPayload(playback.id(), reason));
        }
    }

    /** The server asking for a cutscene, or for the one playing to stop. */
    public static void handle(CutscenePayload payload) {
        if (payload.id().isEmpty()) {
            if (current != null && !current.preview) current.stop();
            return;
        }
        Identifier id = payload.id().get();
        Vec3 origin = payload.origin().orElse(null);
        CutscenePlayback playback = payload.cutscene().isPresent()
                ? play(id, payload.cutscene().get(), origin, payload.yaw(), true)
                : play(id, origin, payload.yaw(), true);
        // The server is waiting to hear how it went: a cutscene this client doesn't have never will.
        if (playback == null && Minecraft.getInstance().getConnection() != null) Network.sendToServer(new CutsceneEndedPayload(id, CutsceneEndReason.STOPPED));
    }

    private static void startTick(Minecraft minecraft) {
        if (current != null && current.locksInput() && minecraft.gui.screen() == null) KeyMapping.releaseAll();
    }

    private static void endTick(Minecraft minecraft) {
        LetterboxManager.tick();
        CutsceneOverlay.tick();
        CutsceneEditor.tick(minecraft);
        PlaybackImpl playback = current;
        if (playback == null) return;
        if (minecraft.level == null) {
            end(playback, CutsceneEndReason.LEFT_WORLD);
            return;
        }
        if (minecraft.isPaused()) return;
        if (!playback.preview && playback.cutscene().skippable()) {
            if (minecraft.gui.screen() == null && isSkipKeyDown(minecraft)) {
                if (++playback.skipHeld >= SKIP_TICKS) {
                    boolean allowed = true;
                    try {
                        allowed = !CutsceneEvents.BEFORE_SKIP.invoker().onBeforeSkip(playback).isInterrupt();
                    } catch (RuntimeException e) {
                        CoolCatCanvas.LOGGER.error("A listener of a cutscene about to be skipped failed", e);
                    }
                    if (allowed) {
                        end(playback, CutsceneEndReason.SKIPPED);
                        return;
                    }
                    // Refused: the key has to be let go and held again.
                    playback.skipHeld = 0;
                }
            } else {
                playback.skipHeld = Math.max(0, playback.skipHeld - 2);
            }
        }
        playback.tick();
    }

    // The key mappings are all released while a cutscene locks input, so the skip key is read from the window.
    private static boolean isSkipKeyDown(Minecraft minecraft) {
        InputConstants.Key key = SKIP.bound();
        if (key.getValue() == InputConstants.UNKNOWN.getValue() || !minecraft.isWindowActive()) return false;
        return switch (key.getType()) {
            case KEYBOARD -> InputConstants.isKeyDown(key.getValue());
            // SDL's pressed buttons, one bit each from the left button up.
            case MOUSE -> (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer) null, null) & (1 << (key.getValue() - 1))) != 0;
        };
    }

    static Component skipKeyName() {
        return SKIP.getTranslatedKeyMessage();
    }

    // ---- What the mixins ask ----

    /**
     * Where the camera goes this frame, given where the game put it; null to leave it there.
     *
     * @param yaw   the game's camera yaw
     * @param pitch the game's camera pitch
     */
    public static @Nullable CameraPose camera(float partialTick, Vec3 position, float yaw, float pitch) {
        cameraWeight = 0.0F;
        PlaybackImpl playback = current;
        if (playback == null || !playback.drivesCamera()) return null;
        float time = playback.renderTime(partialTick);
        float weight = playback.blendWeight(time);
        if (weight <= 0.0F) return null;
        CameraPose pose = playback.worldPose(time, Minecraft.getInstance().options.fov().get(), partialTick);
        if (!playback.preview && CutsceneEvents.CAMERA.hasListeners()) {
            try {
                pose = CutsceneEvents.CAMERA.invoker().onCamera(playback, pose, partialTick);
            } catch (RuntimeException e) {
                CoolCatCanvas.LOGGER.error("A listener of a cutscene's camera failed", e);
            }
        }
        cameraWeight = weight;
        cameraFov = pose.fov();
        return weight >= 1.0F ? pose : new CameraPose(position, yaw, pitch, 0.0F, pose.fov()).lerp(pose, weight);
    }

    /** The field of view this frame, given the game's own. */
    public static float fov(float vanilla) {
        return cameraWeight <= 0.0F ? vanilla : Mth.lerp(cameraWeight, vanilla, cameraFov);
    }

    /** Whether a cutscene has the camera this frame: the hand, the crosshair's block outline and the like go. */
    public static boolean hasCamera() {
        return cameraWeight > 0.0F && current != null;
    }

    public static boolean locksInput() {
        return current != null && current.locksInput();
    }

    // A key mapping whose bound key can be read, on every loader.
    private static final class SkipKey extends KeyMapping {
        SkipKey() {
            super("key.coolcatlib.skip_cutscene", InputConstants.KEY_RETURN, KeyMapping.Category.MISC);
        }

        InputConstants.Key bound() {
            return this.key;
        }
    }
}
