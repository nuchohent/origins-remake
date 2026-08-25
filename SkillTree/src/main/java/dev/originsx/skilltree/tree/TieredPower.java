package dev.originsx.skilltree.tree;

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
 * Decorates one race power with its skill-tree state.
 * <ul>
 *   <li>{@code tier == 0} — locked: every gameplay hook is a no-op, but identity
 *       and bind metadata still come from the original power so client keybind
 *       slot indices stay stable.</li>
 *   <li>{@code tier 1..2} — unlocked: hooks delegate to the scaled weak copy
 *       built by {@link TierScaler}.</li>
 *   <li>{@code tier == 3} — fully upgraded: hooks delegate to the node's own
 *       inline power at full (unscaled) strength.</li>
 * </ul>
 */
public final class TieredPower implements Power {

    private final Power original;
    private final Power active;
    private final int tier;

    private TieredPower(Power original, Power active, int tier) {
        this.original = original;
        this.active = active;
        this.tier = tier;
    }

    /** Builds the wrapper for the given tier ({@code 0..3}). */
    public static Power of(Power original, Power activeCopy, int tier) {
        return new TieredPower(original, switch (tier) {
            case 0 -> null;
            default -> activeCopy != null ? activeCopy : original;
        }, tier);
    }

    public int tier() {
        return tier;
    }

    @Override
    public Power getWrapped() {
        // While locked the inner power must stay invisible to unwrap-based
        // checks (e.g. action restrictions); when unlocked it applies fully.
        return active == null ? this : active.getWrapped();
    }

    @Override
    public Identifier getId() {
        return original.getId();
    }

    @Override
    public Component getDisplayName() {
        return original.getDisplayName();
    }

    @Override
    public Component getDescription() {
        return original.getDescription();
    }

    @Override
    public int getDifficulty() {
        return original.getDifficulty();
    }

    @Override
    public boolean isHidden() {
        return original.isHidden();
    }

    @Override
    public boolean hasBinding() {
        return original.hasBinding();
    }

    @Override
    public int getBindSlot() {
        return original.getBindSlot();
    }

    @Override
    public int getCooldownTicks() {
        return active == null ? 0 : active.getCooldownTicks();
    }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return active == null ? 0 : active.getRemainingCooldownTicks(player);
    }

    @Override
    public void onAttach(ServerPlayer player) {
        if (active != null) {
            active.onAttach(player);
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        if (active != null) {
            active.onRemove(player);
        }
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (active != null) {
            active.onTick(player);
        }
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (active != null) {
            active.onKeyPressed(player);
        }
    }

    @Override
    public void onJump(ServerPlayer player) {
        if (active != null) {
            active.onJump(player);
        }
    }

    @Override
    public void onAttack(ServerPlayer player, LivingEntity target, float damage) {
        if (active != null) {
            active.onAttack(player, target, damage);
        }
    }

    @Override
    public float onFall(ServerPlayer player, float fallDistance) {
        return active == null ? fallDistance : active.onFall(player, fallDistance);
    }

    @Override
    public void onBreakBlock(ServerPlayer player, BlockPos pos, BlockState state) {
        if (active != null) {
            active.onBreakBlock(player, pos, state);
        }
    }

    @Override
    public float onBreakSpeed(ServerPlayer player, float speed) {
        return active == null ? speed : active.onBreakSpeed(player, speed);
    }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        return active == null ? amount : active.onHurt(player, source, amount);
    }

    @Override
    public void onInteractBlock(ServerPlayer player, InteractionHand hand, BlockPos pos, BlockHitResult hitResult) {
        if (active != null) {
            active.onInteractBlock(player, hand, pos, hitResult);
        }
    }

    @Override
    public void onInteractEntity(ServerPlayer player, InteractionHand hand, Entity target) {
        if (active != null) {
            active.onInteractEntity(player, hand, target);
        }
    }
}
