package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Melee hits knock the target back with extra force. Knockback on every hit
 * cannot be granted by any vanilla effect or attribute.
 */
public class HeavyHitterPower implements Power {

    private final Identifier id;
    private final int difficulty;

    public HeavyHitterPower(Identifier id, int difficulty) {
        this.id = id;
        this.difficulty = difficulty;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power." + id.getNamespace() + "." + id.getPath() + ".desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onAttack(ServerPlayer player, LivingEntity target, float damage) {
        if (target != player && target.isAlive()) {
            target.knockback(0.9, player.getX() - target.getX(), player.getZ() - target.getZ(),
                    player.damageSources().playerAttack(player), damage, false);
        }
    }
}
