package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;

public class DensityAnchorPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double fallMultiplier;
    private final double sinkSpeed;

    public DensityAnchorPower(Identifier id, int difficulty, double fallMultiplier, double sinkSpeed) {
        this.id = id;
        this.difficulty = difficulty;
        this.fallMultiplier = fallMultiplier;
        this.sinkSpeed = sinkSpeed;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.density_anchor.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.density_anchor.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    @Override
    public void onTick(ServerPlayer player) {
        // water sinking is applied CLIENT-side (applyClient) - the local
        // player's swim motion is client-authoritative
    }

    /** Client tick: heavy body sinks in water/lava instead of floating. */
    public void applyClient(net.minecraft.world.entity.player.Player player) {
        if (player.isInWater() || player.isInLava()) {
            Vec3 motion = player.getDeltaMovement();
            if (motion.y > sinkSpeed) {
                player.setDeltaMovement(motion.x * 0.8, sinkSpeed, motion.z * 0.8);
            }
        }
    }

    @Override
    public float onFall(ServerPlayer player, float fallDistance) {
        return (float) (fallDistance * fallMultiplier);
    }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        if (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.ARROW)
                || source.is(DamageTypes.TRIDENT) || source.is(DamageTypes.EXPLOSION)
                || source.is(DamageTypes.PLAYER_EXPLOSION) || source.is(DamageTypes.WITHER)
                || source.is(DamageTypes.DRAGON_BREATH)) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        }
        return amount;
    }
}
