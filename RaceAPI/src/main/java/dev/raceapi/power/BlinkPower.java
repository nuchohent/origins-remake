package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.network.CooldownPayload;
import dev.raceapi.race.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Active power that teleports the player a short distance toward where they
 * look. Handy for ambushing, escaping and exploring.
 */
public class BlinkPower implements Power {

    private static final int DEFAULT_COOLDOWN = 80;

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double range;

    public BlinkPower(Identifier id, int difficulty) {
        this(id, difficulty, DEFAULT_COOLDOWN, 10.0);
    }

    public BlinkPower(Identifier id, int difficulty, int cooldownTicks, double range) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.range = range;
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
    public boolean hasBinding() {
        return true;
    }

    @Override
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        // resolve the destination FIRST so a failed blink does not burn the
        // cooldown; the landing point must fit the whole bounding box, so the
        // blink settles on the last fully free spot along the ray
        ServerLevel level = RaceUtils.serverLevel(player);
        Vec3 from = player.position().add(0, player.getEyeHeight() * 0.5, 0);
        Vec3 look = player.getLookAngle();
        double r = player.getBbWidth() / 2.0;
        double h = player.getBbHeight();
        Vec3 target = null;
        for (double d = 1.0; d <= range; d += 0.5) {
            Vec3 candidate = from.add(look.x * d, look.y * d, look.z * d);
            BlockPos pos = BlockPos.containing(candidate);
            if (!level.getBlockState(pos).isAir()) {
                break;
            }
            Vec3 spot = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            AABB box = new AABB(spot.x - r, spot.y, spot.z - r, spot.x + r, spot.y + h, spot.z + r);
            if (level.noCollision(player, box)) {
                target = spot;
            }
        }
        if (target == null) {
            return;
        }

        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) {
            return;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(0.5), player.getZ(),
                24, 0.4, 0.4, 0.4, 0.1);

        player.teleportTo(target.x, target.y, target.z);
        level.playSound(null, target.x, target.y, target.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL, target.x, target.y + 0.5, target.z,
                24, 0.4, 0.4, 0.4, 0.1);
        player.gameEvent(GameEvent.TELEPORT);

        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
    }
}
