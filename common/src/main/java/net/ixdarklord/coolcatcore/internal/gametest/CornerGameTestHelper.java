package net.ixdarklord.coolcatcore.internal.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.world.phys.Vec3;

// The helper CoolCatLib's game tests get on this version, counting positions from the structure's own corner as newer
// versions do: (0, 0, 0) is the structure's lowest block, (0, 0, 0) to (7, 0, 7) the platform's floor. The game's own
// helper counts from the structure block, one below the structure. Every position the helper takes or gives goes
// through these four, the structure's bounds included.
final class CornerGameTestHelper extends GameTestHelper {
    CornerGameTestHelper(GameTestInfo testInfo) {
        super(testInfo);
    }

    @Override
    public BlockPos absolutePos(BlockPos pos) {
        return super.absolutePos(pos.above());
    }

    @Override
    public BlockPos relativePos(BlockPos pos) {
        return super.relativePos(pos).below();
    }

    @Override
    public Vec3 absoluteVec(Vec3 relativeVec3) {
        return super.absoluteVec(relativeVec3.add(0.0, 1.0, 0.0));
    }

    @Override
    public Vec3 relativeVec(Vec3 absoluteVec3) {
        return super.relativeVec(absoluteVec3).subtract(0.0, 1.0, 0.0);
    }
}
