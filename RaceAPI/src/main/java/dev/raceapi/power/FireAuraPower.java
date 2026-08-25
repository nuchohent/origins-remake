package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Passive power that ignites nearby creatures while the race is active.
 */
public class FireAuraPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double radius;
    private final int fireTicks;

    public FireAuraPower(Identifier id, int difficulty) {
        this(id, difficulty, 3.0, 60);
    }

    public FireAuraPower(Identifier id, int difficulty, double radius, int fireTicks) {
        this.id = id;
        this.difficulty = difficulty;
        this.radius = radius;
        this.fireTicks = fireTicks;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.fire_aura.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.fire_aura.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onTick(ServerPlayer player) {
        ServerLevel level = RaceUtils.serverLevel(player);
        if (level == null) {
            return;
        }
        if (player.tickCount % 10 == 0) {
            AABB searchBox = new AABB(
                    player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                    player.getX() + radius, player.getY() + 2.0 + radius, player.getZ() + radius);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    searchBox, e -> e.isAlive() && e != player)) {
                entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), fireTicks));
            }
        }
        if (player.tickCount % 4 == 0) {
            var random = level.getRandom();
            for (int i = 0; i < 2; i++) {
                double px = player.getX() + (random.nextDouble() - 0.5) * radius;
                double py = player.getY() + random.nextDouble() * 2.0;
                double pz = player.getZ() + (random.nextDouble() - 0.5) * radius;
                level.sendParticles(ParticleTypes.FLAME, px, py, pz,
                        1, 0.0, 0.02, 0.0, 0.01);
            }
        }
    }
}
