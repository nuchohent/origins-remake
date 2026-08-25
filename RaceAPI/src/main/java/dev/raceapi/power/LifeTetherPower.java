package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class LifeTetherPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double range;
    private final float healthLossFraction;
    private final int slownessDuration;
    private final int slownessAmplifier;

    public LifeTetherPower(Identifier id, int difficulty, double range,
                           float healthLossFraction, int slownessDuration, int slownessAmplifier) {
        this.id = id;
        this.difficulty = difficulty;
        this.range = range;
        this.healthLossFraction = healthLossFraction;
        this.slownessDuration = slownessDuration;
        this.slownessAmplifier = slownessAmplifier;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.life_tether.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.life_tether.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    @Override
    public void onTick(ServerPlayer player) {
    }

    public void onNearbyPlayerDeath(ServerPlayer victim, ServerPlayer tether) {
        double distance = victim.distanceTo(tether);
        if (distance > range || victim == tether) {
            return;
        }
        float healthLoss = tether.getHealth() * healthLossFraction;
        tether.hurt(tether.damageSources().magic(), healthLoss);
        tether.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.SLOWNESS, slownessDuration, slownessAmplifier, false, false, true));
        ServerLevel level = RaceUtils.serverLevel(tether);
        if (level != null) {
            level.sendParticles(ParticleTypes.SOUL,
                    tether.getX(), tether.getY() + 1, tether.getZ(),
                    20, 0.5, 0.5, 0.5, 0.01);
        }
    }
}
