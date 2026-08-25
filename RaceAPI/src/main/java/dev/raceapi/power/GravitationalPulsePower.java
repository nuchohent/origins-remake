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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GravitationalPulsePower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double radius;
    private final double force;
    private final int cooldownTicks;
    private final int maxChargeTicks;
    private final float damage;
    private final Map<UUID, ChargeState> states = new HashMap<>();

    public GravitationalPulsePower(Identifier id, int difficulty, double radius, double force,
                                   int cooldownTicks, int maxChargeTicks, float damage) {
        this.id = id;
        this.difficulty = difficulty;
        this.radius = radius;
        this.force = force;
        this.cooldownTicks = cooldownTicks;
        this.maxChargeTicks = maxChargeTicks;
        this.damage = damage;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.gravity_pulse.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.gravity_pulse.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        // pressing the key again while charging releases the pulse early:
        // the longer the charge, the stronger the effect
        ChargeState charging = states.get(player.getUUID());
        if (charging != null && !charging.released) {
            states.remove(player.getUUID());
            release(player, charging);
            return;
        }
        if (PowerCooldowns.remaining(player, id, cooldownTicks) > 0) return;
        PowerCooldowns.markPendingUse(player, id);
        states.put(player.getUUID(), new ChargeState());
    }

    @Override
    public void onTick(ServerPlayer player) {
        ChargeState state = states.get(player.getUUID());
        if (state == null || state.released) return;

        state.chargeTicks++;

        player.setDeltaMovement(player.getDeltaMovement().scale(0.2));
        player.hurtMarked = true;

        ServerLevel level = RaceUtils.serverLevel(player);
        if (state.chargeTicks % 3 == 0) {
            double progress = (double) state.chargeTicks / maxChargeTicks;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1, player.getZ(),
                    (int) (progress * 5), 0.3, 0.3, 0.3, 0.02);
        }

        if (state.chargeTicks >= maxChargeTicks) {
            states.remove(player.getUUID());
            release(player, state);
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        states.remove(player.getUUID());
        PowerCooldowns.clearPendingUse(player, id);
    }

    private void release(ServerPlayer player, ChargeState state) {
        if (state.released) return;
        state.released = true;

        // the cooldown starts when the pulse actually goes out (early or
        // fully charged alike), so a second press can always reach the
        // early-release branch above
        PowerCooldowns.forceUse(player, id);
        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);

        // push/pull is decided at release time by the current sneak state
        boolean push = player.isShiftKeyDown();

        float chargeFraction = Math.min(1.0f, (float) state.chargeTicks / maxChargeTicks);
        if (chargeFraction < 0.1f) return;

        ServerLevel level = RaceUtils.serverLevel(player);
        Vec3 pos = player.position();

        AABB box = new AABB(pos.x - radius, pos.y - 1, pos.z - radius,
                pos.x + radius, pos.y + 3, pos.z + radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            double dist = target.distanceTo(player);
            double strength = force * chargeFraction * (1.0 - dist / radius);
            if (strength <= 0) continue;

            Vec3 dir;
            if (push) {
                dir = target.position().subtract(pos).normalize().scale(strength);
                dir = new Vec3(dir.x, Math.max(dir.y, 0.3), dir.z);
            } else {
                dir = pos.subtract(target.position()).normalize().scale(strength);
                dir = new Vec3(dir.x, Math.min(dir.y, 0.5), dir.z);
            }
            target.push(dir.x, dir.y, dir.z);
            target.hurtMarked = true;
            target.hurt(level.damageSources().playerAttack(player), damage * chargeFraction);
        }

        if (push) {
            List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, box, Projectile::isAlive);
            Vec3 look = player.getLookAngle().normalize();
            for (Projectile proj : projectiles) {
                Vec3 deflect = proj.getDeltaMovement().add(look.scale(2));
                proj.setDeltaMovement(deflect);
                proj.hurtMarked = true;
            }
        }

        level.sendParticles(push ? ParticleTypes.EXPLOSION : ParticleTypes.SOUL_FIRE_FLAME,
                pos.x, pos.y + 1, pos.z, 5, 1.0, 0.5, 1.0, 0.05);
        float volume = 0.8f * chargeFraction;
        float pitch = push ? 0.8f : 1.5f;
        if (push) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, volume, pitch);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, volume, pitch);
        }
    }

    private static class ChargeState {
        int chargeTicks = 0;
        boolean released = false;
    }
}
