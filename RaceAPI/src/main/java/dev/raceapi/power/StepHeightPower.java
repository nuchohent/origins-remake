package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Raises the player's step height via the {@code minecraft:step_height}
 * attribute, letting them walk up full blocks without jumping. The bonus is
 * removed while crouching so sneaking players don't step up blocks.
 */
public class StepHeightPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double amount;
    private final Map<UUID, Boolean> crouched = new HashMap<>();

    public StepHeightPower(Identifier id, int difficulty) {
        this(id, difficulty, 1.0);
    }

    public StepHeightPower(Identifier id, int difficulty, double amount) {
        this.id = id;
        this.difficulty = difficulty;
        this.amount = amount;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.step_height.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.step_height.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onAttach(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.STEP_HEIGHT);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean sneaking = player.isCrouching();
        Boolean previous = crouched.get(uuid);
        if (previous != null && previous == sneaking) {
            return;
        }
        crouched.put(uuid, sneaking);
        AttributeInstance instance = player.getAttribute(Attributes.STEP_HEIGHT);
        if (instance == null) {
            return;
        }
        if (sneaking) {
            instance.removeModifier(id);
        } else if (!instance.hasModifier(id)) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.STEP_HEIGHT);
        if (instance != null) {
            instance.removeModifier(id);
        }
        crouched.remove(player.getUUID());
    }
}
