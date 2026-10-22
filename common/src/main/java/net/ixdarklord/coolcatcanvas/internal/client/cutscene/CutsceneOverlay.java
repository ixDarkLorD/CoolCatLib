package net.ixdarklord.coolcatcanvas.internal.client.cutscene;

import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.editor.CutsceneEditor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

// What a cutscene puts on the screen besides the world: letterbox bars, the fade, subtitles and the skip prompt. Drawn
// from GuiMixin before the HUD, so with the HUD left on it sits under it. When a cutscene ends with its bars in or the
// screen covered, they ease away over a few ticks instead of vanishing.
public final class CutsceneOverlay {
    private static final int TAIL_TICKS = 10;
    private static final float SUBTITLE_FADE = 5.0F;
    private static final int LINE_HEIGHT = 10;

    private static float tailBars;
    private static float tailAspect;
    private static float tailFade;
    private static int tailColor;
    private static int tailAge = TAIL_TICKS;

    private CutsceneOverlay() {}

    static void tick() {
        if (tailAge < TAIL_TICKS) tailAge++;
    }

    static void reset() {
        tailAge = TAIL_TICKS;
    }

    /** Keeps what an ending cutscene had on screen, to ease it away. */
    static void leave(PlaybackImpl playback) {
        Cutscene cutscene = playback.cutscene();
        tailBars = cutscene.barsAt(playback.time());
        tailAspect = cutscene.letterbox();
        tailFade = cutscene.fadeAt(playback.time());
        tailColor = cutscene.fadeColor();
        tailAge = tailBars > 0.0F || tailFade > 0.0F ? 0 : TAIL_TICKS;
    }

    /**
     * Draws the overlay for a frame.
     *
     * @return whether the HUD should be left out of it
     */
    public static boolean extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        PlaybackImpl playback = CutsceneManager.currentImpl();

        float bars = 0.0F;
        float aspect = 0.0F;
        float fade = 0.0F;
        int color = 0;
        float time = 0.0F;
        if (playback != null) {
            Cutscene cutscene = playback.cutscene();
            time = playback.renderTime(partialTick);
            bars = cutscene.barsAt(time);
            aspect = cutscene.letterbox();
            fade = cutscene.fadeAt(time);
            color = cutscene.fadeColor();
        } else if (tailAge < TAIL_TICKS) {
            float left = 1.0F - Math.min(1.0F, (tailAge + partialTick) / TAIL_TICKS);
            left = left * left * (3.0F - 2.0F * left);
            bars = tailBars * left;
            aspect = tailAspect;
            fade = tailFade * left;
            color = tailColor;
        }

        // Bars: the cutscene's, or the Letterbox API's when those reach further in.
        int barHeight = barSize(height, width / aspectOr(aspect), bars);
        int barWidth = barSize(width, height * aspectOr(aspect), bars);
        float standalone = LetterboxManager.amount(partialTick);
        if (standalone > 0.0F) {
            barHeight = Math.max(barHeight, barSize(height, width / LetterboxManager.aspect(), standalone));
            barWidth = Math.max(barWidth, barSize(width, height * LetterboxManager.aspect(), standalone));
        }
        if (barHeight > 0) {
            graphics.fill(0, 0, width, barHeight, 0xFF000000);
            graphics.fill(0, height - barHeight, width, height, 0xFF000000);
        }
        if (barWidth > 0) {
            graphics.fill(0, 0, barWidth, height, 0xFF000000);
            graphics.fill(width - barWidth, 0, width, height, 0xFF000000);
        }
        if (fade > 0.0F) graphics.fill(0, 0, width, height, Math.round(fade * 255.0F) << 24 | color);

        if (playback == null) {
            CutsceneEditor.extractHud(graphics, minecraft);
            return false;
        }
        extractSubtitles(graphics, minecraft.font, playback.cutscene(), time, width, height, barHeight);
        if (playback.cutscene().skippable() && !playback.preview) extractSkipPrompt(graphics, minecraft.font, playback, partialTick, width, height);
        return playback.drivesCamera() && playback.cutscene().hideHud();
    }

    private static float aspectOr(float aspect) {
        return aspect > 0.0F ? aspect : 1.0E6F;
    }

    // The size of one of a pair of bars that leave `kept` of `full` between them, slid in by `amount`.
    private static int barSize(int full, float kept, float amount) {
        if (amount <= 0.0F || kept >= full) return 0;
        return Math.round((full - kept) / 2.0F * amount);
    }

    private static void extractSubtitles(GuiGraphicsExtractor graphics, Font font, Cutscene cutscene, float time, int width, int height, int barHeight) {
        for (Cutscene.Subtitle subtitle : cutscene.subtitles()) {
            if (time < subtitle.start() || time > subtitle.end()) continue;
            float alpha = Mth.clamp(Math.min(time - subtitle.start(), subtitle.end() - time) / SUBTITLE_FADE, 0.0F, 1.0F);
            // Text this faint isn't drawn at all by the font renderer.
            if (alpha < 0.05F) continue;
            List<FormattedCharSequence> lines = font.split(subtitle.text(), Math.min(width - 40, 360));
            int textHeight = lines.size() * LINE_HEIGHT;
            // Inside the lower bar when it has the room, otherwise just above it on a soft backdrop.
            boolean inBar = barHeight >= textHeight + 6;
            int y = inBar ? height - barHeight + (barHeight - textHeight) / 2 + 1 : height - barHeight - textHeight - 14;
            int color = Math.round(alpha * 255.0F) << 24 | 0xFFFFFF;
            if (!inBar) {
                int widest = lines.stream().mapToInt(font::width).max().orElse(0);
                graphics.fill((width - widest) / 2 - 5, y - 4, (width + widest) / 2 + 5, y + textHeight + 2, Math.round(alpha * 140.0F) << 24);
            }
            for (FormattedCharSequence line : lines) {
                graphics.text(font, line, (width - font.width(line)) / 2, y, color, true);
                y += LINE_HEIGHT;
            }
        }
    }

    // "Hold [key] to skip" in the corner: shown as the cutscene opens, and whenever the key is down, with a line
    // that fills as it is held.
    private static void extractSkipPrompt(GuiGraphicsExtractor graphics, Font font, PlaybackImpl playback, float partialTick, int width, int height) {
        float shown = playback.skipHeld > 0 ? 1.0F : Mth.clamp((70.0F - playback.age - partialTick) / 15.0F, 0.0F, 1.0F);
        if (shown < 0.05F) return;
        Component hint = Component.translatableWithFallback("cutscene.coolcatlib.skip", "Hold %s to skip", CutsceneManager.skipKeyName());
        int textWidth = font.width(hint);
        int x = width - textWidth - 10;
        int y = height - 20;
        graphics.text(font, hint, x, y, Math.round(shown * 200.0F) << 24 | 0xFFFFFF, true);
        float held = Mth.clamp((float) playback.skipHeld / CutsceneManager.SKIP_TICKS, 0.0F, 1.0F);
        graphics.fill(x, y + 10, x + textWidth, y + 11, Math.round(shown * 90.0F) << 24 | 0xFFFFFF);
        if (held > 0.0F) graphics.fill(x, y + 10, x + Math.round(textWidth * held), y + 11, 0xFFFFFFFF);
    }
}
