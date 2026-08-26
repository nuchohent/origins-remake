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
        try {
            if (raceId == null || raceId.isEmpty()) {
                RACES.remove(UUID.fromString(uuid));
            } else {
                RACES.put(UUID.fromString(uuid), raceId);
            }
        } catch (IllegalArgumentException ignored) {
            // malformed uuid must never break the payload handler
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
