package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ThermalShockPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int shockCooldownTicks;
    private final int abilityDisableTicks;
    private final float shockDamage;
    private final Map<UUID, ShockState> states = new HashMap<>();

    public ThermalShockPower(Identifier id, int difficulty,
                             int shockCooldownTicks, int abilityDisableTicks, float shockDamage) {
        this.id = id;
        this.difficulty = difficulty;
        this.shockCooldownTicks = shockCooldownTicks;
        this.abilityDisableTicks = abilityDisableTicks;
        this.shockDamage = shockDamage;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.thermal_shock.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.thermal_shock.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    @Override
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ShockState state = states.computeIfAbsent(uuid, k -> new ShockState());

        boolean inWater = player.isInWater();
        boolean inFire = player.isOnFire();

        boolean shock = false;
        if (state.wasInWater && inFire) shock = true;
        else if (state.wasInFire && inWater) shock = true;

        state.wasInWater = inWater;
        state.wasInFire = inFire;

        if (shock && player.tickCount - state.lastShockTick >= shockCooldownTicks) {
            state.lastShockTick = player.tickCount;
            player.hurt(player.damageSources().magic(), shockDamage);
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, abilityDisableTicks, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, abilityDisableTicks, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, abilityDisableTicks, 1, false, false, true));
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        states.remove(player.getUUID());
    }

    private static class ShockState {
        boolean wasInWater;
        boolean wasInFire;
        int lastShockTick = -100;
    }
}
