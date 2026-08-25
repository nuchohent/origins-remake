package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.network.CooldownPayload;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Active power: teleports the player to the nearest living entity they are looking at
 * within range, deals damage and applies Nausea.
 */
public class TeleportStrikePower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double range;
    private final float damage;

    public TeleportStrikePower(Identifier id, int difficulty, int cooldownTicks, double range, float damage) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.range = range;
        this.damage = damage;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.teleport_strike.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.teleport_strike.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        ServerLevel level = RaceUtils.serverLevel(player);
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(range));

        // Find nearest entity along look direction using raycast
        LivingEntity target = null;
        double closestDist = Double.MAX_VALUE;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eyePos, endPos).inflate(1.0), e -> e != player && e.isAlive());

        for (LivingEntity entity : candidates) {
            // Simple distance + angle check
            Vec3 toEntity = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(eyePos);
            double dist = toEntity.length();
            if (dist > range || dist > closestDist) continue;
            double dot = toEntity.normalize().dot(lookVec);
            if (dot > 0.95) {
                closestDist = dist;
                target = entity;
            }
        }

        // no target: the strike fails without burning the cooldown
        if (target == null) {
            return;
        }

        // find a free standing spot behind the target BEFORE burning the
        // cooldown: if nothing fits the strike fails and stays retryable
        Vec3 rawBehind = target.position().subtract(target.getLookAngle().scale(1.5));
        Vec3 behind = new Vec3(rawBehind.x, Math.max(rawBehind.y, target.getY()), rawBehind.z);
        Vec3 safe = findSafeSpot(level, player, behind);
        if (safe == null) {
            return;
        }

        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;

        player.teleportTo(safe.x, safe.y, safe.z);
        player.fallDistance = 0;

        // Deal damage
        target.hurt(level.damageSources().playerAttack(player), damage);
        target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, false, false, true));

        level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1, target.getZ(),
                10, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);

        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
    }

    private static Vec3 findSafeSpot(ServerLevel level, ServerPlayer player, Vec3 dest) {
        double r = player.getBbWidth() / 2.0;
        double h = player.getBbHeight();
        Vec3[] probes = new Vec3[]{
                dest,
                dest.add(0, 1, 0),
                dest.add(0, -1, 0),
                dest.add(0, -2, 0),
                dest.add(0, -3, 0)
        };
        for (Vec3 p : probes) {
            AABB box = new AABB(p.x - r, p.y, p.z - r, p.x + r, p.y + h, p.z + r);
            if (level.noCollision(player, box)) {
                return p;
            }
        }
        return null;
    }
}
