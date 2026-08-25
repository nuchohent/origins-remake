package dev.raceapi.race;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A single ability or weakness granted by a {@link Race}.
 * <p>
 * Powers run on the server thread. A power with a negative {@link #getDifficulty()}
 * is a weakness and is rendered in red by the GUI; positive ones in green.
 */
public interface Power {

    /**
     * If this power decorates another power (e.g. {@code NamedPower} wrapping a
     * concrete power type), returns the underlying power; otherwise returns
     * {@code this}. Implementations that wrap a delegate must return
     * {@code delegate.getWrapped()} so a single call fully unwraps a chain.
     */
    default Power getWrapped() {
        return this;
    }

    Identifier getId();

    Component getDisplayName();

    Component getDescription();

    /**
     * Contribution to race difficulty. Negative = weakness, positive = strength.
     */
    default int getDifficulty() {
        return 1;
    }

    /** Hide from the GUI (utility/technical powers). */
    default boolean isHidden() {
        return false;
    }

    /** Whether this power is triggered by a player keybind. */
    default boolean hasBinding() {
        return false;
    }

    /** Total cooldown in ticks of an active power (0 = no cooldown). */
    default int getCooldownTicks() {
        return 0;
    }

    /**
     * Race resource (mana/stamina) units consumed on activation. 0 = free.
     * Checked by the keybind pipeline before {@link #onKeyPressed}; if the
     * player has less than this, the activation is denied.
     */
    default double getResourceCost() {
        return 0;
    }

    /**
     * Player-aware cost variant: wrappers may suppress or alter the cost based
     * on live state (e.g. a {@code ConditionalPower} charges nothing while its
     * condition is false). The keybind pipeline calls this one.
     */
    default double getResourceCost(ServerPlayer player) {
        return getResourceCost();
    }

    /**
     * The explicitly assigned keybind slot for this active power, or -1 if the
     * slot should be auto-assigned from the power's order in the race.
     */
    default int getBindSlot() {
        return -1;
    }

    /** Remaining cooldown ticks for this player (0 = ready). */
    default int getRemainingCooldownTicks(ServerPlayer player) {
        return 0;
    }

    default void onAttach(ServerPlayer player) {
    }

    default void onRemove(ServerPlayer player) {
    }

    /** Called once per server tick while the race is active. */
    default void onTick(ServerPlayer player) {
    }

    /** Called when the player presses this power's bound key. */
    default void onKeyPressed(ServerPlayer player) {
    }

    /** Called when the player jumps. */
    default void onJump(ServerPlayer player) {
    }

    /** Called when this player deals melee damage to a living target. */
    default void onAttack(ServerPlayer player, LivingEntity target, float damage) {
    }

    /**
     * Called when the player lands from a fall. Return the new fall distance;
     * returning 0 negates fall damage.
     */
    default float onFall(ServerPlayer player, float fallDistance) {
        return fallDistance;
    }

    /** Called when the player breaks a block. */
    default void onBreakBlock(ServerPlayer player, BlockPos pos, BlockState state) {
    }

    /**
     * Called when the player mines a block. Return the modified break speed
     * (1.0 = normal); used to make mining faster or slower conditionally.
     */
    default float onBreakSpeed(ServerPlayer player, float speed) {
        return speed;
    }

    /**
     * Called when the player is about to take damage. Return the modified damage;
     * returning 0 cancels the damage.
     */
    default float onHurt(ServerPlayer player, DamageSource source, float amount) {
        return amount;
    }

    /** Called when the player right-clicks a block (after vanilla block use). */
    default void onInteractBlock(ServerPlayer player, InteractionHand hand, BlockPos pos, BlockHitResult hitResult) {
    }

    /** Called when the player right-clicks an entity (after vanilla entity interaction). */
    default void onInteractEntity(ServerPlayer player, InteractionHand hand, Entity target) {
    }

    /** Called when this player kills a living entity (after the death is applied). */
    default void onKill(ServerPlayer player, LivingEntity victim) {
    }

    /** Called when the player finishes eating a food item. */
    default void onEat(ServerPlayer player) {
    }
}
