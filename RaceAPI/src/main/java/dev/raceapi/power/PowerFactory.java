package dev.raceapi.power;

import com.google.gson.JsonObject;
import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PowerFactory {

    static final Logger LOGGER = LoggerFactory.getLogger(PowerFactory.class);

    private PowerFactory() {
    }

    /**
     * Parses a power definition. The power type name is looked up in
     * {@link PowerTypes}; unknown types log an error and return {@code null}.
     */
    public static Power create(Identifier id, JsonObject json) {
        String type = getString(json, "type", "");
        if (type.isEmpty()) {
            LOGGER.error("Power {} is missing a 'type' field", id);
            return null;
        }
        String path = type;
        if (type.contains(":")) {
            Identifier typeId = Identifier.tryParse(type);
            path = typeId != null ? typeId.getPath() : type;
        }
        int difficulty = getInt(json, "difficulty", 0);

        PowerParser parser = PowerTypes.get(path);
        if (parser == null) {
            LOGGER.error("Unknown power type '{}' for power {}", type, id);
            return null;
        }
        Power power;
        try {
            power = parser.parse(id, json, difficulty);
        } catch (Exception e) {
            LOGGER.error("Failed to parse power {} (type '{}'): {}", id, type, e.getMessage());
            return null;
        }
        if (power == null) {
            return null;
        }
        // Active powers may declare a resource (mana/stamina) cost; the
        // decorator is checked by the keybind pipeline on every activation.
        double cost = getDouble(json, "cost", 0.0);
        if (cost > 0 && power.hasBinding()) {
            power = new CostedPower(power, cost);
        }
        // "toggle": true turns the active into an ON/OFF switch instead of a
        // one-shot trigger (the switch charges the cost itself on activation).
        // OverdrivePower implements its own toggle - wrapping it would swallow
        // its onKeyPressed and the aura would never turn on.
        if (getBool(json, "toggle", false) && power.hasBinding()
                && !(power instanceof ToggledPower) && !(power.getWrapped() instanceof OverdrivePower)) {
            power = new ToggledPower(power);
        }
        String displayName = getString(json, "display_name", "");
        String description = getString(json, "description", "");
        int bindSlot = getInt(json, "bind_slot", -1);
        boolean dynamic = PowerTypes.isDynamicDisplay(path);
        if (displayName.isEmpty() && description.isEmpty() && bindSlot < 0) {
            if (!dynamic) {
                return new NamedPower(power,
                        Component.translatable("power.raceapi." + path + ".name"),
                        Component.translatable("power.raceapi." + path + ".desc"));
            }
            return power;
        }
        // Inline powers get an auto-generated id (e.g. mypack:test_power_0), so
        // a fallback that builds the key from the power id would show a raw key.
        // For fixed-type powers use the type translation; for dynamic types
        // (attribute/status_effect/conditional) keep the delegate's own text.
        return new NamedPower(power,
                displayName.isEmpty()
                        ? (dynamic ? power.getDisplayName()
                                : Component.translatable("power.raceapi." + path + ".name"))
                        : Component.literal(displayName),
                description.isEmpty()
                        ? (dynamic ? power.getDescription()
                                : Component.translatable("power.raceapi." + path + ".desc"))
                        : Component.literal(description),
                bindSlot);
    }

    // ---- JSON helpers (package-visible for PowerTypes parsers) ----

    static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }

    static double getDouble(JsonObject json, String key, double fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
    }

    static boolean getBool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
    }

    /**
     * Reads a duration/cooldown field expressed in SECONDS and returns ticks.
     * All user-facing time fields use seconds; only the engine works in ticks.
     */
    static int getSeconds(JsonObject json, String key, double defaultSeconds) {
        double seconds = getDouble(json, key, defaultSeconds);
        return (int) Math.round(seconds * 20.0);
    }

    static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> parseAttribute(String id) {
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            return null;
        }
        java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute>> holder =
                net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(location);
        if (holder.isPresent()) {
            return holder.get();
        }
        // 1.21.x registers attributes under a "generic."/"player." prefix
        // (e.g. "generic.max_health"), while the creator and older datapacks
        // store unprefixed ids like "minecraft:max_health". Fall back to the
        // prefixed keys so existing races keep their attributes working.
        for (String prefix : new String[]{"generic", "player"}) {
            Identifier prefixed = Identifier.fromNamespaceAndPath(location.getNamespace(), prefix + "." + location.getPath());
            java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute>> candidate =
                    net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(prefixed);
            if (candidate.isPresent()) {
                return candidate.get();
            }
        }
        return null;
    }

    static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> parseEffect(String id) {
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            return null;
        }
        java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.effect.MobEffect>> holder =
                net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(location);
        return holder.orElse(null);
    }

    static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation parseOperation(String operation) {
        return switch (operation) {
            case "add_multiplied_base" -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
        };
    }
}
