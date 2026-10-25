package net.ixdarklord.coolcatcore.internal.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

// The helper CoolCatLib's game tests get on this version, counting positions from the structure's own corner as newer
// versions do: (0, 0, 0) is the structure's lowest block, (0, 0, 0) to (7, 0, 7) the platform's floor. The game's own
// helper counts from the structure block, one below the structure (Forge's counts from the corner when its
// fixGameTestBlockPosAsserts option is on); this one does the same everywhere. These four are the game's, turned about
// the structure's corner instead of its structure block; every position the helper takes or gives goes through them.
final class CornerGameTestHelper extends GameTestHelper {
    private final GameTestInfo info;

    CornerGameTestHelper(GameTestInfo testInfo) {
        super(testInfo);
        this.info = testInfo;
    }

    private BlockPos corner() {
        return StructureUtils.getStructureOrigin(this.info.getStructureBlockEntity());
    }

    @Override
    public BlockPos absolutePos(BlockPos pos) {
        BlockPos corner = this.corner();
        return StructureTemplate.transform(corner.offset(pos), Mirror.NONE, this.info.getRotation(), corner);
    }

    @Override
    public BlockPos relativePos(BlockPos pos) {
        BlockPos corner = this.corner();
        return StructureTemplate.transform(pos, Mirror.NONE, this.info.getRotation().getRotated(Rotation.CLOCKWISE_180), corner).subtract(corner);
    }

    @Override
    public Vec3 absoluteVec(Vec3 relativeVec3) {
        BlockPos corner = this.corner();
        return StructureTemplate.transform(Vec3.atLowerCornerOf(corner).add(relativeVec3), Mirror.NONE, this.info.getRotation(), corner);
    }

    @Override
    public Vec3 relativeVec(Vec3 absoluteVec3) {
        BlockPos corner = this.corner();
        return StructureTemplate.transform(absoluteVec3.subtract(Vec3.atLowerCornerOf(corner)), Mirror.NONE, this.info.getRotation(), corner);
    }
}
