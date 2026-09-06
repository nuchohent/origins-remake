package dev.raceapi.power;

import dev.raceapi.player.Resources;
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
 * Turns an active power into a switch: pressing its key toggles the inner
 * power ON/OFF instead of triggering it once. While ON, all of the inner
 * power's hooks (tick, attack, hurt, ...) run; while OFF they are swallowed.
 * <p>
 * Applied by {@link PowerFactory} for JSON definitions with {@code "toggle": true}.
 * The state starts OFF on every race attach and is stored per player in
 * persistent data. A resource cost is charged ONCE on activation (this wrapper
 * reports zero cost to the keybind pipeline so the cost is never charged twice).
 */
public final class ToggledPower implements Power {

    private final Power delegate;

    public ToggledPower(Power delegate) {
        this.delegate = delegate;
    }

    private static String stateKey(Identifier id) {
        return "raceapi_tgl_" + id;
    }

    private static boolean isOn(ServerPlayer player, Identifier id) {
        return player.getPersistentData().getBoolean(stateKey(id)).orElse(false);
    }

    private static void setOn(ServerPlayer player, Identifier id, boolean on) {
        player.getPersistentData().putBoolean(stateKey(id), on);
    }

    @Override
    public Power getWrapped() {
        return delegate.getWrapped();
    }

    /**
     * Zero on purpose: the keybind pipeline must NOT pre-charge the cost -
     * {@link #onKeyPressed} charges it only when switching ON.
     */
    @Override
    public double getResourceCost() {
        return 0;
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
        return true;
    }

    @Override
    public int getCooldownTicks() {
        return 0;
    }

    @Override
    public int getBindSlot() {
        return delegate.getBindSlot();
    }

    @Override
    public void onAttach(ServerPlayer player) {
        // every fresh attach starts with the switch OFF
        setOn(player, delegate.getId(), false);
        dev.raceapi.network.PowerStatePayload.send(player, delegate.getId().toString(), false);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        if (isOn(player, delegate.getId())) {
            delegate.onRemove(player);
        }
        setOn(player, delegate.getId(), false);
        dev.raceapi.network.PowerStatePayload.send(player, delegate.getId().toString(), false);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        Identifier id = delegate.getId();
        if (!isOn(player, id)) {
            double cost = delegate.getResourceCost();
            if (cost > 0 && !Resources.tryConsume(player, cost)) {
                player.sendOverlayMessage(Component.translatable("raceapi.resource.insufficient"));
                return;
            }
            setOn(player, id, true);
            delegate.onAttach(player);
            // HUD indicator instead of chat/action-bar spam
            dev.raceapi.network.PowerStatePayload.send(player, id.toString(), true);
        } else {
            setOn(player, id, false);
            delegate.onRemove(player);
            dev.raceapi.network.PowerStatePayload.send(player, id.toString(), false);
        }
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (isOn(player, delegate.getId())) {
            delegate.onTick(player);
        }
    }

    @Override
    public void onJump(ServerPlayer player) {
        if (isOn(player, delegate.getId())) {
            delegate.onJump(player);
        }
    }

    @Override
    public void onAttack(ServerPlayer player, LivingEntity target, float damage) {
        if (isOn(player, delegate.getId())) {
            delegate.onAttack(player, target, damage);
        }
    }

    @Override
    public float onFall(ServerPlayer player, float fallDistance) {
        if (!isOn(player, delegate.getId())) {
            return fallDistance;
        }
        return delegate.onFall(player, fallDistance);
    }

    @Override
    public float onBreakSpeed(ServerPlayer player, float speed) {
        if (!isOn(player, delegate.getId())) {
            return speed;
        }
        return delegate.onBreakSpeed(player, speed);
    }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        if (!isOn(player, delegate.getId())) {
            return amount;
        }
        return delegate.onHurt(player, source, amount);
    }

    @Override
    public void onBreakBlock(ServerPlayer player, BlockPos pos, BlockState state) {
        if (isOn(player, delegate.getId())) {
            delegate.onBreakBlock(player, pos, state);
        }
    }

    @Override
    public void onInteractBlock(ServerPlayer player, InteractionHand hand, BlockPos pos, BlockHitResult hitResult) {
        if (isOn(player, delegate.getId())) {
            delegate.onInteractBlock(player, hand, pos, hitResult);
        }
    }

    @Override
    public void onInteractEntity(ServerPlayer player, InteractionHand hand, Entity target) {
        if (isOn(player, delegate.getId())) {
            delegate.onInteractEntity(player, hand, target);
        }
    }

    @Override
    public void onKill(ServerPlayer player, LivingEntity victim) {
        if (isOn(player, delegate.getId())) {
            delegate.onKill(player, victim);
        }
    }

    @Override
    public void onEat(ServerPlayer player) {
        if (isOn(player, delegate.getId())) {
            delegate.onEat(player);
        }
    }
}
