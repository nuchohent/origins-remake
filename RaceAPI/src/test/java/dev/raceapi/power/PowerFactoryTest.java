package dev.raceapi.power;

import com.google.gson.JsonObject;
import dev.raceapi.race.Power;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerFactoryTest {

    @Test
    void getStringReadsPrimitiveAndFallsBack() {
        JsonObject json = new JsonObject();
        json.addProperty("name", "dash");
        assertEquals("dash", PowerFactory.getString(json, "name", "fallback"));
        assertEquals("fallback", PowerFactory.getString(json, "missing", "fallback"));
    }

    @Test
    void getIntReadsPrimitiveAndFallsBack() {
        JsonObject json = new JsonObject();
        json.addProperty("value", 7);
        assertEquals(7, PowerFactory.getInt(json, "value", 0));
        assertEquals(42, PowerFactory.getInt(json, "missing", 42));
    }

    @Test
    void getDoubleReadsPrimitiveAndFallsBack() {
        JsonObject json = new JsonObject();
        json.addProperty("value", 2.5);
        assertEquals(2.5, PowerFactory.getDouble(json, "value", 1.0));
        assertEquals(1.0, PowerFactory.getDouble(json, "missing", 1.0));
    }

    @Test
    void getBoolReadsPrimitiveAndFallsBack() {
        JsonObject json = new JsonObject();
        json.addProperty("value", true);
        assertEquals(true, PowerFactory.getBool(json, "value", false));
        assertEquals(false, PowerFactory.getBool(json, "missing", false));
    }

    @Test
    void getSecondsConvertsSecondsToTicks() {
        assertEquals(60, PowerFactory.getSeconds(new JsonObject(), "cooldown", 3.0));
        assertEquals(0, PowerFactory.getSeconds(new JsonObject(), "cooldown", 0.0));
        JsonObject json = new JsonObject();
        json.addProperty("cooldown", 2.5);
        assertEquals(50, PowerFactory.getSeconds(json, "cooldown", 3.0));
    }

    @Test
    void parseOperationMapsKnownAndDefaults() {
        assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE,
                PowerFactory.parseOperation("add_value"));
        assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                PowerFactory.parseOperation("add_multiplied_base"));
        assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                PowerFactory.parseOperation("add_multiplied_total"));
        assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE,
                PowerFactory.parseOperation("bogus"));
    }

    @Test
    void createParsesSimplePowerType() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "safe_landing");
        json.addProperty("difficulty", 1);
        Power power = PowerFactory.create(Identifier.fromNamespaceAndPath("test", "feather"), json);
        assertNotNull(power);
        assertEquals("test", power.getId().getNamespace());
        assertEquals("feather", power.getId().getPath());
        assertEquals(1, power.getDifficulty());
        assertNotNull(power.getDisplayName());
    }

    @Test
    void createRejectsUnknownType() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "not_a_real_type");
        assertNull(PowerFactory.create(Identifier.fromNamespaceAndPath("test", "x"), json));
    }

    @Test
    void createRejectsMissingType() {
        assertNull(PowerFactory.create(Identifier.fromNamespaceAndPath("test", "x"), new JsonObject()));
    }

    @Test
    void createWrapsInNamedPowerWhenDisplayNameProvided() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "safe_landing");
        json.addProperty("display_name", "Feather");
        Power power = PowerFactory.create(Identifier.fromNamespaceAndPath("test", "feather"), json);
        assertNotNull(power);
        assertEquals("Feather", power.getDisplayName().getString());
        assertNotNull(power.getWrapped());
        assertTrue(power.getWrapped() instanceof SafeLandingPower);
    }
}
