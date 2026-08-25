package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.network.CooldownPayload;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Active power that launches the player forwards along their look direction
 * with a short cooldown. Triggered by the player's active-power keybind.
 */
public class DashPower implements Power {

    private static final int DEFAULT_COOLDOWN = 60;

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double strength;

    public DashPower(Identifier id, int difficulty) {
        this(id, difficulty, DEFAULT_COOLDOWN, 1.8);
    }

    public DashPower(Identifier id, int difficulty, int cooldownTicks, double strength) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.strength = strength;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power." + id.getNamespace() + "." + id.getPath() + ".desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public boolean hasBinding() {
        return true;
    }

    @Override
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) {
            return;
        }

        Vec3 look = player.getLookAngle();
        Vec3 velocity = new Vec3(look.x * strength, 0.25, look.z * strength);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;

        RaceUtils.serverLevel(player).playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.gameEvent(GameEvent.ELYTRA_GLIDE);
        Vec3 base = player.position().add(0, 1, 0);
        for (int i = 0; i < 20; i++) {
            double spread = player.level().getRandom().nextGaussian() * 0.2;
            Vec3 pos = base.add(look.x * i * 0.5 + spread,
                    0.0, look.z * i * 0.5 + spread);
            RaceUtils.serverLevel(player).sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z,
                    1, 0.0, 0.0, 0.0, 0.02);
        }

        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
    }
}
