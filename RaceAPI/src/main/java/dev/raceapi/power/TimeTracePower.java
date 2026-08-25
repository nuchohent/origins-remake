package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.network.CooldownPayload;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TimeTracePower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int autoReturnTicks;
    private final int cooldownTicks;
    private final Map<UUID, TraceState> states = new HashMap<>();

    public TimeTracePower(Identifier id, int difficulty, int autoReturnTicks) {
        this(id, difficulty, autoReturnTicks, 0);
    }

    public TimeTracePower(Identifier id, int difficulty, int autoReturnTicks, int cooldownTicks) {
        this.id = id;
        this.difficulty = difficulty;
        this.autoReturnTicks = autoReturnTicks;
        this.cooldownTicks = cooldownTicks;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.time_trace.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.time_trace.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;
        if (cooldownTicks > 0) {
            CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
        }

        UUID uuid = player.getUUID();
        TraceState state = states.get(uuid);
        boolean active = state != null && state.active;

        if (!active) {
            TraceState newState = new TraceState();
            newState.savedPos = player.position();
            newState.savedHealth = player.getHealth();
            newState.tickCount = 0;
            newState.active = true;
            states.put(uuid, newState);

            ServerLevel level = RaceUtils.serverLevel(player);
            level.sendParticles(ParticleTypes.END_ROD, newState.savedPos.x, newState.savedPos.y + 1, newState.savedPos.z,
                    5, 0.3, 0.5, 0.3, 0.02);
            player.sendSystemMessage(Component.translatable("power.raceapi.time_trace.marked"));
        } else {
            returnToMark(player, state);
        }
    }

    @Override
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        TraceState state = states.get(uuid);
        if (state == null || !state.active) return;

        state.tickCount++;
        if (state.tickCount >= autoReturnTicks) {
            returnToMark(player, state);
            return;
        }
        if (state.tickCount % 5 == 0 && state.savedPos != null) {
            RaceUtils.serverLevel(player).sendParticles(ParticleTypes.REVERSE_PORTAL,
                    state.savedPos.x, state.savedPos.y + 0.5, state.savedPos.z, 2, 0.3, 0.5, 0.3, 0.01);
        }
    }

    private void returnToMark(ServerPlayer player, TraceState state) {
        if (state == null || state.savedPos == null) {
            states.remove(player.getUUID());
            return;
        }
        ServerLevel level = RaceUtils.serverLevel(player);
        player.teleportTo(state.savedPos.x, state.savedPos.y, state.savedPos.z);
        if (player.getHealth() > state.savedHealth) {
            player.setHealth(state.savedHealth);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1, player.getZ(), 10, 0.5, 0.8, 0.5, 0.3);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
        states.remove(player.getUUID());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        states.remove(player.getUUID());
    }

    private static class TraceState {
        Vec3 savedPos;
        float savedHealth;
        int tickCount;
        boolean active;
    }
}
