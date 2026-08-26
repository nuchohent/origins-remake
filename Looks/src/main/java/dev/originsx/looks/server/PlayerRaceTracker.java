package dev.originsx.looks.server;

import dev.originsx.looks.net.PlayerRacePayload;
import dev.raceapi.event.RaceChangedEvent;
import dev.raceapi.player.RaceManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server side: keeps every client informed about everyone's race so the
 * Looks render layer can draw cosmetics on all visible players.
 */
public final class PlayerRaceTracker {

    private PlayerRaceTracker() {
    }

    /** A joining player's race is broadcast to all (including itself). */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var race = RaceManager.getRace(player);
            PlayerRacePayload.broadcast(player,
                    race == null ? "" : race.getId().toString());
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // let remaining clients drop the stale entry
            PlayerRacePayload.broadcast(player, "");
        }
    }

    public static void onRaceChanged(RaceChangedEvent event) {
        var race = event.getNewRace();
        PlayerRacePayload.broadcast(event.getPlayer(),
                race == null ? "" : race.getId().toString());
    }
}
