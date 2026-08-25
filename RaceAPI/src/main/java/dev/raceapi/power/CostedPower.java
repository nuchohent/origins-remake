package dev.raceapi.power;

import dev.raceapi.race.Power;
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
 * Decorator that adds a resource (mana/stamina) cost to an active power.
 * Applied by {@link PowerFactory} when a JSON definition carries a positive
 * {@code cost} field; the keybind pipeline checks it via
 * {@link #getResourceCost()} before delegating {@link #onKeyPressed}.
 * <p>
 * Unlike {@link NamedPower}, {@link #getWrapped()} skips straight through this
 * decorator: the cost stays readable on the wrapper returned by the first
 * {@code getWrapped()} call, while type checks for inner power types keep
 * working.
 */
public final class CostedPower implements Power {

    private final Power delegate;
    private final double cost;

    public CostedPower(Power delegate, double cost) {
        this.delegate = delegate;
        this.cost = cost;
    }

    public double cost() {
        return cost;
    }

    @Override
    public Power getWrapped() {
        return delegate.getWrapped();
    }

    @Override
    public double getResourceCost() {
        return cost;
    }

    @Override
    public Identifier getId() {
        return delegate.getId();
    }

    @Override
    public Component getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    @Override
    public int getDifficulty() {
        return delegate.getDifficulty();
    }

    @Override
    public boolean isHidden() {
        return delegate.isHidden();
    }

    @Override
    public boolean hasBinding() {
        return delegate.hasBinding();
    }

    @Override
    public int getCooldownTicks() {
        return delegate.getCooldownTicks();
    }

    @Override
    public int getBindSlot() {
        return delegate.getBindSlot();
    }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return delegate.getRemainingCooldownTicks(player);
    }

    @Override
    public void onAttach(ServerPlayer player) {
        delegate.onAttach(player);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        delegate.onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        delegate.onTick(player);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        delegate.onKeyPressed(player);
    }

    @Override
    public void onJump(ServerPlayer player) {
        delegate.onJump(player);
    }

    @Override
    public void onAttack(ServerPlayer player, LivingEntity target, float damage) {
        delegate.onAttack(player, target, damage);
    }

    @Override
    public float onFall(ServerPlayer player, float fallDistance) {
        return delegate.onFall(player, fallDistance);
    }

    @Override
    public void onBreakBlock(ServerPlayer player, BlockPos pos, BlockState state) {
        delegate.onBreakBlock(player, pos, state);
    }

    @Override
    public float onBreakSpeed(ServerPlayer player, float speed) {
        return delegate.onBreakSpeed(player, speed);
    }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        return delegate.onHurt(player, source, amount);
    }

    @Override
    public void onInteractBlock(ServerPlayer player, InteractionHand hand, BlockPos pos, BlockHitResult hitResult) {
        delegate.onInteractBlock(player, hand, pos, hitResult);
    }

    @Override
    public void onInteractEntity(ServerPlayer player, InteractionHand hand, Entity target) {
        delegate.onInteractEntity(player, hand, target);
    }

    @Override
    public void onKill(ServerPlayer player, LivingEntity victim) {
        delegate.onKill(player, victim);
    }

    @Override
    public void onEat(ServerPlayer player) {
        delegate.onEat(player);
    }
}
