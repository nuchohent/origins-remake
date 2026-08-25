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
import org.jetbrains.annotations.Nullable;

/**
 * Decorates a {@link Power} with a fixed display name (and optionally a
 * description). Used by JSON power definitions that supply a {@code display_name}
 * (or {@code description}) field, so datapack creators don't have to ship
 * translation files for every power.
 */
public final class NamedPower implements Power {

    private final Power delegate;
    private final Component displayName;
    @Nullable
    private final Component description;
    private final int bindSlot;

    public NamedPower(Power delegate, Component displayName, @Nullable Component description) {
        this(delegate, displayName, description, -1);
    }

    public NamedPower(Power delegate, Component displayName, @Nullable Component description, int bindSlot) {
        this.delegate = delegate;
        this.displayName = displayName;
        this.description = description;
        this.bindSlot = bindSlot;
    }

    /**
     * Unwraps any {@link NamedPower} decorators so the underlying power type
     * can be inspected directly (e.g. with {@code instanceof}). JSON powers
     * that carry a {@code display_name}/{@code description}/{@code bind_slot}
     * are always wrapped, so type checks must go through this method or they
     * silently fail.
     *
     * @deprecated use {@link Power#getWrapped()} instead
     */
    @Deprecated
    public static Power unwrap(Power power) {
        return power.getWrapped();
    }

    @Override
    public Power getWrapped() {
        return delegate.getWrapped();
    }

    @Override
    public Identifier getId() {
        return delegate.getId();
    }

    @Override
    public Component getDisplayName() {
        return displayName;
    }

    @Override
    public Component getDescription() {
        return description != null ? description : delegate.getDescription();
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
    public double getResourceCost() {
        return delegate.getResourceCost();
    }

    @Override
    public int getBindSlot() {
        return bindSlot >= 0 ? bindSlot : delegate.getBindSlot();
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
    public void onAttack(ServerPlayer player, net.minecraft.world.entity.LivingEntity target, float damage) {
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
