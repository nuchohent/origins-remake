package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Passive power: modifies the player's block and entity interaction range.
 * <p>
 * The {@code reach_multiplier} is applied to both the vanilla block interaction
 * range (4.5) and entity interaction range (3.0).  For example, 0.5 = half reach,
 * 2.0 = double reach.
 */
public class ReachPower implements Power {

    private static final double BASE_BLOCK_REACH = 4.5;
    private static final double BASE_ENTITY_REACH = 3.0;

    private final Identifier id;
    private final int difficulty;
    private final double reachMultiplier;

    public ReachPower(Identifier id, int difficulty, double reachMultiplier) {
        this.id = id;
        this.difficulty = difficulty;
        this.reachMultiplier = reachMultiplier;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.reach.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.reach.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return false; }

    @Override
    public void onAttach(ServerPlayer player) {
        applyReach(player, Attributes.BLOCK_INTERACTION_RANGE, BASE_BLOCK_REACH);
        applyReach(player, Attributes.ENTITY_INTERACTION_RANGE, BASE_ENTITY_REACH);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        removeReach(player, Attributes.BLOCK_INTERACTION_RANGE);
        removeReach(player, Attributes.ENTITY_INTERACTION_RANGE);
    }

    private void applyReach(ServerPlayer player, Holder<Attribute> attribute, double baseValue) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        double target = baseValue * reachMultiplier;
        double diff = target - baseValue;
        if (Math.abs(diff) < 0.001) return;
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, diff, AttributeModifier.Operation.ADD_VALUE));
    }

    private void removeReach(ServerPlayer player, Holder<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
