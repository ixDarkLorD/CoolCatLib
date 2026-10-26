package net.ixdarklord.coolcatcore.internal.mixin;

import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

// Where players' data files are, to write an offline player's values (OfflineAttachments).
@Mixin(PlayerDataStorage.class)
public interface PlayerDataStorageAccessor {
    @Accessor("playerDir")
    File coolcatlib$playerDir();
}
