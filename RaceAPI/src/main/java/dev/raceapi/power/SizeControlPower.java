package dev.raceapi.power;

import dev.raceapi.network.CooldownPayload;
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
 * Active power: changes the player's scale on key press.
 * <p>
 * Modes:
 * <ul>
 *   <li>{@code permanent = true} — toggle: press to apply target scale, press again to reset.</li>
 *   <li>{@code permanent = false} — timed: press to apply target scale for {@code duration} seconds.</li>
 * </ul>
 * Scale is applied via the native {@code minecraft:scale} attribute.
 */
public class SizeControlPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double targetScale;
    private final boolean permanent;
    private final int durationTicks;
    private final int cooldownTicks;

    private final Map<UUID, Boolean> active = new HashMap<>();
    private final Map<UUID, Integer> timers = new HashMap<>();

    public SizeControlPower(Identifier id, int difficulty, double targetScale,
                            boolean permanent, int durationTicks, int cooldownTicks) {
        this.id = id;
        this.difficulty = difficulty;
        this.targetScale = targetScale;
        this.permanent = permanent;
        this.durationTicks = durationTicks;
        this.cooldownTicks = cooldownTicks;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.size_control.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.size_control.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean currentlyActive = active.getOrDefault(uuid, false);

        if (permanent) {
            if (currentlyActive) {
                resetScale(player);
                active.put(uuid, false);
            } else {
                if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;
                applyScale(player);
                active.put(uuid, true);
                CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
            }
        } else {
            if (currentlyActive) return;
            if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;
            applyScale(player);
            active.put(uuid, true);
            timers.put(uuid, durationTicks);
            CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
        }
    }

    @Override
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (permanent || !active.getOrDefault(uuid, false)) return;

        int remaining = timers.getOrDefault(uuid, 0) - 1;
        if (remaining <= 0) {
            resetScale(player);
            active.put(uuid, false);
            timers.remove(uuid);
        } else {
            timers.put(uuid, remaining);
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (active.getOrDefault(uuid, false)) {
            resetScale(player);
        }
        active.remove(uuid);
        timers.remove(uuid);
    }

    private void applyScale(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance != null) {
            double currentScale = instance.getValue();
            double scaleDiff = targetScale - currentScale;
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, scaleDiff, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void resetScale(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
