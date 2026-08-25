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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KineticCounterPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int parryWindowTicks;
    private final int stunDurationTicks;
    private final int baseCooldownTicks;
    private final int missCooldownTicks;
    private final double pushbackStrength;

    private final Map<UUID, Integer> parryTimers = new HashMap<>();
    private final Map<UUID, Boolean> wasHit = new HashMap<>();

    public KineticCounterPower(Identifier id, int difficulty,
                               int parryWindowTicks, int stunDurationTicks,
                               int baseCooldownTicks, int missCooldownTicks,
                               double pushbackStrength) {
        this.id = id;
        this.difficulty = difficulty;
        this.parryWindowTicks = parryWindowTicks;
        this.stunDurationTicks = stunDurationTicks;
        this.baseCooldownTicks = baseCooldownTicks;
        this.missCooldownTicks = missCooldownTicks;
        this.pushbackStrength = pushbackStrength;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.kinetic_counter.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.kinetic_counter.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return baseCooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, baseCooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (!PowerCooldowns.tryUse(player, id, baseCooldownTicks)) return;
        parryTimers.put(player.getUUID(), parryWindowTicks);
        wasHit.put(player.getUUID(), false);
        CooldownPayload.send(player, id.toString(), baseCooldownTicks, baseCooldownTicks);
    }

    @Override
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Integer timer = parryTimers.get(uuid);
        if (timer == null || timer <= 0) return;
        parryTimers.put(uuid, timer - 1);

        if (timer > 0 && RaceUtils.serverLevel(player).getGameTime() % 3 == 0) {
            RaceUtils.serverLevel(player).sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY() + 1, player.getZ(), 2, 0.3, 0.3, 0.3, 0.05);
        }

        if (timer - 1 <= 0) {
            if (!Boolean.TRUE.equals(wasHit.get(uuid))) {
                // missed window: lock out from NOW — tryUse would reject the
                // write while the base cooldown from the press is still running
                PowerCooldowns.forceUse(player, id);
                CooldownPayload.send(player, id.toString(), baseCooldownTicks, baseCooldownTicks);
                player.sendSystemMessage(Component.translatable("power.raceapi.kinetic_counter.miss"));
            }
            parryTimers.remove(uuid);
            wasHit.remove(uuid);
        }
    }

    public boolean tryParry(ServerPlayer player, Entity attacker) {
        UUID uuid = player.getUUID();
        Integer timer = parryTimers.get(uuid);
        if (timer == null || timer <= 0) return false;

        wasHit.put(uuid, true);
        parryTimers.remove(uuid);

        if (attacker != null) {
            Vec3 dir = attacker.position().subtract(player.position()).normalize().scale(pushbackStrength);
            attacker.push(dir.x, pushbackStrength * 0.25, dir.z);
            attacker.hurtMarked = true;
            if (attacker instanceof LivingEntity living) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.SLOWNESS, stunDurationTicks, 127, false, false, true));
            }
        }

        ServerLevel level = RaceUtils.serverLevel(player);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1, player.getZ(), 8, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    public boolean isParrying(ServerPlayer player) {
        Integer timer = parryTimers.get(player.getUUID());
        return timer != null && timer > 0;
    }

    @Override
    public void onRemove(ServerPlayer player) {
        UUID uuid = player.getUUID();
        parryTimers.remove(uuid);
        wasHit.remove(uuid);
    }
}
