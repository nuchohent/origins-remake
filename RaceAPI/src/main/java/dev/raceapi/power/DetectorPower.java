package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Marks nearby creatures with glowing, letting the player spot hidden mobs
 * through walls. Applying glowing to others passively is not possible in vanilla.
 */
public class DetectorPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double radius;

    public DetectorPower(Identifier id, int difficulty) {
        this(id, difficulty, 6.0);
    }

    public DetectorPower(Identifier id, int difficulty, double radius) {
        this.id = id;
        this.difficulty = difficulty;
        this.radius = radius;
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
        if (player.tickCount % 10 != 0) {
            return;
        }
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive())) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        }
    }
}
