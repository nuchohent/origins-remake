package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sharp direction changes cancel horizontal momentum; sustained straight-line
 * movement accelerates by {@code accelFactor}.
 * <p>
 * The logic runs CLIENT-side ({@link #applyClient}): the local player's
 * motion is client-authoritative, so per-tick {@code deltaMovement} edits on
 * the server entity were always overridden by the client's own movement
 * simulation and the power never did anything.
 */
public class HyperInertiaPower implements Power {

    /** Cap for the accelerated horizontal speed, in units of the player's own
     *  movement-speed attribute (5.0 x attribute ≈ 2x sprint). */
    private static final double MAX_SPEED_FACTOR = 5.0;

    private final Identifier id;
    private final int difficulty;
    private final double turnThreshold;
    private final double accelFactor;
    private final Map<UUID, Vec3> lastDirection = new HashMap<>();

    public HyperInertiaPower(Identifier id, int difficulty, double turnThreshold, double accelFactor) {
        this.id = id;
        this.difficulty = difficulty;
        this.turnThreshold = turnThreshold;
        this.accelFactor = accelFactor;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.hyper_inertia.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.hyper_inertia.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    /** Client tick: apply the inertia rules to the local player's motion.
     *  {@code hasMovementInput} is true while the player holds a movement key:
     *  acceleration only applies then, so releasing the keys lets friction
     *  actually stop the player (previously the per-tic boost kept fighting
     *  friction and the player could never stop). */
    public void applyClient(Entity player, boolean hasMovementInput) {
        UUID uuid = player.getUUID();
        Vec3 lastDir = lastDirection.getOrDefault(uuid, Vec3.ZERO);

        Vec3 motion = player.getDeltaMovement();
        Vec3 horizontal = new Vec3(motion.x, 0, motion.z);

        if (player.onGround()) {
            if (horizontal.lengthSqr() > 0.001) {
                Vec3 currentDir = horizontal.normalize();
                if (lastDir.lengthSqr() > 0.001 && currentDir.dot(lastDir) < turnThreshold) {
                    // sharp turn: momentum is lost
                    player.setDeltaMovement(0, motion.y, 0);
                    lastDirection.put(uuid, currentDir);
                    return;
                }
                lastDirection.put(uuid, currentDir);
            } else {
                lastDirection.put(uuid, Vec3.ZERO);
            }

            if (hasMovementInput && horizontal.lengthSqr() > 0.001) {
                // accelerate along the movement direction, capped relative to
                // the player's own walk speed (~2x sprint) so the loop cannot
                // run away
                double cap = MAX_SPEED_FACTOR * speedAttribute(player);
                double scale = 1.0 + accelFactor;
                double newLen = horizontal.length() * scale;
                if (newLen > cap) {
                    scale *= cap / newLen;
                }
                player.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
            }
        } else {
            if (horizontal.lengthSqr() > 0.001) {
                Vec3 currentDir = horizontal.normalize();
                if (lastDir.lengthSqr() > 0.001 && currentDir.dot(lastDir) < turnThreshold) {
                    player.setDeltaMovement(0, motion.y, 0);
                }
                lastDirection.put(uuid, currentDir);
            }
        }
    }

    private static double speedAttribute(Entity player) {
        return player instanceof net.minecraft.world.entity.LivingEntity living
                ? living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                : 0.1;
    }

    @Override
    public void onRemove(ServerPlayer player) {
        lastDirection.remove(player.getUUID());
    }
}
