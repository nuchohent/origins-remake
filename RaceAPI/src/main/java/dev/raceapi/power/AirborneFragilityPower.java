package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class AirborneFragilityPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double damageMultiplier;
    private final int jumpDisableTicks;

    public AirborneFragilityPower(Identifier id, int difficulty, double damageMultiplier, int jumpDisableTicks) {
        this.id = id;
        this.difficulty = difficulty;
        this.damageMultiplier = damageMultiplier;
        this.jumpDisableTicks = jumpDisableTicks;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.airborne_fragility.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.airborne_fragility.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        if (!player.onGround()) {
            amount = (float) (amount * damageMultiplier);
            player.setDeltaMovement(player.getDeltaMovement().x * 0.1, -1.5, player.getDeltaMovement().z * 0.1);
            player.hurtMarked = true;
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, jumpDisableTicks, -1, false, false, true));
        }
        return amount;
    }
}
