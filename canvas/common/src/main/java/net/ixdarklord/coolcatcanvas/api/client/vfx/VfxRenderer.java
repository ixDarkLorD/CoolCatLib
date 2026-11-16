package net.ixdarklord.coolcatcanvas.api.client.vfx;

/** Draws world effects each frame; registered with {@link WorldVfx#register}. */
@FunctionalInterface
public interface VfxRenderer {
    void render(VfxFrame frame);
}
