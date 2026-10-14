package net.ixdarklord.coolcatcore.api.config;

import com.google.gson.JsonObject;

/**
 * Upgrades a config file's contents by one version, before the values are read. The file's version is kept in its
 * {@code "$version"} key; a config built with {@link ConfigBuilder#version(int)} runs every migration from the
 * file's version up to its own, in order, then rewrites the file.
 * <pre>{@code
 * builder.version(2).migration(1, root -> {
 *     // "renderDistance" moved into the "rendering" group
 *     JsonObject rendering = root.getAsJsonObject("rendering");
 *     rendering.add("distance", root.remove("renderDistance"));
 * });
 * }</pre>
 * Simple renames don't need a migration: see {@code aliases} on the entry builders.
 */
@FunctionalInterface
public interface ConfigMigration {
    /**
     * @param root the file's contents as nested objects, whatever its format; change it in place
     */
    void migrate(JsonObject root);
}
