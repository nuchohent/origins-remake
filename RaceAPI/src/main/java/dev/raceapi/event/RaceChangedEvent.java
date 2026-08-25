package dev.raceapi.event;

import dev.raceapi.race.Race;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Fired on the NeoForge event bus when a player's race changes.
 * <p>
 * This event is fired <b>after</b> the old race's powers have been removed and
 * the new race's powers have been applied. Other mods can listen to this event
 * to react to race changes (e.g. apply custom effects, update data, etc.).
 * <p>
 * This event is not cancellable.
 */
public class RaceChangedEvent extends Event {

    private final ServerPlayer player;
    @Nullable
    private final Race oldRace;
    @Nullable
    private final Race newRace;

    public RaceChangedEvent(ServerPlayer player, @Nullable Race oldRace, @Nullable Race newRace) {
        this.player = player;
        this.oldRace = oldRace;
        this.newRace = newRace;
    }

    /** The player whose race changed. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** The previous race, or null if the player had no race. */
    @Nullable
    public Race getOldRace() {
        return oldRace;
    }

    /** The new race, or null if the race was cleared. */
    @Nullable
    public Race getNewRace() {
        return newRace;
    }
}
