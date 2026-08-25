package dev.raceapi.data;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists the selected race id per player UUID in the overworld's data storage.
 */
public class RaceSavedData extends SavedData {

    public static final SavedDataType<RaceSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("raceapi", "raceapi_races"),
            RaceSavedData::new,
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .xmap(RaceSavedData::fromMap, RaceSavedData::toMap));

    private final Map<UUID, String> races = new HashMap<>();

    public static RaceSavedData get(ServerLevel level) {
        // always the overworld's storage so the race survives dimension changes
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public String get(UUID uuid) {
        return races.getOrDefault(uuid, "");
    }

    public void set(UUID uuid, String raceId) {
        if (raceId == null || raceId.isEmpty()) {
            races.remove(uuid);
        } else {
            races.put(uuid, raceId);
        }
        setDirty();
    }

    private static RaceSavedData fromMap(Map<String, String> map) {
        RaceSavedData data = new RaceSavedData();
        map.forEach((key, value) -> {
            try {
                data.races.put(UUID.fromString(key), value);
            } catch (IllegalArgumentException ignored) {
            }
        });
        return data;
    }

    private static Map<String, String> toMap(RaceSavedData data) {
        Map<String, String> map = new HashMap<>();
        data.races.forEach((key, value) -> map.put(key.toString(), value));
        return map;
    }
}
