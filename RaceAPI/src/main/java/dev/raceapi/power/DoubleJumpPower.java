package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.network.CooldownPayload;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Active power that gives a second jump while the player is mid-air, with a
 * short cooldown. The extra jump also resets the fall distance so it can be
 * used to survive a long drop.
 */
public class DoubleJumpPower implements Power {

    private static final int DEFAULT_COOLDOWN = 40;

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double boost;

    public DoubleJumpPower(Identifier id, int difficulty) {
        this(id, difficulty, DEFAULT_COOLDOWN, 0.7);
    }

    public DoubleJumpPower(Identifier id, int difficulty, int cooldownTicks, double boost) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.boost = boost;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.double_jump.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.double_jump.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public boolean hasBinding() {
        return true;
    }

    @Override
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (player.onGround() || player.isInWater() || player.isInLava()) {
            return;
        }
        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) {
            return;
        }

        player.fallDistance = 0.0f;
        player.setDeltaMovement(player.getDeltaMovement().x, boost, player.getDeltaMovement().z);
        player.hurtMarked = true;

        RaceUtils.serverLevel(player).playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 0.5F, 1.2F);
        RaceUtils.serverLevel(player).sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY(), player.getZ(), 12, 0.4, 0.1, 0.4, 0.02);

        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
    }
}
