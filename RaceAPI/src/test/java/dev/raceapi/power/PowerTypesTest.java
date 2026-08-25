package dev.raceapi.power;

import com.google.gson.JsonObject;
import dev.raceapi.race.Power;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerTypesTest {

    @Test
    void builtinsAreRegistered() {
        assertTrue(PowerTypes.contains("dash"));
        assertTrue(PowerTypes.contains("safe_landing"));
        assertTrue(PowerTypes.contains("conditional"));
        assertTrue(PowerTypes.contains("disarm_wave"));
        assertFalse(PowerTypes.contains("bogus_type"));
        assertTrue(PowerTypes.getTypeNames().size() >= 40);
    }

    @Test
    void dynamicDisplayTypesAreFlagged() {
        assertTrue(PowerTypes.isDynamicDisplay("attribute"));
        assertTrue(PowerTypes.isDynamicDisplay("status_effect"));
        assertTrue(PowerTypes.isDynamicDisplay("conditional"));
        assertFalse(PowerTypes.isDynamicDisplay("dash"));
        assertFalse(PowerTypes.isDynamicDisplay("not_registered"));
    }

    @Test
    void customTypeCanBeRegistered() {
        String type = "test_custom_" + System.nanoTime();
        PowerParser parser = (id, json, difficulty) -> new SafeLandingPower(id, difficulty);
        PowerTypes.register(type, parser);
        assertTrue(PowerTypes.contains(type));
        assertNotNull(PowerTypes.get(type));
        Power power = PowerTypes.get(type).parse(
                Identifier.fromNamespaceAndPath("test", "custom"), new JsonObject(), 2);
        assertEquals(2, power.getDifficulty());
    }

    @Test
    void duplicateRegistrationThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerTypes.register("dash", (id, json, difficulty) -> null));
    }
}
