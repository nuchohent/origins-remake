package dev.originsx.skilltree;

import dev.originsx.skilltree.net.SyncSkillStatePayload;
import dev.raceapi.event.RaceChangedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server-side pushes of skill state to clients.
 */
public final class ServerEvents {

    private ServerEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SyncSkillStatePayload.send(player);
        }
    }

    public static void onRaceChanged(RaceChangedEvent event) {
        SyncSkillStatePayload.send(event.getPlayer());
    }
}
