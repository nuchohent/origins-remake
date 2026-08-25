package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.food.FoodData;

public class InverseRegenerationPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final float damagePerTick;
    private final int damageTickInterval;
    private final int foodThreshold;
    private final float regenPunishment;

    public InverseRegenerationPower(Identifier id, int difficulty,
                                    float damagePerTick, int damageTickInterval,
                                    int foodThreshold, float regenPunishment) {
        this.id = id;
        this.difficulty = difficulty;
        this.damagePerTick = damagePerTick;
        this.damageTickInterval = damageTickInterval;
        this.foodThreshold = foodThreshold;
        this.regenPunishment = regenPunishment;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.inverse_regeneration.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.inverse_regeneration.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    @Override
    public void onTick(ServerPlayer player) {
        if (player.tickCount % 5 != 0) return;
        FoodData foodData = player.getFoodData();
        if (foodData.getSaturationLevel() > 0 && player.getHealth() < player.getMaxHealth()) {
            if (player.tickCount % damageTickInterval == 0 && foodData.getFoodLevel() >= foodThreshold) {
                player.hurt(player.damageSources().magic(), damagePerTick);
            }
        }
    }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
            player.hurt(player.damageSources().magic(), amount * regenPunishment);
            return 0;
        }
        return amount;
    }
}
