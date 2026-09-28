package net.ixdarklord.coolcatcore.internal.core;

import net.ixdarklord.coolcatcore.api.core.ModConstructor;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentSync;
import net.ixdarklord.coolcatcore.internal.menu.StorageMenuValuesPayload;

// CoolCat Core's own common entry point; the loaders hook up their events first, then construct it.
public final class CoolCatCoreConstructor implements ModConstructor {
    @Override
    public void onConstructMod() {
        ConfigManager.init();
        AttachmentSync.init();
        StorageMenuValuesPayload.init();
    }
}
