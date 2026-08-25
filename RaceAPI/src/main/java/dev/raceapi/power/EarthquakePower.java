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
 * Active power: AoE earthquake slam that damages nearby enemies, applies
 * Slowness and launches them upward. Deals moderate self-damage to the caster.
 */
public class EarthquakePower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double radius;
    private final float damage;
    private final float selfDamage;
    private final double knockupStrength;
    private final int slownessDuration;
    private final int slownessAmplifier;

    public EarthquakePower(Identifier id, int difficulty, int cooldownTicks,
                           double radius, float damage, float selfDamage,
                           double knockupStrength, int slownessDuration, int slownessAmplifier) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.radius = radius;
        this.damage = damage;
        this.selfDamage = selfDamage;
        this.knockupStrength = knockupStrength;
        this.slownessDuration = slownessDuration;
        this.slownessAmplifier = slownessAmplifier;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.earthquake.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.earthquake.desc"); }
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

        ServerLevel level = RaceUtils.serverLevel(player);
        Vec3 pos = player.position();

        AABB box = new AABB(pos.x - radius, pos.y - 2, pos.z - radius,
                pos.x + radius, pos.y + 4, pos.z + radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            double dist = target.distanceTo(player);
            float falloff = Math.max(0, 1.0f - (float)(dist / radius));

            target.hurt(level.damageSources().playerAttack(player), damage * falloff);
            target.push(0, knockupStrength * falloff, 0);
            target.hurtMarked = true;

            if (slownessDuration > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        slownessDuration * 20, slownessAmplifier, false, false, true));
            }
        }

        // Self damage
        if (selfDamage > 0) {
            player.hurt(level.damageSources().generic(), selfDamage);
        }

        // Visual & sound effects
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                pos.x, pos.y + 0.2, pos.z, 20, radius * 0.5, 0.5, radius * 0.5, 0.02);
        level.sendParticles(ParticleTypes.DUST_PLUME,
                pos.x, pos.y + 0.5, pos.z, 15, radius * 0.4, 0.3, radius * 0.4, 0.04);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 0.5f);

        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
    }
}
