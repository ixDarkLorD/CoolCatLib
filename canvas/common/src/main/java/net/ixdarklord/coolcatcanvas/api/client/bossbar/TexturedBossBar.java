package net.ixdarklord.coolcatcanvas.api.client.bossbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * A boss bar drawn from one texture: a frame (with whatever dresses it: an emblem, thorns, a blade), and inside its
 * track a fill for the health and a trail for what was just lost. Optional: marks that light once the bar falls past
 * them (phases), a glow that pulses over a point (an eye, a gem; faster as the bar empties), a jolt and a flash on
 * each hit, and the name in a colour of its own above it.
 * <pre>{@code
 * TexturedBossBar.builder(MyMod.id("textures/gui/boss/warden.png"), 240, 48)
 *         .frame(0, 0, 240, 30)           // the frame's place in the texture, and its size
 *         .track(35, 12, 180, 7)          // the inside of its track, within the frame
 *         .fill(0, 32).trail(0, 40)       // where the fill and the trail are in the texture (track-sized)
 *         .mark(0.5F, 200, 32, 2, 13, -3) // at half, a lit mark (2 x 13 from 200, 32), 3 px above the track
 *         .pulse(184, 32, 12, 12, 7)      // a 12 x 12 glow, centred on (7, 14) of the frame
 *         .at(7, 14)
 *         .nameColor(0xFFE8A0A8)
 *         .build();
 * }</pre>
 */
public final class TexturedBossBar implements BossBarRenderer {
    private record Mark(float at, int u, int v, int width, int height, int offsetY) {
    }

    private final Identifier texture;
    private final int textureWidth, textureHeight;
    private final int frameU, frameV, frameWidth, frameHeight;
    private final int trackX, trackY, trackWidth, trackHeight;
    private final int fillU, fillV, trailU, trailV;
    private final boolean hasTrail;
    private final List<Mark> marks;
    private final int pulseU, pulseV, pulseWidth, pulseHeight, pulseX, pulseY;
    private final boolean hasPulse;
    private final int nameColor;
    private final boolean jolt, flash;

    private TexturedBossBar(Builder b) {
        this.texture = b.texture;
        this.textureWidth = b.textureWidth;
        this.textureHeight = b.textureHeight;
        this.frameU = b.frameU;
        this.frameV = b.frameV;
        this.frameWidth = b.frameWidth;
        this.frameHeight = b.frameHeight;
        this.trackX = b.trackX;
        this.trackY = b.trackY;
        this.trackWidth = b.trackWidth;
        this.trackHeight = b.trackHeight;
        this.fillU = b.fillU;
        this.fillV = b.fillV;
        this.trailU = b.trailU;
        this.trailV = b.trailV;
        this.hasTrail = b.hasTrail;
        this.marks = List.copyOf(b.marks);
        this.pulseU = b.pulseU;
        this.pulseV = b.pulseV;
        this.pulseWidth = b.pulseWidth;
        this.pulseHeight = b.pulseHeight;
        this.pulseX = b.pulseX;
        this.pulseY = b.pulseY;
        this.hasPulse = b.hasPulse;
        this.nameColor = b.nameColor;
        this.jolt = b.jolt;
        this.flash = b.flash;
    }

    /** A bar from a texture of this size. */
    public static Builder builder(Identifier texture, int textureWidth, int textureHeight) {
        return new Builder(texture, textureWidth, textureHeight);
    }

    @Override
    public int render(GuiGraphicsExtractor graphics, BossBarView bar, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        int x = graphics.guiWidth() / 2 - this.frameWidth / 2;
        if (this.jolt && bar.hurt() > 0.0F) x += Math.round(Mth.sin(bar.time() * 3.0F) * 2.0F * bar.hurt());

        Component name = bar.name();
        graphics.text(minecraft.font, name, graphics.guiWidth() / 2 - minecraft.font.width(name) / 2, y, this.nameColor);
        int top = y + minecraft.font.lineHeight + 1;

        graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, x, top, this.frameU, this.frameV, this.frameWidth, this.frameHeight,
                this.textureWidth, this.textureHeight);
        int left = x + this.trackX;
        int trackTop = top + this.trackY;
        int filled = Math.round(this.trackWidth * Mth.clamp(bar.progress(), 0.0F, 1.0F));
        int trailed = Math.round(this.trackWidth * Mth.clamp(bar.trail(), 0.0F, 1.0F));
        if (this.hasTrail && trailed > filled) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, left + filled, trackTop, this.trailU + filled, this.trailV, trailed - filled,
                    this.trackHeight, this.textureWidth, this.textureHeight);
        }
        if (filled > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, left, trackTop, this.fillU, this.fillV, filled, this.trackHeight,
                    this.textureWidth, this.textureHeight);
            if (this.flash && bar.hurt() > 0.5F) {
                graphics.fill(left, trackTop, left + filled, trackTop + this.trackHeight, ARGB.white((bar.hurt() - 0.5F) * 0.8F));
            }
        }
        for (Mark mark : this.marks) {
            if (bar.progress() > mark.at) continue;
            int markX = left + Math.round(this.trackWidth * mark.at) - mark.width / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, markX, trackTop + mark.offsetY, mark.u, mark.v, mark.width, mark.height,
                    this.textureWidth, this.textureHeight);
        }
        if (this.hasPulse) {
            float rate = 0.12F + (1.0F - bar.progress()) * 0.25F;
            float beat = (float) Math.pow(Math.max(0.0, Math.sin(bar.time() * rate)), 4.0);
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, x + this.pulseX - this.pulseWidth / 2, top + this.pulseY - this.pulseHeight / 2,
                    this.pulseU, this.pulseV, this.pulseWidth, this.pulseHeight, this.textureWidth, this.textureHeight,
                    ARGB.white(0.35F + 0.65F * beat));
        }
        return minecraft.font.lineHeight + 1 + this.frameHeight + 4;
    }

    public static final class Builder {
        private final Identifier texture;
        private final int textureWidth, textureHeight;
        private int frameU, frameV, frameWidth = 182, frameHeight = 5;
        private int trackX, trackY, trackWidth = 182, trackHeight = 5;
        private int fillU, fillV, trailU, trailV;
        private boolean hasTrail;
        private final List<Mark> marks = new ArrayList<>();
        private int pulseU, pulseV, pulseWidth, pulseHeight, pulseX, pulseY;
        private boolean hasPulse;
        private int nameColor = 0xFFFFFFFF;
        private boolean jolt = true, flash = true;

        private Builder(Identifier texture, int textureWidth, int textureHeight) {
            this.texture = texture;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        /** The frame: its place in the texture and its size. It is drawn centred across the screen. */
        public Builder frame(int u, int v, int width, int height) {
            this.frameU = u;
            this.frameV = v;
            this.frameWidth = width;
            this.frameHeight = height;
            return this;
        }

        /** The inside of the frame's track, where the fill goes: its place within the frame and its size. */
        public Builder track(int x, int y, int width, int height) {
            this.trackX = x;
            this.trackY = y;
            this.trackWidth = width;
            this.trackHeight = height;
            return this;
        }

        /** Where in the texture the fill is, as big as the track; it is drawn as far as the bar is full. */
        public Builder fill(int u, int v) {
            this.fillU = u;
            this.fillV = v;
            return this;
        }

        /** Where in the texture the trail is, as big as the track: what was just lost, draining after a hit. */
        public Builder trail(int u, int v) {
            this.trailU = u;
            this.trailV = v;
            this.hasTrail = true;
            return this;
        }

        /**
         * A mark drawn over the track at a share of it (0 to 1), once the bar has fallen to it: a phase reached.
         *
         * @param offsetY how far below the track's top edge it is drawn (negative above it)
         */
        public Builder mark(float at, int u, int v, int width, int height, int offsetY) {
            this.marks.add(new Mark(at, u, v, width, height, offsetY));
            return this;
        }

        /** A glow in the texture that pulses over the frame, like a heartbeat, faster as the bar empties. */
        public Builder pulse(int u, int v, int width, int height) {
            this.pulseU = u;
            this.pulseV = v;
            this.pulseWidth = width;
            this.pulseHeight = height;
            this.hasPulse = true;
            return this;
        }

        /** Where the pulse is centred, within the frame. */
        public Builder at(int x, int y) {
            this.pulseX = x;
            this.pulseY = y;
            return this;
        }

        /** The colour of the name above it (ARGB). */
        public Builder nameColor(int argb) {
            this.nameColor = argb;
            return this;
        }

        /** Whether a hit jolts the bar sideways, and flashes its fill. Both on by default. */
        public Builder onHit(boolean jolt, boolean flash) {
            this.jolt = jolt;
            this.flash = flash;
            return this;
        }

        public TexturedBossBar build() {
            return new TexturedBossBar(this);
        }
    }
}
