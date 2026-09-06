package dev.originsx.looks.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client mirror of every online player's multi-layer race selection (one race
 * id per origin layer), updated from {@link dev.originsx.looks.net.PlayerRacePayload}
 * broadcasts. Cosmetics of every selected layer are combined when rendering.
 */
public final class PlayerRaceClient {

    private static final Map<UUID, Map<String, String>> RACES = new HashMap<>();

    private PlayerRaceClient() {
    }

    public static void apply(String uuid, Map<String, String> layers) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        try {
            UUID id = UUID.fromString(uuid);
            if (layers == null || layers.isEmpty()) {
                RACES.remove(id);
            } else {
                RACES.put(id, new HashMap<>(layers));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** @return layer id → race id map of the player, or an empty map when unknown/no race. */
    public static Map<String, String> getLayers(UUID uuid) {
        return RACES.getOrDefault(uuid, Map.of());
    }

    public static void clear() {
        RACES.clear();
    }
}
