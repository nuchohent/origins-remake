package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OverdrivePower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final float selfDamagePerTick;
    private final double auraRadius;
    private final float auraDamage;
    private final int auraFireTicks;
    private final Map<UUID, Boolean> enabled = new HashMap<>();

    public OverdrivePower(Identifier id, int difficulty,
                          float selfDamagePerTick, double auraRadius,
                          float auraDamage, int auraFireTicks) {
        this.id = id;
        this.difficulty = difficulty;
        this.selfDamagePerTick = selfDamagePerTick;
        this.auraRadius = auraRadius;
        this.auraDamage = auraDamage;
        this.auraFireTicks = auraFireTicks;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.overdrive.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.overdrive.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        boolean current = enabled.getOrDefault(player.getUUID(), false);
        enabled.put(player.getUUID(), !current);
        ServerLevel level = RaceUtils.serverLevel(player);
        if (!current) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED,
                    MobEffectInstance.INFINITE_DURATION, 1, false, false, true));
            // HUD indicator instead of a chat line
            dev.raceapi.network.PowerStatePayload.send(player, id.toString(), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.5f);
        } else {
            player.removeEffect(MobEffects.SPEED);
            dev.raceapi.network.PowerStatePayload.send(player, id.toString(), false);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!enabled.getOrDefault(player.getUUID(), false)) return;
        ServerLevel level = RaceUtils.serverLevel(player);

        if (!player.hasEffect(MobEffects.SPEED)) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED,
                    MobEffectInstance.INFINITE_DURATION, 1, false, false, true));
        }

        if (level.getGameTime() % 20 == 0) {
            player.hurt(level.damageSources().magic(), selfDamagePerTick);
            AABB auraBox = player.getBoundingBox().inflate(auraRadius);
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, auraBox,
                    e -> e != player && e.isAlive());
            for (LivingEntity e : nearby) {
                if (auraFireTicks > 0) {
                    e.igniteForSeconds(auraFireTicks / 20.0f);
                }
                e.hurt(level.damageSources().magic(), auraDamage);
            }
        }

        if (level.getGameTime() % 2 == 0) {
            double dx = level.getRandom().nextDouble() - 0.5;
            double dy = level.getRandom().nextDouble() - 0.5;
            double dz = level.getRandom().nextDouble() - 0.5;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.0001) len = 1.0;
            level.sendParticles(ParticleTypes.FLAME,
                    player.getX() + dx / len * auraRadius,
                    player.getY() + 1.0 + dy / len * auraRadius,
                    player.getZ() + dz / len * auraRadius,
                    1, 0, 0, 0, 0.01);
        }
    }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        return amount;
    }

    public boolean isActive(ServerPlayer player) {
        return enabled.getOrDefault(player.getUUID(), false);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        enabled.remove(player.getUUID());
        player.removeEffect(MobEffects.SPEED);
        dev.raceapi.network.PowerStatePayload.send(player, id.toString(), false);
    }
}
