package dev.raceapi.power;

import dev.raceapi.player.Resources;
import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Passive reward on eating any food: heals the player and optionally grants
 * race resource. JSON type {@code on_eat}.
 */
public final class EatRewardPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double heal;
    private final double resourceGain;

    public EatRewardPower(Identifier id, int difficulty, double heal, double resourceGain) {
        this.id = id;
        this.difficulty = difficulty;
        this.heal = Math.max(0.0, heal);
        this.resourceGain = Math.max(0.0, resourceGain);
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.on_eat.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.on_eat.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onEat(ServerPlayer player) {
        if (heal > 0) {
            player.heal((float) heal);
        }
        if (resourceGain > 0 && Resources.hasResource(player)) {
            Resources.add(player, resourceGain);
        }
    }
}
