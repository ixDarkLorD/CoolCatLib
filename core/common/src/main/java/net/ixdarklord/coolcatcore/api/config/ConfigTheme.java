package net.ixdarklord.coolcatcore.api.config;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a mod's config screens look: its {@link ConfigColorScheme color schemes} for dark and light mode, an optional
 * background texture, and the icon beside the title (the mod's own icon unless set). Players pick dark or light mode
 * for every mod with the sun/moon button in the top bar.
 * Set it for all of a mod's screens (its configs, config list and pickers) with {@link #setForMod}, or for one config
 * with {@link ConfigBuilder#theme}:
 * <pre>{@code
 * ConfigTheme.setForMod("mymod", ConfigTheme.builder()
 *         .colors(ConfigColorScheme.tinted(0xFFFF8A3D))
 *         .background(Identifier.fromNamespaceAndPath("mymod", "textures/gui/config_background.png"))
 *         .mode(ConfigTheme.BackgroundMode.COVER)
 *         .textureOpacity(0.8F)       // lets the panorama or world show through the texture a little
 *         .backgroundOpacity(0.3F)    // how strongly the backdrop color covers it
 *         .build());
 * }</pre>
 * The texture is a full path in a resource pack ({@code assets/mymod/textures/gui/config_background.png}). Without one,
 * the screens are see-through: the title panorama, or the world while playing, blurred behind the panels. While a
 * world is open, screens show the blurred world instead, so players still see where they are.
 * <p>
 * Resource packs can restyle a mod's screens too, with {@code assets/<modid>/coolcatcore/config_theme.json}:
 * <pre>{@code
 * {
 *   "base": "tinted",                // "dark", "light" or "tinted" (the dark scheme shaded toward the accent)
 *   "colors": { "accent": "#FF8A3D", "panel": "#C0141018" },
 *   "light_base": "light",           // the light mode's scheme: "light", "dark" or "tinted"
 *   "light_colors": { "accent": "#D9601A" },
 *   "icon": "mymod:textures/gui/config_icon.png",   // instead of the mod's own icon
 *   "background": "mymod:textures/gui/config_background.png",
 *   "background_mode": "cover",      // "cover", "stretch" or "tile"
 *   "tile_size": 32,
 *   "texture_opacity": 0.8,          // the texture's own opacity, from 0 to 1
 *   "background_opacity": 0.3,       // the backdrop color over the background, from 0 to 1
 *   "background_in_world": false
 * }
 * }</pre>
 * Every field is optional and overrides the mod's own theme; color names match {@link ConfigColorScheme.Builder}.
 * Players can scale both opacities for every mod in CoolCatLib: Core's client config.
 */
public final class ConfigTheme {
    /** CoolCatLib: Core's own look: see-through to the title panorama or the world, with the dark scheme. */
    public static final ConfigTheme DEFAULT = builder().build();
    private static final Map<String, ConfigTheme> BY_MOD = new ConcurrentHashMap<>();

    private final ConfigColorScheme colors;
    private final @Nullable ConfigColorScheme lightColors;
    private final @Nullable Identifier icon;
    private final @Nullable Identifier background;
    private final BackgroundMode mode;
    private final int tileSize;
    private final float backgroundOpacity;
    private final float textureOpacity;
    private final boolean backgroundInWorld;

    private ConfigTheme(Builder builder) {
        this.colors = builder.colors;
        this.lightColors = builder.lightColors;
        this.icon = builder.icon;
        this.background = builder.background;
        this.mode = builder.mode;
        this.tileSize = builder.tileSize;
        this.backgroundOpacity = builder.backgroundOpacity;
        this.textureOpacity = builder.textureOpacity;
        this.backgroundInWorld = builder.backgroundInWorld;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The theme of every screen of a mod's configs that don't set their own. */
    public static void setForMod(String modId, ConfigTheme theme) {
        BY_MOD.put(modId, theme);
    }

    /** The mod's theme, or {@link #DEFAULT}; a resource pack's {@code config_theme.json} is applied on top when drawn. */
    public static ConfigTheme forMod(String modId) {
        return BY_MOD.getOrDefault(modId, DEFAULT);
    }

    /** The colors in dark mode. */
    public ConfigColorScheme colors() {
        return this.colors;
    }

    /** The colors in light mode: the ones set, or the light scheme in the dark scheme's accent. */
    public ConfigColorScheme lightColors() {
        return this.lightColors != null ? this.lightColors : ConfigColorScheme.tintedLight(this.colors.accent());
    }

    /** The colors in a mode; players switch modes with the button in the config screens' top bar. */
    public ConfigColorScheme colors(Mode mode) {
        return mode == Mode.LIGHT ? this.lightColors() : this.colors;
    }

    /** The dark mode's accent color, ARGB. */
    public int accent() {
        return this.colors.accent();
    }

    /** The icon beside the title in the top bar, or null for the mod's own icon (its logo on NeoForge). */
    public @Nullable Identifier icon() {
        return this.icon;
    }

    /** The background texture's full path, or null for the see-through background. */
    public @Nullable Identifier background() {
        return this.background;
    }

    public BackgroundMode mode() {
        return this.mode;
    }

    /** The size one tile is drawn at, for {@link BackgroundMode#TILE}; 0 for the texture's own size. */
    public int tileSize() {
        return this.tileSize;
    }

    /**
     * The opacity of the scheme's backdrop color drawn over the background (the texture, or the blurred panorama or
     * world), from 0 (not drawn) to 1 (hides it completely). It keeps the panels readable over busy backgrounds.
     */
    public float backgroundOpacity() {
        return this.backgroundOpacity;
    }

    /**
     * The background texture's own opacity, from 0 to 1. Below 1 the blurred panorama, or the world, shows through it.
     */
    public float textureOpacity() {
        return this.textureOpacity;
    }

    /** Whether the texture is drawn in a world too, instead of the blurred world. */
    public boolean backgroundInWorld() {
        return this.backgroundInWorld;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.colors = this.colors;
        builder.lightColors = this.lightColors;
        builder.icon = this.icon;
        builder.background = this.background;
        builder.mode = this.mode;
        builder.tileSize = this.tileSize;
        builder.backgroundOpacity = this.backgroundOpacity;
        builder.textureOpacity = this.textureOpacity;
        builder.backgroundInWorld = this.backgroundInWorld;
        return builder;
    }

    /** Dark or light: each player's choice for every mod's config screens. */
    public enum Mode {
        DARK,
        LIGHT
    }

    /** How the background texture fills the screen. */
    public enum BackgroundMode {
        /** Scaled to cover the whole screen, keeping its proportions (edges may be cut off). */
        COVER,
        /** Stretched to the screen's size. */
        STRETCH,
        /** Repeated, like the vanilla dirt background. */
        TILE
    }

    public static final class Builder {
        private ConfigColorScheme colors = ConfigColorScheme.DARK;
        private @Nullable ConfigColorScheme lightColors;
        private @Nullable Identifier icon;
        private @Nullable Identifier background;
        private BackgroundMode mode = BackgroundMode.COVER;
        private int tileSize;
        private float backgroundOpacity = 0.35F;
        private float textureOpacity = 1.0F;
        private boolean backgroundInWorld;

        private Builder() {}

        /** Every color of the screens in dark mode. */
        public Builder colors(ConfigColorScheme colors) {
            this.colors = colors;
            return this;
        }

        /** Every color of the screens in light mode; by default the light scheme in the dark scheme's accent. */
        public Builder lightColors(ConfigColorScheme lightColors) {
            this.lightColors = lightColors;
            return this;
        }

        /** Just the accent (of both modes), keeping the rest; an opaque RGB like {@code 0xFF8A3D} works too. */
        public Builder accent(int color) {
            this.colors = this.colors.toBuilder().accent(color).build();
            if (this.lightColors != null) this.lightColors = this.lightColors.toBuilder().accent(color).build();
            return this;
        }

        /** An icon texture's full path for the top bar, instead of the mod's own icon. */
        public Builder icon(@Nullable Identifier texture) {
            this.icon = texture;
            return this;
        }

        /** A texture's full path, like {@code mymod:textures/gui/config_background.png}. */
        public Builder background(@Nullable Identifier texture) {
            this.background = texture;
            return this;
        }

        public Builder mode(BackgroundMode mode) {
            this.mode = mode;
            return this;
        }

        /** Tiles the texture at this size (in GUI pixels); 0 for the texture's own size. */
        public Builder tiled(int tileSize) {
            this.mode = BackgroundMode.TILE;
            this.tileSize = Math.max(0, tileSize);
            return this;
        }

        /**
         * The opacity of the backdrop color over the background, from 0 (not drawn) to 1 (hides it); 0.35 by default.
         */
        public Builder backgroundOpacity(float opacity) {
            this.backgroundOpacity = Math.clamp(opacity, 0, 1);
            return this;
        }

        /** The background texture's opacity, from 0 (invisible) to 1 (opaque, the default). */
        public Builder textureOpacity(float opacity) {
            this.textureOpacity = Math.clamp(opacity, 0, 1);
            return this;
        }

        /** Draws the texture in a world too, instead of the blurred world. */
        public Builder backgroundInWorld(boolean backgroundInWorld) {
            this.backgroundInWorld = backgroundInWorld;
            return this;
        }

        public ConfigTheme build() {
            return new ConfigTheme(this);
        }
    }
}
