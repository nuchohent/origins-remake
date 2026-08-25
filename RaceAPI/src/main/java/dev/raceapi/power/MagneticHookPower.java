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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MagneticHookPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double range;
    private final double pullSpeed;
    private final float pullDamage;

    public MagneticHookPower(Identifier id, int difficulty, int cooldownTicks,
                             double range, double pullSpeed, float pullDamage) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.range = range;
        this.pullSpeed = pullSpeed;
        this.pullDamage = pullDamage;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.magnetic_hook.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.magnetic_hook.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;
        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);

        ServerLevel level = RaceUtils.serverLevel(player);
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eyePos.add(look.scale(range));

        AABB searchBox = new AABB(eyePos, end).inflate(1.5);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive());

        Vec3 playerTarget = player.position().add(0, player.getBbHeight() / 2, 0);

        boolean hitAny = false;
        for (LivingEntity e : entities) {
            double dist = e.distanceTo(player);
            if (dist <= range) {
                Vec3 dir = playerTarget.subtract(e.position().add(0, e.getBbHeight() / 2, 0))
                        .normalize().scale(pullSpeed);
                e.push(dir.x, dir.y + 0.5, dir.z);
                e.hurtMarked = true;
                e.hurt(level.damageSources().playerAttack(player), pullDamage);
                hitAny = true;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(),
                        3, 0.2, 0.2, 0.2, 0.05);
            }
        }

        if (hitAny) {
            spawnTrail(level, player.position(), playerTarget);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 0.8f);
        } else {
            Vec3 target = end;
            Vec3 dir = target.subtract(player.position()).normalize().scale(pullSpeed);
            player.setDeltaMovement(dir);
            player.hurtMarked = true;
            spawnTrail(level, player.position(), target);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.2f);
        }
    }

    private void spawnTrail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        Vec3 norm = dir.normalize();
        for (double t = 0; t < dist; t += 0.5) {
            Vec3 p = from.add(norm.scale(t));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
        }
    }
}
