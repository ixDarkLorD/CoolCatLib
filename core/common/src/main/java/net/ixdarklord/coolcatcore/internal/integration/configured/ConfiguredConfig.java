package net.ixdarklord.coolcatcore.internal.integration.configured;

import com.google.gson.JsonObject;
import com.mrcrayfish.configured.api.ActionResult;
import com.mrcrayfish.configured.api.ConfigType;
import com.mrcrayfish.configured.api.IConfigEntry;
import com.mrcrayfish.configured.api.IConfigValue;
import com.mrcrayfish.configured.api.IModConfig;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// One CoolCatLib: Core config as Configured sees it. Configured edits a tree of entries made for each screen it opens;
// saving applies the changed ones the way Core's own edits are applied: here (and to the file), or sent to the server
// for a synced config the player may change there.
final class ConfiguredConfig implements IModConfig {
    private final ConfigImpl config;
    // The tree Configured is editing, made fresh for each screen.
    private @Nullable ConfiguredFolder root;

    ConfiguredConfig(ConfigImpl config) {
        this.config = config;
    }

    @Override
    public ConfigType getType() {
        return switch (this.config.scope()) {
            case CLIENT -> ConfigType.CLIENT;
            case COMMON, STARTUP -> ConfigType.UNIVERSAL;
            case SERVER -> ConfigType.SERVER_SYNC;
        };
    }

    @Override
    public String getFileName() {
        return this.config.fileName();
    }

    @Override
    public String getModId() {
        return this.config.modId();
    }

    @Override
    public @Nullable String getTranslationKey() {
        String key = "config." + this.config.modId() + "." + this.config.name() + ".title";
        return Language.getInstance().has(key) ? key : null;
    }

    @Override
    public boolean isReadOnly() {
        return this.access() == ClientConfigManager.Access.READ_ONLY;
    }

    @Override
    public IConfigEntry createRootEntry() {
        this.root = new ConfiguredFolder(this.config.root());
        return this.root;
    }

    @Override
    public ActionResult update(IConfigEntry entry) {
        Map<ConfigValueImpl<?>, Object> changes = new LinkedHashMap<>();
        for (ConfiguredValue<?> value : changedValues(entry)) changes.put(value.configValue(), value.pending());
        if (changes.isEmpty()) return ActionResult.success();
        switch (this.access()) {
            case LOCAL -> {
                this.config.applyChanges(changes);
                this.config.save();
            }
            case REMOTE -> {
                JsonObject json = new JsonObject();
                changes.forEach((value, newValue) -> json.add(value.path(), value.encodeUnchecked(newValue)));
                ClientConfigManager.sendUpdate(this.config, json);
            }
            case READ_ONLY, UNAVAILABLE -> {
                return ActionResult.fail(Component.translatableWithFallback("config.coolcatcore.configured.read_only",
                        "This config can't be changed here"));
            }
        }
        changedValues(entry).forEach(ConfiguredValue::cleanCache);
        return ActionResult.success();
    }

    @Override
    public boolean isChanged() {
        return this.root != null && !changedValues(this.root).isEmpty();
    }

    // Core has no per-world configs, so there's never a world's file to open.
    @Override
    public ActionResult loadWorldConfig(Path serverConfigFolder) {
        return ActionResult.fail();
    }

    @Override
    public void stopEditing(boolean changed) {
        this.root = null;
    }

    @Override
    public Optional<Runnable> restoreDefaultsTask() {
        if (this.access() != ClientConfigManager.Access.LOCAL) return Optional.empty();
        return Optional.of(() -> {
            this.config.resetAll();
            this.config.save();
        });
    }

    @Override
    public ActionResult canPlayerEdit(@Nullable Player player) {
        ClientConfigManager.Access access = this.access();
        return access == ClientConfigManager.Access.LOCAL || access == ClientConfigManager.Access.REMOTE ? ActionResult.success() : ActionResult.fail();
    }

    @Override
    public ActionResult showSaveConfirmation(@Nullable Player player) {
        // Changes to the server's values reach every player.
        return this.access() == ClientConfigManager.Access.REMOTE ? ActionResult.success() : ActionResult.fail();
    }

    // Where edits go: as Core's own screens decide.
    private ClientConfigManager.Access access() {
        if (!Platform.isClient()) return ClientConfigManager.Access.UNAVAILABLE;
        return ClientConfigManager.access(this.config);
    }

    private static List<ConfiguredValue<?>> changedValues(IConfigEntry entry) {
        List<ConfiguredValue<?>> changed = new ArrayList<>();
        collectChanged(entry, changed);
        return changed;
    }

    private static void collectChanged(IConfigEntry entry, List<ConfiguredValue<?>> changed) {
        if (entry.isLeaf()) {
            IConfigValue<?> value = entry.getValue();
            if (value instanceof ConfiguredValue<?> configured && configured.isChanged()) changed.add(configured);
            return;
        }
        for (IConfigEntry child : entry.getChildren()) collectChanged(child, changed);
    }
}
