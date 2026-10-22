package net.ixdarklord.coolcatcanvas.internal.client.cutscene.editor;

import net.ixdarklord.coolcatcanvas.api.cutscene.CameraPose;
import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.api.cutscene.Interpolation;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.api.utils.Easings;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneFiles;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.PlaybackImpl;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.editor.CutsceneEditor.Key;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.DropdownScreen;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.FlatButton;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledScreen;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ToggleSwitch;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

// The cutscene editor's screen: the scene's settings on the left, the selected keyframe's numbers on the right, and
// the timeline along the bottom, with the world showing between them through the cutscene's camera at the playhead.
// "Play" hides the panels and lets the preview run. Closing the screen keeps the session: the player flies on, the
// path drawn around them, and comes back.
public final class CutsceneEditorScreen extends StyledScreen {
    private static final int EDGE = 6;
    private static final int TOP_HEIGHT = 24;
    private static final int TIMELINE_HEIGHT = 62;
    private static final int SCENE_WIDTH = 124;
    private static final int KEY_WIDTH = 164;
    private static final int ROW = 16;
    private static final int FIELD_HEIGHT = 14;
    private static final int PANEL_TITLE = 15;
    private static final int KEY_HIT = 4;
    private static final long STATUS_MILLIS = 4000;

    private final List<AbstractWidget> chrome = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private @Nullable Key shownFor;
    private boolean playing;
    private @Nullable Key dragging;
    private boolean scrubbing;
    private boolean settingFields;
    private boolean confirmNew;
    private @Nullable Component status;
    private long statusUntil;

    // A number box and where its number comes from, to follow changes made elsewhere (a drag on the timeline).
    private record Field(StyledEditBox box, DoubleSupplier value) {}

    private record Label(Component text, int x, int y) {}

    public CutsceneEditorScreen() {
        super(Component.translatableWithFallback("cutscene.coolcatlib.editor.title", "Cutscene Editor"), ConfigTheme.forMod(CoolCatCore.MOD_ID));
    }

    // ---- Layout ----

    private int timelineY() {
        return this.height - EDGE - TIMELINE_HEIGHT;
    }

    private int panelsY() {
        return EDGE + TOP_HEIGHT + 4;
    }

    private int keyPanelX() {
        return this.width - EDGE - KEY_WIDTH;
    }

    private int trackLeft() {
        return EDGE + 10;
    }

    private int trackWidth() {
        return this.width - EDGE * 2 - 20;
    }

    private int rulerY() {
        return this.timelineY() + 25;
    }

    private int trackY() {
        return this.rulerY() + 11;
    }

    private int trackBottom() {
        return this.timelineY() + TIMELINE_HEIGHT - 6;
    }

    /** How many ticks the timeline shows: the cutscene and a little room after it. */
    private float viewLength() {
        return Math.max(100.0F, CutsceneEditor.duration() * 1.08F + 10.0F);
    }

    private int timeToX(float time) {
        return this.trackLeft() + Math.round(time / this.viewLength() * this.trackWidth());
    }

    private float xToTime(double x) {
        return Mth.clamp((float) (x - this.trackLeft()) / this.trackWidth(), 0.0F, 1.0F) * this.viewLength();
    }

    @Override
    protected void init() {
        this.chrome.clear();
        this.fields.clear();
        this.labels.clear();
        this.shownFor = CutsceneEditor.selected;
        this.initTopBar();
        this.initScenePanel();
        this.initKeyPanel();
        this.initTransport();
        this.setPlaying(this.playing);
    }

    private <T extends AbstractWidget> T add(T widget, int x, int y) {
        widget.setPosition(x, y);
        this.chrome.add(widget);
        return this.addRenderableWidget(widget);
    }

    private void initTopBar() {
        int y = EDGE + 2;
        int titleEnd = EDGE + 14 + this.font.width(this.title.copy().withStyle(ChatFormatting.BOLD));
        StyledEditBox nameBox = this.add(new StyledEditBox(this.font, 92, 20, text("name", "Name")), titleEnd + 8, y);
        nameBox.setMaxLength(48);
        nameBox.setValue(CutsceneEditor.name);
        nameBox.setPlaceholder(text("name", "Name"));
        nameBox.setResponder(value -> {
            CutsceneEditor.name = value.toLowerCase(Locale.ROOT);
            nameBox.setInvalid(!CutsceneFiles.isValidName(CutsceneEditor.name));
        });

        int right = this.width - EDGE - 2;
        this.add(FlatButton.of(text("save", "Save"), 40, button -> this.say(CutsceneEditor.save())).style(FlatButton.Style.PRIMARY)
                .tooltip(text("save.tooltip", "Writes it to config/coolcatlib/cutscenes/ as JSON, playable as coolcatlib:saved/<name>")), right - 40, y);
        FlatButton load = FlatButton.of(text("load", "Load"), 40, button -> this.openLoadMenu((FlatButton) button));
        this.add(load, right - 84, y);
        this.add(FlatButton.of(text("new", "New"), 36, button -> {
            if (CutsceneEditor.dirty && !this.confirmNew) {
                this.confirmNew = true;
                this.say(text("new.confirm", "Unsaved changes: click New again to discard them"));
                return;
            }
            this.confirmNew = false;
            CutsceneEditor.startNew();
            this.rebuildWidgets();
        }), right - 124, y);
    }

    private void initScenePanel() {
        int x = EDGE + 6;
        int fieldX = EDGE + SCENE_WIDTH - 6 - 44;
        int y = this.panelsY() + PANEL_TITLE;
        this.number(text("letterbox", "Letterbox"), text("letterbox.tooltip", "The aspect ratio the black bars frame; 2.39 is cinema widescreen, 0 is no bars"),
                x, fieldX, y, 44, () -> CutsceneEditor.letterbox, value -> CutsceneEditor.letterbox = (float) Math.max(0.0, value));
        y += ROW;
        this.labels.add(new Label(text("hide_hud", "Hide HUD"), x, y + 3));
        this.add(new ToggleSwitch(44, FIELD_HEIGHT, () -> CutsceneEditor.hideHud, button -> {
            CutsceneEditor.hideHud = !CutsceneEditor.hideHud;
            CutsceneEditor.changed();
        }), fieldX, y);
        y += ROW;
        this.number(text("blend_in", "Blend in"), text("blend_in.tooltip", "Ticks the view glides from the player's own into the first keyframe"),
                x, fieldX, y, 44, () -> CutsceneEditor.blendIn, value -> CutsceneEditor.blendIn = (float) Math.max(0.0, value));
        y += ROW;
        this.number(text("blend_out", "Blend out"), text("blend_out.tooltip", "Ticks the view glides back to the player's own at the end"),
                x, fieldX, y, 44, () -> CutsceneEditor.blendOut, value -> CutsceneEditor.blendOut = (float) Math.max(0.0, value));
        y += ROW;
        this.number(text("fade_in", "Fade in"), text("fade_in.tooltip", "Ticks it takes to clear from black at the start"),
                x, fieldX, y, 44, () -> CutsceneEditor.fadeIn, value -> {
                    CutsceneEditor.fadeIn = (float) Math.max(0.0, value);
                    CutsceneEditor.markFadeEdited();
                });
        y += ROW;
        this.number(text("fade_out", "Fade out"), text("fade_out.tooltip", "Ticks it takes to go to black at the end"),
                x, fieldX, y, 44, () -> CutsceneEditor.fadeOut, value -> {
                    CutsceneEditor.fadeOut = (float) Math.max(0.0, value);
                    CutsceneEditor.markFadeEdited();
                });
        y += ROW;
        this.number(text("end_hold", "End hold"), text("end_hold.tooltip", "Ticks the camera stays on the last keyframe before the cutscene ends"),
                x, fieldX, y, 44, () -> CutsceneEditor.endHold, value -> CutsceneEditor.endHold = (float) Math.max(0.0, value));
    }

    private void initKeyPanel() {
        Key key = CutsceneEditor.selected;
        if (key == null) return;
        int left = this.keyPanelX() + 6;
        int column = (KEY_WIDTH - 12 - 4) / 2;
        int rightColumn = left + column + 4;
        int labelWidth = 30;
        int boxWidth = column - labelWidth;
        int y = this.panelsY() + PANEL_TITLE;

        this.number(text("time", "Time"), text("time.tooltip", "When the camera is here, in ticks (20 a second)"), left, left + labelWidth, y, boxWidth, () -> key.time, value -> key.time = (float) Math.max(0.0, value));
        this.number(text("fov", "FOV"), text("fov.tooltip", "Field of view in degrees; 0 uses the player's own setting"), rightColumn, rightColumn + labelWidth, y, boxWidth,
                () -> key.fov, value -> key.fov = (float) Mth.clamp(value, 0.0, 170.0));
        y += ROW;
        this.number(Component.literal("X"), null, left, left + labelWidth, y, boxWidth, () -> key.x, value -> key.x = value);
        this.number(text("yaw", "Yaw"), null, rightColumn, rightColumn + labelWidth, y, boxWidth, () -> key.yaw, value -> key.yaw = (float) value);
        y += ROW;
        this.number(Component.literal("Y"), null, left, left + labelWidth, y, boxWidth, () -> key.y, value -> key.y = value);
        this.number(text("pitch", "Pitch"), null, rightColumn, rightColumn + labelWidth, y, boxWidth, () -> key.pitch, value -> key.pitch = (float) Mth.clamp(value, -90.0, 90.0));
        y += ROW;
        this.number(Component.literal("Z"), null, left, left + labelWidth, y, boxWidth, () -> key.z, value -> key.z = value);
        this.number(text("roll", "Roll"), text("roll.tooltip", "Tilt around the line of sight, in degrees"), rightColumn, rightColumn + labelWidth, y, boxWidth, () -> key.roll, value -> key.roll = (float) value);
        y += ROW;

        // How the camera arrives here from the keyframe before.
        int wide = KEY_WIDTH - 12 - 44;
        this.labels.add(new Label(text("easing", "Easing"), left, y + 3));
        String easingName = Easings.nameOf(key.easing);
        FlatButton easing = new FlatButton(wide, FIELD_HEIGHT, Component.literal(easingName != null ? easingName : "custom"), button -> this.openEasingMenu((FlatButton) button, key));
        easing.tooltip(text("easing.tooltip", "How the move into this keyframe is paced"));
        this.add(easing, left + 44, y);
        y += ROW;
        this.labels.add(new Label(text("path", "Path"), left, y + 3));
        FlatButton path = new FlatButton(wide, FIELD_HEIGHT, Component.literal(key.interpolation.getSerializedName()), button -> {
            Interpolation[] values = Interpolation.values();
            key.interpolation = values[(key.interpolation.ordinal() + 1) % values.length];
            button.setMessage(Component.literal(key.interpolation.getSerializedName()));
            CutsceneEditor.changed();
        });
        path.tooltip(text("path.tooltip", "The move into this keyframe: a smooth curve, a straight line, or a cut"));
        this.add(path, left + 44, y);
        y += ROW + 2;

        int third = (KEY_WIDTH - 12 - 4) / 3;
        this.add(new FlatButton(third, 16, text("set_view", "Capture"), button -> {
            LocalPlayer player = this.minecraft.player;
            if (player == null) return;
            CutsceneEditor.setToView(key, player);
            CutsceneEditor.changed();
        }).tooltip(text("set_view.tooltip", "Moves this keyframe to where you stand and look")), left, y);
        this.add(new FlatButton(third, 16, text("go_to", "Go to"), button -> this.goTo(key))
                .tooltip(text("go_to.tooltip", "Teleports you to this keyframe (needs permission to use /tp)")), left + third + 2, y);
        this.add(new FlatButton(third, 16, text("delete", "Delete"), button -> {
            CutsceneEditor.delete(key);
            this.rebuildWidgets();
        }).style(FlatButton.Style.DANGER), left + (third + 2) * 2, y);
    }

    private void initTransport() {
        int y = this.timelineY() + 5;
        int x = EDGE + 6;
        this.add(new FlatButton(18, 16, Component.literal("|<"), button -> this.step(-1)).tooltip(text("previous", "Previous keyframe")), x, y);
        this.add(new FlatButton(34, 16, text("play", "Play"), button -> this.play()).style(FlatButton.Style.PRIMARY)
                .tooltip(text("play.tooltip", "Plays from the playhead with the panels hidden (Space)")), x + 20, y);
        this.add(new FlatButton(18, 16, Component.literal(">|"), button -> this.step(1)).tooltip(text("next", "Next keyframe")), x + 56, y);

        int right = this.width - EDGE - 6;
        this.add(new FlatButton(30, 16, text("exit", "Exit"), button -> {
            CutsceneEditor.exit();
            this.minecraft.gui.setScreen(null);
        }).style(FlatButton.Style.DANGER).tooltip(text("exit.tooltip", "Leaves the editor. Esc only hides this screen, so you can fly to the next keyframe")), right - 30, y);
        this.add(new FlatButton(70, 16, viewLabel(), button -> {
            CutsceneEditor.cameraView = !CutsceneEditor.cameraView;
            button.setMessage(viewLabel());
            PlaybackImpl preview = CutsceneEditor.preview();
            if (preview != null) preview.setCameraEnabled(CutsceneEditor.cameraView);
        }).tooltip(text("view.tooltip", "Look through the cutscene's camera at the playhead, or through your own eyes at its path")), right - 104, y);
        this.add(new FlatButton(72, 16, text("add", "+ Keyframe"), button -> {
            // On the path when the playhead is between keyframes, else a new one where the player is.
            boolean between = CutsceneEditor.playhead > 0.0F && CutsceneEditor.playhead < CutsceneEditor.lastKeyTime();
            Key added = between ? CutsceneEditor.addKeyAtPlayhead() : CutsceneEditor.addKeyFromView();
            if (added == null) this.say(text("add.taken", "There is a keyframe at the playhead already"));
            this.rebuildWidgets();
        }).tooltip(text("add.tooltip", "Between keyframes: one on the path at the playhead. Otherwise: one where you stand, after the last (K while flying)")), right - 180, y);
    }

    private static Component viewLabel() {
        return CutsceneEditor.cameraView ? text("view.camera", "View: Camera") : text("view.free", "View: Free");
    }

    private static Component text(String key, String fallback) {
        return Component.translatableWithFallback("cutscene.coolcatlib.editor." + key, fallback);
    }

    // A label and a number box after it.
    private void number(Component label, @Nullable Component tooltip, int labelX, int boxX, int y, int boxWidth, DoubleSupplier value, DoubleConsumer setter) {
        this.labels.add(new Label(label, labelX, y + 3));
        StyledEditBox box = this.add(new StyledEditBox(this.font, boxWidth, FIELD_HEIGHT, label), boxX, y);
        box.setMaxLength(12);
        box.setValue(CutsceneEditor.format(value.getAsDouble()));
        if (tooltip != null) box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
        box.setResponder(typed -> {
            if (this.settingFields) return;
            try {
                double number = Double.parseDouble(typed.trim());
                if (!Double.isFinite(number)) throw new NumberFormatException();
                setter.accept(number);
                box.setInvalid(false);
                CutsceneEditor.changed();
                if (CutsceneEditor.selected != null) CutsceneEditor.setPlayhead(CutsceneEditor.playhead);
            } catch (NumberFormatException e) {
                box.setInvalid(true);
            }
        });
        this.fields.add(new Field(box, value));
    }

    // Boxes not being typed in follow their numbers, which the timeline and the buttons change too.
    private void refreshFields() {
        this.settingFields = true;
        for (Field field : this.fields) {
            if (field.box.isFocused()) continue;
            String shown = CutsceneEditor.format(field.value.getAsDouble());
            if (!shown.equals(field.box.getValue())) {
                field.box.setValue(shown);
                field.box.setInvalid(false);
            }
        }
        this.settingFields = false;
    }

    private void say(Component message) {
        this.status = message;
        this.statusUntil = System.currentTimeMillis() + STATUS_MILLIS;
    }

    // ---- Menus ----

    private void openEasingMenu(FlatButton anchor, Key key) {
        String current = Easings.nameOf(key.easing);
        this.minecraft.gui.setScreen(new DropdownScreen<>(this, anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight(), Easings.names(),
                current != null ? current : "", Component::literal, name -> null, name -> {
            Easing easing = Easings.byName(name);
            if (easing != null) {
                key.easing = easing;
                CutsceneEditor.changed();
            }
        }));
    }

    // The editor's own saves by name, then every other cutscene by id.
    private void openLoadMenu(FlatButton anchor) {
        List<String> choices = new ArrayList<>(CutsceneFiles.names());
        int saved = choices.size();
        for (Identifier id : CutsceneManager.ids()) {
            // A mod's cutscene marked as not editable isn't offered.
            Cutscene known = CutsceneManager.get(id);
            if (known == null || !known.editable()) continue;
            if (CutsceneFiles.names().stream().noneMatch(name -> CutsceneFiles.id(name).equals(id))) choices.add(id.toString());
        }
        if (choices.isEmpty()) {
            this.say(text("load.empty", "Nothing to load yet"));
            return;
        }
        this.minecraft.gui.setScreen(new DropdownScreen<>(this, anchor.getX(), anchor.getY(), 150, anchor.getHeight(), choices, "", Component::literal, choice -> null, choice -> {
            boolean ownSave = choices.indexOf(choice) < saved;
            Cutscene cutscene = ownSave ? CutsceneFiles.get(choice) : null;
            String name = choice;
            if (cutscene == null) {
                Identifier id = Identifier.tryParse(choice);
                cutscene = id != null ? CutsceneManager.get(id) : null;
                // Saved under its own last word, so loading a mod's cutscene never overwrites another save unasked.
                if (id != null) name = id.getPath().substring(id.getPath().lastIndexOf('/') + 1).replaceAll("[^a-z0-9_-]", "_");
            }
            if (cutscene != null) CutsceneEditor.load(name, cutscene);
        }));
    }

    // ---- Preview and transport ----

    @Override
    public void added() {
        super.added();
        CutsceneEditor.preview();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        PlaybackImpl preview = CutsceneEditor.preview();
        if (this.playing) {
            // It pauses itself on its last frame.
            if (preview == null || preview.isPaused()) this.stopPlaying();
            else CutsceneEditor.playhead = preview.time();
        }
        if (CutsceneEditor.selected != this.shownFor) this.rebuildWidgets();
    }

    private void play() {
        PlaybackImpl preview = CutsceneEditor.preview();
        if (preview == null) {
            this.say(text("save.empty", "Add a keyframe first"));
            return;
        }
        preview.seek(CutsceneEditor.playhead >= CutsceneEditor.duration() - 0.01F ? 0.0F : CutsceneEditor.playhead);
        preview.setCameraEnabled(true);
        preview.resume();
        this.setPlaying(true);
    }

    private void stopPlaying() {
        PlaybackImpl preview = CutsceneEditor.preview();
        if (preview != null) {
            preview.pause();
            CutsceneEditor.playhead = preview.time();
            preview.seek(CutsceneEditor.playhead);
            preview.setCameraEnabled(CutsceneEditor.cameraView);
        }
        this.setPlaying(false);
    }

    private void setPlaying(boolean playing) {
        this.playing = playing;
        for (AbstractWidget widget : this.chrome) widget.visible = !playing;
        if (playing) this.setFocused(null);
    }

    /** Moves the playhead to the keyframe before or after it, and selects that one. */
    private void step(int direction) {
        Key target = null;
        for (Key key : CutsceneEditor.keys) {
            if (direction > 0 && key.time > CutsceneEditor.playhead + 0.01F) {
                target = key;
                break;
            }
            if (direction < 0 && key.time < CutsceneEditor.playhead - 0.01F) target = key;
        }
        if (target == null) return;
        CutsceneEditor.selected = target;
        CutsceneEditor.setPlayhead(target.time);
    }

    private void goTo(Key key) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return;
        CameraPose pose = CutsceneEditor.worldPose(key);
        player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", pose.position().x, pose.position().y - player.getEyeHeight(),
                pose.position().z, Mth.wrapDegrees(pose.yaw()), pose.pitch()));
    }

    // ---- Drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        // No blur or dimming: the world is the preview.
        if (this.playing) {
            Component hint = text("playing", "Playing: click or press any key to stop");
            int width = this.font.width(hint) + 12;
            ConfigStyle.rect(graphics, (this.width - width) / 2, 6, width, 16, ConfigStyle.withAlpha(ConfigStyle.colors().bar(), 0xA0));
            ConfigStyle.centeredText(graphics, this.font, hint, this.width / 2, 10, width, ConfigStyle.colors().textDim());
            return;
        }
        this.extractPanels(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.refreshFields();
        int frameWidth = this.width - EDGE * 2;

        // Top bar: the title, then (after the name box) the last thing the editor had to say.
        ConfigStyle.bar(graphics, EDGE, EDGE, frameWidth, TOP_HEIGHT);
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        ConfigStyle.rect(graphics, EDGE + 6, EDGE + 7, 2, 10, ConfigStyle.accent());
        graphics.text(this.font, title, EDGE + 12, EDGE + 8, ConfigStyle.colors().text(), false);
        int statusX = EDGE + 14 + this.font.width(title) + 8 + 92 + 8;
        int statusWidth = this.width - EDGE - 2 - 124 - 6 - statusX;
        if (statusWidth > 30) {
            boolean fresh = this.status != null && System.currentTimeMillis() < this.statusUntil;
            Component shown = fresh ? this.status : CutsceneEditor.dirty ? text("unsaved", "Unsaved changes") : Component.empty();
            ConfigStyle.text(graphics, this.font, shown, statusX, EDGE + 8, statusWidth, fresh ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
        }

        // Side panels.
        int panelsY = this.panelsY();
        ConfigStyle.panel(graphics, EDGE, panelsY, SCENE_WIDTH, PANEL_TITLE + ROW * 7 + 3);
        graphics.text(this.font, text("scene", "Scene").copy().withStyle(ChatFormatting.BOLD), EDGE + 6, panelsY + 4, ConfigStyle.colors().text(), false);
        Key selected = CutsceneEditor.selected;
        if (selected != null) {
            ConfigStyle.panel(graphics, this.keyPanelX(), panelsY, KEY_WIDTH, PANEL_TITLE + ROW * 6 + 2 + 16 + 5);
            Component heading = Component.translatableWithFallback("cutscene.coolcatlib.editor.keyframe", "Keyframe %s of %s", CutsceneEditor.keys.indexOf(selected) + 1, CutsceneEditor.keys.size());
            graphics.text(this.font, heading.copy().withStyle(ChatFormatting.BOLD), this.keyPanelX() + 6, panelsY + 4, ConfigStyle.colors().text(), false);
        } else {
            Component hint = CutsceneEditor.keys.isEmpty()
                    ? text("empty", "Stand where the shot starts, look, and press + Keyframe")
                    : text("none_selected", "Click a keyframe on the timeline to edit it");
            int hintWidth = Math.min(this.font.width(hint) + 12, this.width - EDGE * 2 - SCENE_WIDTH - 8);
            int hintX = this.width - EDGE - hintWidth;
            ConfigStyle.rect(graphics, hintX, panelsY, hintWidth, 16, ConfigStyle.withAlpha(ConfigStyle.colors().bar(), 0xC0));
            ConfigStyle.text(graphics, this.font, hint, hintX + 6, panelsY + 4, hintWidth - 12, ConfigStyle.colors().textDim());
        }
        for (Label label : this.labels) graphics.text(this.font, label.text, label.x, label.y, ConfigStyle.colors().textDim(), false);

        this.extractTimeline(graphics, mouseX, mouseY);
    }

    private void extractTimeline(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = this.timelineY();
        ConfigStyle.panel(graphics, EDGE, y, this.width - EDGE * 2, TIMELINE_HEIGHT);

        // Where the playhead is, in ticks and seconds, between the transport and the buttons on the right.
        float duration = CutsceneEditor.duration();
        Component readout = Component.literal(String.format(Locale.ROOT, "%s / %s ticks   %.2fs / %.2fs", CutsceneEditor.format(Math.round(CutsceneEditor.playhead * 10.0F) / 10.0),
                CutsceneEditor.format(duration), CutsceneEditor.playhead / 20.0F, duration / 20.0F));
        int readoutX = EDGE + 6 + 80;
        ConfigStyle.text(graphics, this.font, readout, readoutX, y + 9, this.width - EDGE - 6 - 184 - readoutX, ConfigStyle.colors().textDim());

        int left = this.trackLeft();
        int width = this.trackWidth();
        int rulerY = this.rulerY();
        int trackY = this.trackY();
        int trackBottom = this.trackBottom();
        ConfigStyle.rect(graphics, left - 4, trackY, width + 8, trackBottom - trackY, ConfigStyle.colors().field());
        // Past the cutscene's end.
        int endX = this.timeToX(duration);
        if (endX < left + width + 4) graphics.fill(endX, trackY + 1, left + width + 3, trackBottom - 1, ConfigStyle.withAlpha(0xFF000000, 0x50));

        // Ruler: a mark every so many ticks, far enough apart for their numbers.
        float perPixel = this.viewLength() / width;
        int step = 5;
        for (int candidate : new int[]{5, 10, 20, 40, 100, 200, 400, 1000, 2000, 4000}) {
            step = candidate;
            if (candidate / perPixel >= 36.0F) break;
        }
        for (int tick = 0; tick <= this.viewLength(); tick += step) {
            int x = this.timeToX(tick);
            graphics.fill(x, rulerY + 7, x + 1, trackBottom - 1, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x28));
            graphics.text(this.font, Integer.toString(tick), x + 2, rulerY, ConfigStyle.colors().textMuted(), false);
        }

        // The moves between keyframes, then the keyframes.
        int middle = (trackY + trackBottom) / 2;
        List<Key> keys = CutsceneEditor.keys;
        for (int i = 1; i < keys.size(); i++) {
            Key key = keys.get(i);
            if (key.interpolation == Interpolation.CUT) continue;
            int color = ConfigStyle.withAlpha(key.interpolation == Interpolation.SMOOTH ? ConfigStyle.accent() : ConfigStyle.colors().text(), 0x90);
            graphics.fill(this.timeToX(keys.get(i - 1).time), middle, this.timeToX(key.time), middle + 1, color);
        }
        boolean overTrack = mouseY >= trackY && mouseY < trackBottom;
        for (Key key : keys) {
            int x = this.timeToX(key.time);
            boolean hovered = overTrack && Math.abs(mouseX - x) <= KEY_HIT && this.dragging == null;
            boolean selected = key == CutsceneEditor.selected;
            diamond(graphics, x, middle, selected ? 5 : 4, selected ? ConfigStyle.accent() : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            if (selected) diamond(graphics, x, middle, 2, ConfigStyle.readableOn(ConfigStyle.accent()));
            if (hovered) graphics.setTooltipForNextFrame(this.font, Component.literal(CutsceneEditor.format(key.time) + " ticks"), mouseX, mouseY);
        }

        int playX = this.timeToX(CutsceneEditor.playhead);
        graphics.fill(playX, rulerY + 5, playX + 1, trackBottom, ConfigStyle.colors().text());
        graphics.fill(playX - 2, rulerY + 3, playX + 3, rulerY + 5, ConfigStyle.colors().text());
        graphics.fill(playX - 1, rulerY + 5, playX + 2, rulerY + 6, ConfigStyle.colors().text());
    }

    // A filled diamond: rows that widen to the middle and narrow again.
    private static void diamond(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = radius - Math.abs(row);
            graphics.fill(centerX - half, centerY + row, centerX + half + 1, centerY + row + 1, color);
        }
    }

    // ---- Input ----

    private @Nullable Key keyAt(double mouseX, double mouseY) {
        if (mouseY < this.trackY() || mouseY >= this.trackBottom()) return null;
        Key nearest = null;
        double best = KEY_HIT + 0.5;
        for (Key key : CutsceneEditor.keys) {
            double distance = Math.abs(mouseX - this.timeToX(key.time));
            if (distance <= best) {
                best = distance;
                nearest = key;
            }
        }
        return nearest;
    }

    private boolean overTimeline(double mouseX, double mouseY) {
        return mouseY >= this.rulerY() && mouseY < this.trackBottom() && mouseX >= EDGE && mouseX < this.width - EDGE;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.playing) {
            this.stopPlaying();
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        // A click on nothing takes the keyboard back from whatever box had it.
        this.setFocused(null);
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !this.overTimeline(event.x(), event.y())) return false;
        Key key = this.keyAt(event.x(), event.y());
        if (key != null) {
            CutsceneEditor.selected = key;
            CutsceneEditor.setPlayhead(key.time);
            this.dragging = key;
        } else {
            this.scrubbing = true;
            CutsceneEditor.setPlayhead(Math.round(this.xToTime(event.x())));
            if (doubleClick && CutsceneEditor.addKeyAtPlayhead() != null) this.scrubbing = false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging != null) {
            float time = Math.round(this.xToTime(event.x()));
            Key key = this.dragging;
            // Keyframes don't pass through each other: one stops a tick short of its neighbours.
            int index = CutsceneEditor.keys.indexOf(key);
            if (index > 0) time = Math.max(time, CutsceneEditor.keys.get(index - 1).time + 1.0F);
            if (index >= 0 && index < CutsceneEditor.keys.size() - 1) time = Math.min(time, CutsceneEditor.keys.get(index + 1).time - 1.0F);
            if (time != key.time && time >= 0.0F) {
                key.time = time;
                CutsceneEditor.changed();
                CutsceneEditor.setPlayhead(key.time);
            }
            return true;
        }
        if (this.scrubbing) {
            CutsceneEditor.setPlayhead(event.hasShiftDown() ? this.xToTime(event.x()) : Math.round(this.xToTime(event.x())));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging != null || this.scrubbing) {
            this.dragging = null;
            this.scrubbing = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.playing) {
            this.stopPlaying();
            return true;
        }
        boolean typing = this.getFocused() instanceof EditBox box && box.isFocused();
        if (!typing) {
            if (event.key() == InputConstants.KEY_SPACE) {
                this.play();
                return true;
            }
            if (event.key() == InputConstants.KEY_DELETE && CutsceneEditor.selected != null) {
                CutsceneEditor.delete(CutsceneEditor.selected);
                this.rebuildWidgets();
                return true;
            }
            if (event.isLeft() || event.isRight()) {
                this.step(event.isRight() ? 1 : -1);
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
