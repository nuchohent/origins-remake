package dev.raceapi.power;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-type parameter schemas with engine defaults, loaded from
 * {@code /assets/raceapi/power_defaults.json}. The file mirrors the fallback
 * values hard-coded in {@link PowerTypes} parsers so external tools (skill
 * tree editor, future UIs) can materialize a sparse power definition into a
 * fully explicit one: every parameter the engine would default becomes a
 * visible, editable field.
 */
public final class PowerSchemas {

    private static final Map<String, JsonObject> SCHEMAS = new HashMap<>();

    static {
        try (var in = PowerSchemas.class.getResourceAsStream("/assets/raceapi/power_defaults.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseString(new String(in.readAllBytes())).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (e.getValue().isJsonObject()) {
                        SCHEMAS.put(e.getKey(), e.getValue().getAsJsonObject().deepCopy());
                    }
                }
            } else {
                PowerFactory.LOGGER.warn("PowerSchemas: power_defaults.json not found on classpath");
            }
        } catch (Exception ex) {
            PowerFactory.LOGGER.warn("PowerSchemas: failed to load power_defaults.json", ex);
        }
    }

    private PowerSchemas() {
    }

    /** Whether a defaults schema exists for the given type (bare or namespaced). */
    public static boolean hasSchema(String type) {
        return SCHEMAS.containsKey(bareType(type));
    }

    /**
     * Returns a deep copy of {@code power} with every schema parameter that is
     * missing filled in with its default. Values already present (including
     * display_name/description/cost/bind_slot/difficulty) are preserved.
     * Unknown types and non-object definitions are returned unchanged.
     */
    public static JsonObject materialize(JsonObject power) {
        if (power == null || !power.has("type") || !power.get("type").isJsonPrimitive()) {
            return power;
        }
        JsonObject defaults = SCHEMAS.get(bareType(power.get("type").getAsString()));
        if (defaults == null) {
            return power;
        }
        JsonObject out = power.deepCopy();
        for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
            if (!out.has(e.getKey())) {
                out.add(e.getKey(), e.getValue().deepCopy());
            }
        }
        return out;
    }

    private static String bareType(String type) {
        if (type == null) {
            return "";
        }
        return type.startsWith("raceapi:") ? type.substring("raceapi:".length()) : type;
    }
}
