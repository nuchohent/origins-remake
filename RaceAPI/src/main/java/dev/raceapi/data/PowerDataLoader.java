package dev.raceapi.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.raceapi.power.PowerFactory;
import dev.raceapi.race.Power;
import dev.raceapi.race.PowerRegistry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads JSON power definitions from {@code data/<namespace>/raceapi/powers/*.json},
 * so races (and other datapacks) can define reusable powers without writing Java.
 *
 * <pre>{@code
 * {
 *   "type": "raceapi:attribute",
 *   "attribute": "minecraft:max_health",
 *   "amount": 4.0,
 *   "operation": "add_value",
 *   "difficulty": 2
 * }
 * }</pre>
 */
public class PowerDataLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    private static final Gson GSON = new Gson();
    private static final FileToIdConverter CONVERTER = FileToIdConverter.json("raceapi/powers");

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier id = CONVERTER.fileToId(entry.getKey());
            Resource resource = entry.getValue();
            try (InputStream stream = resource.open()) {
                map.put(id, JsonParser.parseReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception e) {
                ParseErrors.error("Failed to read power definition " + id + ": " + e.getMessage());
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        ParseErrors.clear();
                PowerRegistry.clearJson();
        for (Map.Entry<Identifier, JsonElement> entry : objects.entrySet()) {
            Identifier id = entry.getKey();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                Power power = PowerFactory.create(id, json);
                if (power == null) {
                    ParseErrors.error("Power definition " + id + " failed to parse");
                    continue;
                }
                PowerRegistry.registerJson(id, power);
            } catch (Exception e) {
                ParseErrors.error("Failed to parse power definition " + id + ": " + e.getMessage());
            }
        }
    }
}
