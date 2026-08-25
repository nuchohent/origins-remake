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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Active power: teleports the player forward along the crosshair (look)
 * direction up to {@code range} blocks, stopping before the first blocking
 * block. If a living entity stands near the landing point it takes backstab
 * damage, Weakness and Slowness.
 */
public class ShadowStepPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double range;
    private final float damage;
    private final int darknessDuration;
    private final int weaknessDuration;

    public ShadowStepPower(Identifier id, int difficulty, int cooldownTicks,
                           double range, float damage, int darknessDuration, int weaknessDuration) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.range = range;
        this.damage = damage;
        this.darknessDuration = darknessDuration;
        this.weaknessDuration = weaknessDuration;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.shadow_step.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.shadow_step.desc", (int) range); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        // check the cooldown WITHOUT consuming it first: if there is nowhere
        // to land the key must stay retryable
        if (PowerCooldowns.remaining(player, id, cooldownTicks) > 0) return;

        ServerLevel level = RaceUtils.serverLevel(player);
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // ray along the crosshair; stop at the first blocking block
        var clip = level.clip(new ClipContext(
                eyePos, eyePos.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 dest = clip.getLocation();
        if (clip.getType() != HitResult.Type.MISS) {
            // step off the hit face so we do not clip into the wall
            dest = dest.subtract(look.scale(0.6));
        }

        Vec3 safe = findSafeSpot(level, player, dest, look);
        if (safe == null) {
            // nowhere to land - cooldown NOT consumed, key stays retryable
            return;
        }
        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;

        Vec3 oldPos = player.position();
        player.teleportTo(safe.x, safe.y, safe.z);
        player.fallDistance = 0;

        // backstab whatever stands at the landing point
        AABB landing = new AABB(
                safe.x - 1.5, safe.y - 1.0, safe.z - 1.5,
                safe.x + 1.5, safe.y + 2.0, safe.z + 1.5);
        LivingEntity victim = level.getEntitiesOfClass(LivingEntity.class, landing,
                e -> e != player && e.isAlive()).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(safe.x, safe.y, safe.z)))
                .orElse(null);
        if (victim != null) {
            victim.hurt(level.damageSources().playerAttack(player), damage);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false, true));
            if (weaknessDuration > 0) {
                // weaknessDuration is already in ticks (parsed from seconds)
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weaknessDuration, 0, false, false, true));
            }
            // darknessDuration arrives in ticks (parsed from seconds in JSON)
            if (darknessDuration > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, darknessDuration, 0, false, false, true));
            }
        }

        // Shadow particles at old and new positions
        level.sendParticles(ParticleTypes.SMOKE,
                oldPos.x, oldPos.y + 1, oldPos.z, 8, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.8f);

        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
    }

    /**
     * Finds a free standing spot near {@code dest}: tries the point itself,
     * a short drop, a step up and a small step back. Returns null when
     * nothing fits.
     */
    private static Vec3 findSafeSpot(ServerLevel level, ServerPlayer player, Vec3 dest, Vec3 look) {
        double r = player.getBbWidth() / 2.0;
        double h = player.getBbHeight();
        Vec3[] probes = new Vec3[]{
                dest,
                dest.add(0, 1, 0),
                dest.subtract(look.scale(1.0)),
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
