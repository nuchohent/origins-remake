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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bouncy landing: the harder the fall, the higher the player bounces back up,
 * and fall damage is cancelled. Each bounce in a chain is weaker than the last,
 * so the player settles quickly instead of bouncing forever (like a slime block).
 */
public class BouncyPower implements Power {

    private static final double MAX_BOUNCE = 1.2;
    private static final double BASE_BOUNCE = 0.4;
    private static final double BOUNCE_FACTOR = 0.12;
    private static final double DECAY = 0.55;
    private static final double MIN_BOUNCE = 0.15;

    private final Identifier id;
    private final int difficulty;
    private final Map<UUID, Double> chain = new HashMap<>();
    private final Map<UUID, Integer> groundTicks = new HashMap<>();

    public BouncyPower(Identifier id, int difficulty) {
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
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (player.onGround()) {
            int ticks = groundTicks.getOrDefault(uuid, 0) + 1;
            groundTicks.put(uuid, ticks);
            if (ticks > 15) {
                chain.remove(uuid);
            }
        } else {
            groundTicks.remove(uuid);
        }
    }

    @Override
    public float onFall(ServerPlayer player, float fallDistance) {
        if (fallDistance > 2.0f && !player.isInWater()) {
            UUID uuid = player.getUUID();
            double factor = chain.getOrDefault(uuid, 1.0);
            double bounce = Math.min(MAX_BOUNCE, BASE_BOUNCE + fallDistance * BOUNCE_FACTOR) * factor;
            if (bounce >= MIN_BOUNCE) {
                player.setDeltaMovement(player.getDeltaMovement().x, bounce, player.getDeltaMovement().z);
                player.hurtMarked = true;
                chain.put(uuid, factor * DECAY);
                ServerLevel level = RaceUtils.serverLevel(player);
                if (level != null) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 0.9F, 0.8F);
                    level.sendParticles(ParticleTypes.ITEM_SLIME,
                            player.getX(), player.getY(), player.getZ(), 14, 0.4, 0.2, 0.4, 0.05);
                }
            } else {
                chain.remove(uuid);
            }
            return 0.0f;
        }
        return fallDistance;
    }

    @Override
    public void onRemove(ServerPlayer player) {
        chain.remove(player.getUUID());
        groundTicks.remove(player.getUUID());
    }
}
