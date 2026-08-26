package dev.raceapi.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.raceapi.power.PowerFactory;
import dev.raceapi.race.Power;
import dev.raceapi.race.PowerRegistry;
import dev.raceapi.race.RaceRegistry;
import dev.raceapi.race.SimpleRace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads JSON race definitions from {@code data/<namespace>/raceapi/races/*.json}.
 *
 * <pre>{@code
 * {
 *   "display_name": "Avian",
 *   "description": "Light as a feather.",
 *   "icon": "minecraft:feather",
 *   "difficulty": 2,
 *   "scale": 0.9,
 *   "width": 0.5,
 *   "height": 1.7,
 *   "powers": ["raceapi:creative_flight", "raceapi:speed"]
 * }
 * }</pre>
 * <p>
 * {@code display_name} and {@code description} are plain text; if you want
 * translated names, supply {@code name_key} / {@code description_key} instead.
 * Each entry of {@code powers} may be a power id string, or an inline power
 * object (same schema as {@code data/<namespace>/raceapi/powers/*.json}).
 */
public class RaceDataLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    private static final Gson GSON = new Gson();
    private static final FileToIdConverter CONVERTER = FileToIdConverter.json("raceapi/races");

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier id = CONVERTER.fileToId(entry.getKey());
            Resource resource = entry.getValue();
            try (InputStream stream = resource.open()) {
                map.put(id, JsonParser.parseReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception e) {
                ParseErrors.error("Failed to read race definition " + id + ": " + e.getMessage());
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects,
                         ResourceManager resourceManager, ProfilerFiller profiler) {

        RaceRegistry.clearJson();
        LAST_LOADED.clear();
        for (Map.Entry<Identifier, JsonElement> entry : objects.entrySet()) {
            Identifier id = entry.getKey();
            try {
                LAST_LOADED.put(id, entry.getValue().getAsJsonObject());
                parseRace(id, entry.getValue().getAsJsonObject());
            } catch (Exception e) {
                ParseErrors.error("Failed to parse race definition " + id + ": " + e.getMessage());
            }
        }
        // dedicated servers: push every race definition to connected clients,
        // their own registry would otherwise stay empty
        dev.raceapi.network.SyncRacesPayload.broadcast();
    }

    /**
     * Raw JSON of the races loaded by the last server-side datapack reload —
     * the source of truth for the client sync payload.
     */
    private static final Map<Identifier, JsonObject> LAST_LOADED = new HashMap<>();

    public static Map<Identifier, JsonObject> lastLoaded() {
        return java.util.Collections.unmodifiableMap(LAST_LOADED);
    }

    /**
     * Client-side entry point for {@link dev.raceapi.network.SyncRacesPayload}:
     * rebuilds the client registry from server-synced definitions.
     */
    public static void applyClientRaces(Map<Identifier, JsonObject> races) {
        RaceRegistry.clearJson();
        for (Map.Entry<Identifier, JsonObject> entry : races.entrySet()) {
            try {
                parseRace(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                // client parse failures must never crash the game
            }
        }
    }

    private static void parseRace(Identifier id, JsonObject json) {
        Component name;
        if (json.has("name_key")) {
            name = Component.translatable(json.get("name_key").getAsString());
        } else {
            name = Component.literal(stringOr(json, "display_name", id.getPath()));
        }
        Component description;
        if (json.has("description_key")) {
            description = Component.translatable(json.get("description_key").getAsString());
        } else {
            description = Component.literal(stringOr(json, "description", ""));
        }
        Identifier iconId = null;
        if (json.has("icon")) {
            iconId = Identifier.tryParse(json.get("icon").getAsString());
        }

        List<Power> powers = new ArrayList<>();
        List<int[]> bindSlots = new ArrayList<>();
        if (json.has("powers") && json.get("powers").isJsonArray()) {
            int inlineIndex = 0;
            for (JsonElement element : json.getAsJsonArray("powers")) {
                if (element.isJsonPrimitive()) {
                    Identifier powerId = Identifier.tryParse(element.getAsString());
                    if (powerId == null) {
                        ParseErrors.error("Invalid power id '" + element.getAsString() + "' in race " + id);
                        continue;
                    }
                    Power power = PowerRegistry.createOrNull(powerId);
                    if (power == null) {
                        ParseErrors.error("Unknown power '" + powerId + "' in race " + id);
                        continue;
                    }
                    powers.add(power);
                    bindSlots.add(new int[]{powers.size() - 1, powers.size() - 1});
                } else if (element.isJsonObject()) {
                    int powerNumber = inlineIndex++;
                    Identifier inlineId = Identifier.fromNamespaceAndPath(
                            id.getNamespace(), id.getPath() + "_power_" + powerNumber);
                    Power power = PowerFactory.create(inlineId, element.getAsJsonObject());
                    if (power == null) {
                        ParseErrors.error("Invalid inline power #" + powerNumber + " in race " + id);
                        continue;
                    }
                    int slot = element.getAsJsonObject().has("bind_slot")
                            ? element.getAsJsonObject().get("bind_slot").getAsInt() : powers.size();
                    powers.add(power);
                    bindSlots.add(new int[]{powers.size() - 1, slot});
                }
            }
        }

        // Sort active powers by their bind_slot so the order matches the creator's assignment
        bindSlots.sort((a, b) -> Integer.compare(a[1], b[1]));
        List<Power> sorted = new ArrayList<>();
        for (int[] pair : bindSlots) {
            sorted.add(powers.get(pair[0]));
        }
        powers = sorted;

        SimpleRace race = new SimpleRace(id)
                .displayName(name)
                .description(description)
                .powers(powers)
                .iconId(iconId)
                .difficulty(intOr(json, "difficulty", 0))
                .scale(floatOr(json, "scale", 1.0f))
                .width(doubleOr(json, "width", 0.6))
                .height(doubleOr(json, "height", 1.8))
                .hidden(json.has("hidden") && json.get("hidden").getAsBoolean())
                // keep the raw definition so addons can read custom fields
                // (e.g. "cosmetics") from the synced race on the client
                .sourceJson(json);
        RaceRegistry.registerJson(race);
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static float floatOr(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    private static double doubleOr(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }
}
