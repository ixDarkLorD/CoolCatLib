package net.ixdarklord.coolcatcore.internal.config.client.gui;

import com.google.gson.JsonObject;
import net.ixdarklord.coolcatcore.api.config.ConfigDependency;
import net.ixdarklord.coolcatcore.api.config.ConfigPreset;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.api.config.RestartRequirement;
import net.ixdarklord.coolcatcore.api.config.client.EditSlot;
import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// The changes made in one config's screens: pending values (applied on save), invalid input, and undo/redo history.
public final class ConfigEditSession {
    private static final long MERGE_WINDOW_MS = 1000;
    private static final int MAX_HISTORY = 200;

    private final ConfigImpl config;
    private final ClientConfigManager.Access access;
    private final Map<ConfigValueImpl<?>, Object> pending = new LinkedHashMap<>();
    private final Map<ConfigValueImpl<?>, Component> errors = new HashMap<>();
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();

    public ConfigEditSession(ConfigImpl config) {
        this.config = config;
        this.access = ClientConfigManager.access(config);
    }

    public ConfigImpl config() {
        return this.config;
    }

    public ClientConfigManager.Access access() {
        return this.access;
    }

    // --- Values ---

    public Object get(ConfigValueImpl<?> value) {
        return this.pending.getOrDefault(value, value.getStored());
    }

    public boolean isModified(ConfigValueImpl<?> value) {
        return this.pending.containsKey(value) || this.errors.containsKey(value);
    }

    public boolean isDefault(ConfigValueImpl<?> value) {
        return value.sameValue(this.get(value), value.getDefault());
    }

    public Optional<Component> error(ConfigValueImpl<?> value) {
        return Optional.ofNullable(this.errors.get(value));
    }

    public int modifiedCount() {
        return this.pending.size();
    }

    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    public Collection<Component> errors() {
        return this.errors.values();
    }

    /** Whether the player may change the value: the config is editable here, and a server-only value is visible. */
    public boolean isEditable(ConfigValueImpl<?> value) {
        return switch (this.access) {
            case LOCAL -> true;
            case REMOTE -> value.isSynced() || Minecraft.getInstance().isLocalServer();
            case READ_ONLY, UNAVAILABLE -> false;
        };
    }

    /** Whether the value's dependency is met by the pending values. */
    public boolean isActive(ConfigValueImpl<?> value) {
        Optional<ConfigDependency<?>> dependency = value.dependency();
        if (dependency.isEmpty()) return true;
        ConfigValue<?> source = dependency.get().source();
        Object sourceValue = source instanceof ConfigValueImpl<?> impl && impl.configOrNull() == this.config ? this.get(impl) : source.get();
        return dependency.get().isMetBy(sourceValue);
    }

    /** Whether a remote player sees this value at all: server-only values aren't sent to them. */
    public boolean isVisible(ConfigValueImpl<?> value) {
        return !value.isHidden() && (this.access != ClientConfigManager.Access.REMOTE && this.access != ClientConfigManager.Access.READ_ONLY
                || value.isSynced() || Minecraft.getInstance().isLocalServer());
    }

    /** Sets a pending value, checked by the value's validators; invalid values are reported instead. */
    public void set(ConfigValueImpl<?> value, Object newValue) {
        ValidationResult<?> result = value.validateUnchecked(newValue);
        if (!result.isOk()) {
            this.errors.put(value, result.message().orElse(Component.empty()));
            return;
        }
        this.errors.remove(value);
        Object before = this.get(value);
        if (value.sameValue(before, newValue)) return;
        this.record(new Edit(List.of(new Change(value, before, newValue)), System.currentTimeMillis()));
        this.store(value, newValue);
    }

    public void setError(ConfigValueImpl<?> value, Component error) {
        this.errors.put(value, error);
    }

    /** Sets several pending values as one undoable step. */
    public void setAll(Map<ConfigValueImpl<?>, Object> values) {
        List<Change> changes = new ArrayList<>();
        values.forEach((value, newValue) -> {
            this.errors.remove(value);
            Object before = this.get(value);
            if (!value.sameValue(before, newValue)) changes.add(new Change(value, before, newValue));
        });
        if (changes.isEmpty()) return;
        this.record(new Edit(changes, 0));
        changes.forEach(change -> this.store(change.value, change.after));
    }

    public void resetToDefaults(Collection<ConfigValueImpl<?>> values) {
        Map<ConfigValueImpl<?>, Object> defaults = new LinkedHashMap<>();
        for (ConfigValueImpl<?> value : values) {
            if (this.isEditable(value)) defaults.put(value, value.getDefault());
        }
        this.setAll(defaults);
    }

    /** Drops pending changes, as if nothing had been edited. */
    public void discard(Collection<ConfigValueImpl<?>> values) {
        Map<ConfigValueImpl<?>, Object> current = new LinkedHashMap<>();
        for (ConfigValueImpl<?> value : values) {
            this.errors.remove(value);
            current.put(value, value.getStored());
        }
        this.setAll(current);
    }

    public void applyPreset(ConfigPreset preset) {
        Map<ConfigValueImpl<?>, Object> values = new LinkedHashMap<>();
        preset.values().forEach((value, presetValue) -> {
            if (value instanceof ConfigValueImpl<?> impl && this.isEditable(impl)) values.put(impl, presetValue);
        });
        this.setAll(values);
    }

    // Typing in a text box makes one edit per key; edits to the same value close together undo as one.
    private void record(Edit edit) {
        this.redo.clear();
        Edit last = this.undo.peek();
        if (last != null && edit.time > 0 && last.time > 0 && edit.time - last.time < MERGE_WINDOW_MS
                && last.changes.size() == 1 && edit.changes.size() == 1 && last.changes.getFirst().value == edit.changes.getFirst().value) {
            this.undo.pop();
            edit = new Edit(List.of(new Change(last.changes.getFirst().value, last.changes.getFirst().before, edit.changes.getFirst().after)), edit.time);
        }
        this.undo.push(edit);
        while (this.undo.size() > MAX_HISTORY) this.undo.removeLast();
    }

    private void store(ConfigValueImpl<?> value, Object newValue) {
        if (value.sameValue(newValue, value.getStored())) this.pending.remove(value);
        else this.pending.put(value, newValue);
    }

    public boolean canUndo() {
        return !this.undo.isEmpty();
    }

    public boolean canRedo() {
        return !this.redo.isEmpty();
    }

    public void undo() {
        Edit edit = this.undo.poll();
        if (edit == null) return;
        for (Change change : edit.changes) {
            this.errors.remove(change.value);
            this.store(change.value, change.before);
        }
        this.redo.push(edit);
    }

    public void redo() {
        Edit edit = this.redo.poll();
        if (edit == null) return;
        for (Change change : edit.changes) {
            this.errors.remove(change.value);
            this.store(change.value, change.after);
        }
        this.undo.push(new Edit(edit.changes, 0));
    }

    /** Drops pending values that the config now holds anyway (after the server's values arrived). */
    public void onConfigChanged() {
        this.pending.entrySet().removeIf(entry -> entry.getKey().sameValue(entry.getValue(), entry.getKey().getStored()));
    }

    /**
     * Applies the pending values: locally (and saves the file), or by sending them to the server.
     *
     * @return the strictest restart the changes need
     */
    public RestartRequirement save() {
        if (this.pending.isEmpty() || this.hasErrors()) return RestartRequirement.NONE;
        RestartRequirement restart = ConfigImpl.restartFor(this.pending.keySet());
        if (this.access == ClientConfigManager.Access.REMOTE) {
            JsonObject changes = new JsonObject();
            this.pending.forEach((value, newValue) -> changes.add(value.path(), value.encodeUnchecked(newValue)));
            ClientConfigManager.sendUpdate(this.config, changes);
        } else if (this.access == ClientConfigManager.Access.LOCAL) {
            this.config.applyChanges(this.pending);
            this.config.save();
        }
        this.pending.clear();
        this.undo.clear();
        this.redo.clear();
        return restart;
    }

    /** Saves as {@link #save()} does, and tells the player: a toast for a local save, and what must restart. */
    public void saveAndNotify() {
        ClientConfigManager.Access access = this.access;
        RestartRequirement restart = this.save();
        // One toast, titled with the config's name: what must restart, or that it saved.
        Component message = switch (restart) {
            case GAME -> Component.translatableWithFallback("config.coolcatcore.toast.restart_game",
                    "Restart the game to apply every change").withStyle(ChatFormatting.GOLD);
            case WORLD -> Component.translatableWithFallback("config.coolcatcore.toast.restart_world",
                    "Rejoin the world to apply every change").withStyle(ChatFormatting.GOLD);
            case NONE -> access == ClientConfigManager.Access.LOCAL
                    ? Component.translatableWithFallback("config.coolcatcore.toast.saved", "Config saved") : null;
        };
        if (message != null) ClientConfigManager.toast(this.config.title(), message);
    }

    /** A slot for one of this config's values. */
    public <T> EditSlot<T> slot(ConfigValueImpl<T> value) {
        return new EditSlot<>() {
            @Override
            public ConfigType<T> type() {
                return value.type();
            }

            @Override
            @SuppressWarnings("unchecked")
            public T get() {
                return (T) ConfigEditSession.this.get(value);
            }

            @Override
            public void set(T newValue) {
                ConfigEditSession.this.set(value, newValue);
            }

            @Override
            public void setInvalid(Component error) {
                ConfigEditSession.this.setError(value, error);
            }

            @Override
            public Optional<Component> error() {
                return ConfigEditSession.this.error(value);
            }

            @Override
            public Component name() {
                return value.displayName();
            }
        };
    }

    private record Change(ConfigValueImpl<?> value, @Nullable Object before, Object after) {}

    private record Edit(List<Change> changes, long time) {}
}
