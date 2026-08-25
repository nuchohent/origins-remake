package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Lets the player mine at full speed while underwater. In 26.2 the vanilla
 * underwater mining penalty is the {@code player.submerged_mining_speed}
 * attribute (default 0.2, i.e. 5x slower); it is applied whenever the eyes are
 * in water, and the vanilla Aqua Affinity enchantment is just another +400%
 * modifier on the same attribute.
 * <p>
 * Instead of adding another stacked multiplier (which would push the total
 * above 1.0 and make underwater mining faster than dry-land mining), this
 * power compensates in {@code onBreakSpeed}: whatever part of the penalty is
 * still left after enchants gets multiplied away, exactly up to full speed.
 */
public class AquaHastePower implements Power {

    /**
     * Vanilla {@code Player#getDigSpeed} divides by 5.0 whenever the player is
     * not on the ground, which also applies while swimming/floating underwater.
     * This compensates the floating penalty while submerged.
     */
    private static final float NOT_ON_GROUND_PENALTY = 5.0f;

    private final Identifier id;
    private final int difficulty;

    public AquaHastePower(Identifier id, int difficulty) {
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
    public float onBreakSpeed(ServerPlayer player, float speed) {
        if (!player.isEyeInFluid(FluidTags.WATER)) {
            return speed;
        }
        AttributeInstance instance = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        double multiplier = instance != null ? instance.getValue() : 0.2;
        // cancel only the remaining part of the penalty: with Aqua Affinity
        // (or any other source pushing the attribute to >= 1.0) this is a no-op
        if (multiplier < 0.999) {
            speed *= (float) (1.0 / multiplier);
        }
        if (!player.onGround()) {
            speed *= NOT_ON_GROUND_PENALTY;
        }
        return speed;
    }
}
