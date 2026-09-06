package dev.originsx.skilltree.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Produces the weakened tier copies of a power by numerically scaling a deep
 * copy of its inline JSON definition. Only whitelisted magnitude keys are
 * multiplied; cooldown-family keys are divided instead (a weaker power recharges
 * slower). Structural keys (ids, types, slots, conditions...) are never touched,
 * so unknown or third-party power types degrade gracefully to full strength.
 */
public final class TierScaler {

    /** Power strength at each unlockable tier (tier 3 uses the original power). */
    public static double tier1() {
        return dev.originsx.skilltree.SkillTreeConfig.SPEC.isLoaded()
                ? dev.originsx.skilltree.SkillTreeConfig.INSTANCE.tier1Factor.get() : 0.34;
    }

    public static double tier2() {
        return dev.originsx.skilltree.SkillTreeConfig.SPEC.isLoaded()
                ? dev.originsx.skilltree.SkillTreeConfig.INSTANCE.tier2Factor.get() : 0.67;
    }

    /** Keys whose value scales with power strength. */
    private static final String[] MAGNITUDE = {
            "amount", "damage", "heal", "strength", "boost", "radius", "aura_radius",
            "fraction", "reduction", "exhaustion", "duration", "fire_ticks",
            "slowness_duration", "jump_disable_ticks", "multiplier", "damage_multiplier",
            "fall_multiplier", "sink_speed", "count", "reach_multiplier", "distance",
            "contact_damage", "self_damage", "self_damage_fraction", "aura_damage",
            "hit_damage", "pull_damage", "pull_speed", "shock_damage", "dash_velocity",
            "force", "knockback_strength", "knockup_strength", "pushback_strength",
            "darkness_duration", "weakness_duration", "stun_duration", "effect_duration",
            "damage_per_tick", "regen_punishment", "redirect_fraction",
            "health_loss_fraction", "range", "pick_range", "parry_window"
    };

    /** Keys whose value scales inversely with power strength (seconds/ticks). */
    private static final String[] INVERSE = {
            "cooldown", "base_cooldown", "miss_cooldown", "interval", "shock_cooldown"
    };

    private TierScaler() {
    }

    /** Returns a deep copy of {@code power} scaled for the given tier factor. */
    public static JsonObject scale(JsonObject power, double factor) {
        if (factor >= 1.0) {
            return deepCopy(power);
        }
        JsonObject out = deepCopy(power);
        scaleObject(out, factor);
        return out;
    }

    private static void scaleObject(JsonObject json, double factor) {
        for (String key : MAGNITUDE) {
            scaleKey(json, key, factor);
        }
        for (String key : INVERSE) {
            scaleKey(json, key, 1.0 / factor);
        }
    }

    private static void scaleKey(JsonObject json, String key, double factor) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return;
        }
        JsonPrimitive primitive = json.getAsJsonPrimitive(key);
        if (!primitive.isNumber() || primitive.isBoolean()) {
            return;
        }
        double value = primitive.getAsDouble();
        double scaled = value * factor;
        if (isIntegral(primitive)) {
            // Round away from zero so every unlocked tier keeps a visible share
            // of the original magnitude (plain rounding collapsed small values:
            // 2 * 0.34 and 2 * 0.67 both rounded to 1, making tiers identical).
            long rounded = Math.round(Math.ceil(Math.abs(scaled) - 1.0e-9)) * (scaled < 0 ? -1 : 1);
            json.addProperty(key, rounded);
        } else {
            scaled = Math.round(scaled * 1000.0) / 1000.0;
            // Never scale a positive magnitude down to zero — keep it unscaled
            if (scaled == 0 && value > 0) {
                scaled = value;
            }
            json.addProperty(key, scaled);
        }
    }

    private static boolean isIntegral(JsonPrimitive primitive) {
        return primitive.getAsDouble() == Math.floor(primitive.getAsDouble())
                && !primitive.isBoolean();
    }

    private static JsonObject deepCopy(JsonObject json) {
        JsonObject out = new JsonObject();
        for (var entry : json.entrySet()) {
            out.add(entry.getKey(), copy(entry.getValue()));
        }
        return out;
    }

    private static JsonElement copy(JsonElement element) {
        if (element.isJsonObject()) {
            return deepCopy(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                out.add(copy(child));
            }
            return out;
        }
        return element;
    }
}
