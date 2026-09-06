package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Passive power that grants the player creative-style flight (the ability to
 * fly by double-tapping jump) while the race is active.
 * <p>
 * The ability is granted on attach and re-asserted each tick so nothing can
 * silently strip it while a race that provides it is attached; on remove it is
 * revoked unless the player legitimately holds it (creative/spectator mode).
 * <p>
 * This is the core enabler for multi-layer combos like a "bird" layer that
 * flies plus a "weakness" layer that anchors a heavier form: the flight comes
 * from its own layer and layers combine additively.
 */
public class CreativeFlightPower implements Power {

    private final Identifier id;
    private final int difficulty;

    public CreativeFlightPower(Identifier id, int difficulty) {
        this.id = id;
        this.difficulty = difficulty;
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
    public void onAttach(ServerPlayer player) {
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }

    @Override
    public void onRemove(ServerPlayer player) {
        if (player.getAbilities().mayfly && !player.isCreative()
                && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    @Override
    public void onTick(ServerPlayer player) {
        // keep the flight ability while the power is attached; races stack per
        // layer, so a flight layer stays flighty even under restrictions from
        // other layers that would otherwise reset the ability
        if (!player.isCreative() && !player.isSpectator()
                && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }
}