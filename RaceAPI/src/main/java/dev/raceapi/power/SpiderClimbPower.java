package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Spider-style climbing: while airborne and pressed against a wall the player
 * clings and slowly crawls upward; holding sneak lets them descend. Requires
 * actual horizontal collision (vanilla flag) to prevent false triggers on flat
 * ground near block edges.
 */
public class SpiderClimbPower implements Power {

    private static final double CLIMB_SPEED = 0.25;
    private static final double DESCEND_SPEED = -0.15;

    private final Identifier id;
    private final int difficulty;

    public SpiderClimbPower(Identifier id, int difficulty) {
        this.id = id;
        this.difficulty = difficulty;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power." + id.getNamespace() + "." + id.getPath() + ".desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onTick(ServerPlayer player) {
        // climbing is applied CLIENT-side (applyClient): the local player's
        // motion is client-authoritative, per-tick server-side velocity edits
        // were overridden by the client simulation every tick
    }

    /**
     * Client tick: while airborne and pressed against a wall, cling and crawl
     * upward (sneak = descend). Runs on the local player only.
     */
    public void applyClient(net.minecraft.world.entity.player.Player player) {
        if (player.onGround() || player.isInWater() || player.isInLava()) {
            return;
        }
        // Climb whenever a solid block is directly beside the player's body,
        // without relying on the server-side horizontalCollision flag (which is
        // not reliably set every tick).
        if (!touchingWall(player)) {
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        double targetY = player.isCrouching() ? DESCEND_SPEED : CLIMB_SPEED;
        if (motion.y < targetY) {
            player.setDeltaMovement(motion.x, targetY, motion.z);
        }

        player.fallDistance = 0.0f;
    }

    private boolean touchingWall(net.minecraft.world.entity.player.Player player) {
        var level = player.level();
        BlockPos origin = player.blockPosition();
        // Only check body and head level (y=0 and y=1), skip y=-1 to avoid
        // detecting ground blocks under the player's feet
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int y = 0; y <= 1; y++) {
                BlockPos pos = origin.above(y).relative(dir);
                BlockState state = level.getBlockState(pos);
                if (state.blocksMotion()) {
                    return true;
                }
            }
        }
        return false;
    }
}
