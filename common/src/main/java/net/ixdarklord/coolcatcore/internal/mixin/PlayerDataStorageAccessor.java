package net.ixdarklord.coolcatcore.internal.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

// Where players' data files are, and the data fixer that brings them up to date, to read and write an offline player's
// values (OfflineAttachments).
@Mixin(PlayerDataStorage.class)
public interface PlayerDataStorageAccessor {
    @Accessor("playerDir")
    File coolcatlib$playerDir();

    @Accessor("fixerUpper")
    DataFixer coolcatlib$fixerUpper();
}
