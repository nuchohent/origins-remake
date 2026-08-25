package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Weakness that sets the player on fire whenever they are directly exposed to
 * the daytime sky (under a roof or in rain they are safe). A vampire-style
 * drawback the vanilla game has no command for.
 */
public class LightSensitivePower implements Power {

    private final Identifier id;
    private final int difficulty;

    public LightSensitivePower(Identifier id, int difficulty) {
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
    public void onTick(ServerPlayer player) {
        if (player.tickCount % 10 != 0) {
            return;
        }
        var level = player.level();
        if (level.getOverworldClockTime() % 24000 < 12000 && !level.isRaining() && level.canSeeSky(player.blockPosition().above())) {
            // vanilla deals fire damage only when the counter crosses a
            // multiple of 20 — keep at least 25 ticks burning so the damage
            // tick actually lands between our 10-tick refreshes
            player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 25));
        }
    }
}
