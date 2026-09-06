package dev.raceapi.power;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowerSchemasTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void fillsMissingDefaults() {
        JsonObject out = PowerSchemas.materialize(json("{\"type\":\"raceapi:dash\"}"));
        assertEquals(3.0, out.get("cooldown").getAsDouble(), 1e-9);
        assertEquals(1.8, out.get("strength").getAsDouble(), 1e-9);
        assertEquals("raceapi:dash", out.get("type").getAsString());
    }

    @Test
    void preservesExistingValues() {
        JsonObject out = PowerSchemas.materialize(
                json("{\"type\":\"dash\",\"cooldown\":10,\"display_name\":\"Рывок\"}"));
        assertEquals(10.0, out.get("cooldown").getAsDouble(), 1e-9);
        assertEquals("Рывок", out.get("display_name").getAsString());
        assertEquals(1.8, out.get("strength").getAsDouble(), 1e-9);
    }

    @Test
    void unknownTypeUntouched() {
        JsonObject in = json("{\"type\":\"mymod:hover\",\"speed\":3}");
        JsonObject out = PowerSchemas.materialize(in);
        assertEquals(in, out);
    }

    @Test
    void ravenousDoesNotGetAmplifier() {
        // amplifier/exhaustion are mutually exclusive: materializing "amplifier"
        // would switch the parser into a different mode
        JsonObject out = PowerSchemas.materialize(json("{\"type\":\"ravenous\"}"));
        assertFalse(out.has("amplifier"));
        assertEquals(0.5, out.get("exhaustion").getAsDouble(), 1e-9);
    }

    @Test
    void bareTypeAlsoWorks() {
        JsonObject out = PowerSchemas.materialize(json("{\"type\":\"blink\"}"));
        assertEquals(4.0, out.get("cooldown").getAsDouble(), 1e-9);
        assertEquals(10.0, out.get("range").getAsDouble(), 1e-9);
    }
}
