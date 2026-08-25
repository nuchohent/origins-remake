package dev.raceapi.power;

import dev.raceapi.power.condition.Condition;
import dev.raceapi.race.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A power wrapper that activates an inner power when a condition is true,
 * and optionally activates a {@code negatePower} when the condition is false.
 * <p>
 * This enables conditional race abilities such as:
 * <ul>
 *   <li>Water breathing while underwater, slowness on land</li>
 *   <li>Strength at night, weakness during the day</li>
 *   <li>Fire resistance in the Nether, vulnerability in the Overworld</li>
 *   <li>Regeneration when low on health</li>
 * </ul>
 * <p>
 * On each server tick, the condition is evaluated. If true, the inner power's
 * {@code onTick} is called. If false and a negate power is provided, the negate
 * power's {@code onTick} is called instead.
 */
public class ConditionalPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final Condition condition;
    private final Power innerPower;
    private final Power negatePower;
    private final Component conditionLabel;
    private final java.util.Map<java.util.UUID, Boolean> lastConditionState = new java.util.HashMap<>();

    public ConditionalPower(Identifier id, int difficulty, Condition condition,
                            Power innerPower, Power negatePower, Component conditionLabel) {
        this.id = id;
        this.difficulty = difficulty;
        this.condition = condition;
        this.innerPower = innerPower;
        this.negatePower = negatePower;
        this.conditionLabel = conditionLabel;
    }

    @Override
    public Power getWrapped() {
        return innerPower.getWrapped();
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.conditional.name",
                conditionLabel, innerPower.getDisplayName());
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.conditional.desc",
                conditionLabel, innerPower.getDisplayName());
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public boolean isHidden() {
        return innerPower.isHidden();
    }

    @Override
    public boolean hasBinding() {
        return innerPower.hasBinding();
    }

    @Override
    public int getCooldownTicks() {
        return innerPower.getCooldownTicks();
    }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return innerPower.getRemainingCooldownTicks(player);
    }

    /** Active inner powers must stay activatable through the condition wrapper. */
    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (condition.test(player)) {
            innerPower.onKeyPressed(player);
        }
    }

    @Override
    public double getResourceCost() {
        return innerPower.getResourceCost();
    }

    @Override
    public double getResourceCost(ServerPlayer player) {
        // pressing the key while the condition is false is a guaranteed no-op:
        // charge nothing so the pool isn't drained by dead presses
        return condition.test(player) ? innerPower.getResourceCost(player) : 0;
    }

    @Override
    public int getBindSlot() {
        return innerPower.getBindSlot();
    }

    @Override
    public void onTick(ServerPlayer player) {
        boolean now = condition.test(player);
        boolean before = lastConditionState.getOrDefault(player.getUUID(), false);
        if (now != before) {
            if (now) {
                innerPower.onAttach(player);
                if (negatePower != null) {
                    negatePower.onRemove(player);
                }
            } else {
                innerPower.onRemove(player);
                if (negatePower != null) {
                    negatePower.onAttach(player);
                }
            }
            lastConditionState.put(player.getUUID(), now);
        }
        if (now) {
            innerPower.onTick(player);
        } else if (negatePower != null) {
            negatePower.onTick(player);
        }
    }

    @Override
    public void onJump(ServerPlayer player) {
        if (condition.test(player)) {
            innerPower.onJump(player);
        }
    }

    @Override
    public void onAttack(ServerPlayer player, net.minecraft.world.entity.LivingEntity target, float damage) {
        if (condition.test(player)) {
            innerPower.onAttack(player, target, damage);
        }
    }

    @Override
    public float onFall(ServerPlayer player, float fallDistance) {
        if (condition.test(player)) {
            return innerPower.onFall(player, fallDistance);
        }
        return fallDistance;
    }

    @Override
    public float onHurt(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (condition.test(player)) {
            return innerPower.onHurt(player, source, amount);
        }
        return amount;
    }

    @Override
    public void onBreakBlock(ServerPlayer player, net.minecraft.core.BlockPos pos,
                             net.minecraft.world.level.block.state.BlockState state) {
        if (condition.test(player)) {
            innerPower.onBreakBlock(player, pos, state);
        }
    }

    @Override
    public float onBreakSpeed(ServerPlayer player, float speed) {
        if (condition.test(player)) {
            return innerPower.onBreakSpeed(player, speed);
        }
        return speed;
    }

    @Override
    public void onInteractBlock(ServerPlayer player, InteractionHand hand, BlockPos pos, BlockHitResult hitResult) {
        if (condition.test(player)) {
            innerPower.onInteractBlock(player, hand, pos, hitResult);
        }
    }

    @Override
    public void onInteractEntity(ServerPlayer player, InteractionHand hand, Entity target) {
        if (condition.test(player)) {
            innerPower.onInteractEntity(player, hand, target);
        }
    }

    @Override
    public void onKill(ServerPlayer player, LivingEntity victim) {
        if (condition.test(player)) {
            innerPower.onKill(player, victim);
        }
    }

    @Override
    public void onEat(ServerPlayer player) {
        if (condition.test(player)) {
            innerPower.onEat(player);
        }
    }

    @Override
    public void onAttach(ServerPlayer player) {
        boolean state = condition.test(player);
        lastConditionState.put(player.getUUID(), state);
        if (state) {
            innerPower.onAttach(player);
        } else if (negatePower != null) {
            negatePower.onAttach(player);
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        if (lastConditionState.getOrDefault(player.getUUID(), false)) {
            innerPower.onRemove(player);
        } else {
            if (negatePower != null) {
                negatePower.onRemove(player);
            }
        }
        lastConditionState.remove(player.getUUID());
    }
}
