package dev.originsx.looks.server;

import dev.originsx.looks.net.PlayerRacePayload;
import dev.raceapi.event.RaceChangedEvent;
import dev.raceapi.player.RaceManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server side: keeps every client informed about everyone's multi-layer race
 * selection so the Looks render layer can draw cosmetics on all visible
 * players. Cosmetics from every selected layer are combined client-side.
 */
public final class PlayerRaceTracker {

    private PlayerRaceTracker() {
    }

    /** A joining player's race selection is broadcast to all (including itself). */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRacePayload.broadcast(player, RaceManager.getSelection(player));
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // let remaining clients drop the stale entry
            PlayerRacePayload.broadcast(player, null);
        }
    }

    public static void onRaceChanged(RaceChangedEvent event) {
        PlayerRacePayload.broadcast(event.getPlayer(), RaceManager.getSelection(event.getPlayer()));
    }
}
