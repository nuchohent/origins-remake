package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class DirectionalExposurePower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final float multiplier;
    private final double rearAngle;

    public DirectionalExposurePower(Identifier id, int difficulty, float multiplier, double rearAngle) {
        this.id = id;
        this.difficulty = difficulty;
        this.multiplier = multiplier;
        this.rearAngle = rearAngle;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.directional_exposure.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.directional_exposure.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker == null || attacker == player) {
            return amount;
        }
        Vec3 playerLook = player.getLookAngle().normalize();
        Vec3 attackDirection = attacker.position().subtract(player.position()).normalize();
        double dot = playerLook.dot(attackDirection);

        if (dot < -rearAngle) {
            return amount * multiplier;
        }
        return amount;
    }
}
