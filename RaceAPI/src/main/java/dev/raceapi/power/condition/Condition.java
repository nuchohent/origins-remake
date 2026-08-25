package dev.raceapi.power.condition;

import net.minecraft.server.level.ServerPlayer;

/**
 * A predicate that tests whether a condition is met for a given player.
 * Used by {@link dev.raceapi.power.ConditionalPower} to activate/deactivate inner powers.
 */
@FunctionalInterface
public interface Condition {
    boolean test(ServerPlayer player);
}
