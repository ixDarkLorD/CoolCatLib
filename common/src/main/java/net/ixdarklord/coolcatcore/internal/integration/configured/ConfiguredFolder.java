package net.ixdarklord.coolcatcore.internal.integration.configured;

import com.mrcrayfish.configured.api.IConfigEntry;
import com.mrcrayfish.configured.api.IConfigValue;
import com.mrcrayfish.configured.api.ValueEntry;
import net.ixdarklord.coolcatcore.api.config.ConfigGroup;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// A config group as a Configured folder: its visible values and nested groups, in order.
final class ConfiguredFolder implements IConfigEntry {
    private final ConfigGroup group;
    private @Nullable List<IConfigEntry> children;

    ConfiguredFolder(ConfigGroup group) {
        this.group = group;
    }

    @Override
    public List<IConfigEntry> getChildren() {
        if (this.children == null) {
            List<IConfigEntry> children = new ArrayList<>();
            for (ConfigNode node : this.group.children()) {
                if (node.isHidden()) continue;
                if (node instanceof ConfigGroup subgroup) children.add(new ConfiguredFolder(subgroup));
                else if (node instanceof ConfigValueImpl<?> value) children.add(new ValueEntry(ConfiguredValue.of(value)));
            }
            this.children = children;
        }
        return this.children;
    }

    @Override
    public boolean isRoot() {
        return this.group.isRoot();
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public @Nullable IConfigValue<?> getValue() {
        return null;
    }

    @Override
    public String getEntryName() {
        return this.group.displayName().getString();
    }

    @Override
    public @Nullable Component getTooltip() {
        return this.group.comment().isEmpty() ? null : Component.literal(String.join("\n", this.group.comment()));
    }

    @Override
    public String getTranslationKey() {
        return this.group.translationKey();
    }
}
