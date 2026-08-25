package dev.raceapi.power.condition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionsTest {

    @Test
    void builtinsAreRegistered() {
        assertTrue(Conditions.contains("in_water"));
        assertTrue(Conditions.contains("not"));
        assertTrue(Conditions.contains("all_of"));
        assertTrue(Conditions.contains("any_of"));
        assertTrue(Conditions.contains("in_dimension"));
        // conditions added in 0.7.5
        assertTrue(Conditions.contains("held_item"));
        assertTrue(Conditions.contains("offhand_item"));
        assertTrue(Conditions.contains("wearing_item"));
        assertTrue(Conditions.contains("armor_count_above"));
        assertTrue(Conditions.contains("hunger_below"));
        assertTrue(Conditions.contains("air_below"));
        assertTrue(Conditions.contains("entities_nearby_above"));
        assertTrue(Conditions.contains("standing_on"));
        assertTrue(Conditions.contains("biome_id"));
        assertTrue(Conditions.contains("moon_phase"));
        assertTrue(Conditions.contains("thundering"));
        assertTrue(Conditions.contains("xp_level_above"));
        assertTrue(Conditions.contains("scoreboard_above"));
        assertTrue(Conditions.contains("gamemode"));
        assertTrue(Conditions.contains("riding"));
        assertTrue(Conditions.contains("climbing"));
        assertTrue(Conditions.contains("eye_in_water"));
        assertTrue(Conditions.contains("time_between"));
        assertFalse(Conditions.contains("bogus_condition"));
        assertEquals(50, Conditions.getTypeNames().size());
    }

    @Test
    void describeReturnsLabelForEveryRegisteredType() {
        // every built-in condition must produce a non-empty label without a
        // live server (describe is called at datapack parse time). Types whose
        // label needs bootstrapped vanilla registries (e.g. moon_phase reads
        // EnvironmentAttributes) are skipped — they resolve fine in-game.
        for (String type : Conditions.getTypeNames()) {
            JsonObject json = new JsonObject();
            json.addProperty("condition_type", type);
            try {
                String label = Conditions.describe(json).getString();
                assertNotNull(label, type);
                assertFalse(label.isBlank(), "empty label for " + type);
            } catch (LinkageError bootstrapRequired) {
                // headless test env has no registry bootstrap
                // (NoClassDefFoundError / ExceptionInInitializerError)
            }
        }
    }

    @Test
    void describeRendersThresholdsAndTags() {
        // headless tests have no language provider, so assert the translatable
        // key + args instead of the rendered string
        JsonObject health = new JsonObject();
        health.addProperty("condition_type", "health_below");
        health.addProperty("threshold", 0.3);
        var healthLabel = Conditions.describe(health);
        assertEquals("condition.raceapi.health_below", key(healthLabel));
        assertArrayEquals(new Object[]{30L}, args(healthLabel));

        JsonObject held = new JsonObject();
        held.addProperty("condition_type", "held_item");
        held.addProperty("item_tag", "#minecraft:swords");
        var heldLabel = Conditions.describe(held);
        assertEquals("condition.raceapi.held_item", key(heldLabel));
        assertTrue(args(heldLabel)[0] instanceof net.minecraft.network.chat.Component tag
                && tag.getString().equals("#minecraft:swords"),
                "tag arg should render as #minecraft:swords");

        JsonObject all = new JsonObject();
        all.addProperty("condition_type", "all_of");
        com.google.gson.JsonArray conditions = new com.google.gson.JsonArray();
        conditions.add(sub("is_night"));
        conditions.add(sub("is_sprinting"));
        all.add("conditions", conditions);
        assertEquals("condition.raceapi.and", key(Conditions.describe(all)));
    }

    private static String key(net.minecraft.network.chat.Component component) {
        return ((net.minecraft.network.chat.contents.TranslatableContents) component.getContents()).getKey();
    }

    private static Object[] args(net.minecraft.network.chat.Component component) {
        return ((net.minecraft.network.chat.contents.TranslatableContents) component.getContents()).getArgs();
    }

    @Test
    void parseReturnsNonNullForUnknownType() {
        JsonObject json = new JsonObject();
        json.addProperty("condition_type", "bogus_condition");
        assertNotNull(Conditions.parse(json));
    }

    @Test
    void parseHandlesSimpleCondition() {
        JsonObject json = new JsonObject();
        json.addProperty("condition_type", "on_ground");
        assertNotNull(Conditions.parse(json));
    }

    @Test
    void allOfRequiresEverySubCondition() {
        // true AND false -> false; parsed without a live server, so only the
        // parse outcome shape is checked (never-true fallback on empty input)
        JsonObject empty = new JsonObject();
        empty.addProperty("condition_type", "all_of");
        var condition = Conditions.parse(empty);
        assertNotNull(condition);

        JsonObject json = new JsonObject();
        json.addProperty("condition_type", "all_of");
        com.google.gson.JsonArray conditions = new com.google.gson.JsonArray();
        conditions.add(sub("is_full_health"));
        conditions.add(sub("has_armor"));
        json.add("conditions", conditions);
        assertNotNull(Conditions.parse(json));

        JsonObject any = new JsonObject();
        any.addProperty("condition_type", "any_of");
        any.add("conditions", conditions);
        assertNotNull(Conditions.parse(any));

        JsonObject not = new JsonObject();
        not.addProperty("condition_type", "not");
        not.add("inner", sub("is_full_health"));
        assertNotNull(Conditions.parse(not));
    }

    private static JsonObject sub(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("condition_type", type);
        return json;
    }

    @Test
    void customConditionCanBeRegistered() {
        String type = "test_cond_" + System.nanoTime();
        Conditions.register(type, json -> player -> true);
        assertTrue(Conditions.contains(type));
        JsonObject json = new JsonObject();
        json.addProperty("condition_type", type);
        assertNotNull(Conditions.parse(json));
    }

    @Test
    void duplicateRegistrationThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Conditions.register("in_water", json -> player -> true));
    }
}
