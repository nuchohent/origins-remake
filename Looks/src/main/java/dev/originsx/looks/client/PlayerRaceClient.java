package dev.originsx.looks.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client mirror of every online player's race id, updated from
 * {@link dev.originsx.looks.net.PlayerRacePayload} broadcasts.
 */
public final class PlayerRaceClient {

    private static final Map<UUID, String> RACES = new HashMap<>();

    private PlayerRaceClient() {
    }

    public static void apply(String uuid, String raceId) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        try {
            UUID id = UUID.fromString(uuid);
            if (raceId == null || raceId.isEmpty()) {
                RACES.remove(id);
            } else {
                RACES.put(id, raceId);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** @return race id string of the player, or null when unknown/no race. */
    public static String get(UUID uuid) {
        return RACES.get(uuid);
    }

    public static void clear() {
        RACES.clear();
    }
}
