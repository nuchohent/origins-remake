package dev.raceapi.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists the selected race id per layer per player UUID in the overworld's
 * data storage.
 * <p>
 * A legacy value that is a plain string (not a JSON object) is treated as the
 * race id for the default {@code "origin"} layer, so pre-2.0 saves migrate
 * automatically.
 */
public class RaceSavedData extends SavedData {

    private static final Gson GSON = new Gson();

    public static final SavedDataType<RaceSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("raceapi", "raceapi_races"),
            RaceSavedData::new,
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .xmap(RaceSavedData::fromMap, RaceSavedData::toMap));

    private final Map<UUID, Map<String, String>> raceLayers = new HashMap<>();

    public static RaceSavedData get(ServerLevel level) {
        // always the overworld's storage so the race survives dimension changes
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The race id selected for the given layer, or "" if none. */
    public String get(UUID uuid, String layer) {
        Map<String, String> layers = raceLayers.get(uuid);
        if (layers == null) {
            return "";
        }
        return layers.getOrDefault(layer, "");
    }

    /** Sets the race id for the given layer. Empty clears the layer. */
    public void set(UUID uuid, String layer, String raceId) {
        Map<String, String> layers = raceLayers.computeIfAbsent(uuid, k -> new HashMap<>());
        if (raceId == null || raceId.isEmpty()) {
            layers.remove(layer);
            if (layers.isEmpty()) {
                raceLayers.remove(uuid);
            }
        } else {
            layers.put(layer, raceId);
        }
        setDirty();
    }

    /** Removes every layer selection for a player. */
    public void clearAll(UUID uuid) {
        if (raceLayers.remove(uuid) != null) {
            setDirty();
        }
    }

    private static RaceSavedData fromMap(Map<String, String> map) {
        RaceSavedData data = new RaceSavedData();
        map.forEach((key, value) -> {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            Map<String, String> layers = parseLayers(value);
            if (layers.isEmpty()) {
                return;
            }
            data.raceLayers.put(uuid, layers);
        });
        return data;
    }

    /**
     * A legacy plain race id becomes the {@code origin} layer; anything that
     * parses as a JSON object is read as a layer id to race id map.
     */
    private static Map<String, String> parseLayers(String stored) {
        Map<String, String> layers = new HashMap<>();
        try {
            JsonElement element = GSON.fromJson(stored, JsonElement.class);
            if (element != null && element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        layers.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return layers;
            }
        } catch (com.google.gson.JsonSyntaxException ignored) {
            // fall through to legacy handling
        }
        if (stored != null && !stored.isEmpty() && stored.indexOf('{') != 0) {
            layers.put("origin", stored);
        }
        return layers;
    }

    private static Map<String, String> toMap(RaceSavedData data) {
        Map<String, String> map = new HashMap<>();
        data.raceLayers.forEach((key, layers) -> map.put(key.toString(), GSON.toJson(layers)));
        return map;
    }
}
