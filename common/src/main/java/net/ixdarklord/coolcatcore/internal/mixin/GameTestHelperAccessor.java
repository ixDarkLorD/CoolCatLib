package net.ixdarklord.coolcatcore.internal.mixin;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// GameTests: the running test a helper belongs to, for the helper CoolCatLib's tests get instead (CornerGameTestHelper).
@Mixin(GameTestHelper.class)
public interface GameTestHelperAccessor {
    @Accessor("testInfo")
    GameTestInfo coolcatlib$testInfo();
}
