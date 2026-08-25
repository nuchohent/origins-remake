package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Heals the player for a portion of the melee damage they deal.
 */
public class LifestealPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double fraction;

    public LifestealPower(Identifier id, int difficulty) {
        this(id, difficulty, 0.25);
    }

    public LifestealPower(Identifier id, int difficulty, double fraction) {
        this.id = id;
        this.difficulty = difficulty;
        this.fraction = fraction;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.lifesteal.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.lifesteal.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onAttack(ServerPlayer player, LivingEntity target, float damage) {
        player.heal((float) (damage * fraction));
    }
}
