package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lets the player kick off walls while airborne: when pressed against a wall
 * the player can boost away from it and upward. Only triggers when the player
 * is hugging a wall they are facing, so brushing past blocks while running or
 * jumping does not fling the player around.
 */
public class WallJumpPower implements Power {

    private static final int WALL_COOLDOWN = 12;
    private static final double WALL_RANGE = 1.2;

    private final Identifier id;
    private final int difficulty;

    public WallJumpPower(Identifier id, int difficulty) {
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

    /**
     * True when the player is looking at and hugging a wall within reach.
     * Works for both the server player and the client-side local player.
     */
    private static boolean pressingWall(net.minecraft.world.entity.player.Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 0.0001) {
            return false;
        }
        dir = dir.normalize();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(dir.scale(WALL_RANGE * 1.5)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && eye.distanceToSqr(hit.getLocation()) <= WALL_RANGE * WALL_RANGE;
    }

    @Override
    public void onJump(ServerPlayer player) {
        // Ground jump while pressed fully against a wall: give a small upward bonus
        if (!player.horizontalCollision || !pressingWall(player)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.max(motion.y, 0.6), motion.z);
        player.hurtMarked = true;
    }

    @Override
    public void onTick(ServerPlayer player) {
        // wall jumps are applied CLIENT-side (applyClient): the trigger needs
        // the client's reliable horizontalCollision and client-authoritative
        // motion - the server-side version almost never fired correctly
    }

    @Override
    public void onRemove(ServerPlayer player) {
        // drop the stale client cooldown so re-picking the race starts fresh
        wallJumpReadyAt.remove(player.getUUID());
    }

    /**
     * Client tick: kick off the wall while airborne. Uses the client's own
     * horizontalCollision flag and a local cooldown (the client power instance
     * has no access to the server-side PowerCooldowns storage).
     */
    public void applyClient(net.minecraft.world.entity.player.Player player) {
        if (player.onGround() || player.isInWater() || player.isInLava()) {
            return;
        }
        // Only trigger with full model contact against a wall, not a corner clip
        if (!player.horizontalCollision || !pressingWall(player)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y > 0.4) {
            return; // still rising fast from the initial jump
        }
        long now = player.level().getGameTime();
        Long next = wallJumpReadyAt.get(player.getUUID());
        if (next != null && now < next) {
            return;
        }
        wallJumpReadyAt.put(player.getUUID(), now + WALL_COOLDOWN);

        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 0.0001) {
            dir = new Vec3(1, 0, 0);
        } else {
            dir = dir.normalize();
        }
        player.fallDistance = 0.0f;
        player.setDeltaMovement(dir.x * 0.6, 0.65, dir.z * 0.6);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 0.5F, 1.3F);
    }

    private final java.util.Map<java.util.UUID, Long> wallJumpReadyAt = new java.util.HashMap<>();
}
