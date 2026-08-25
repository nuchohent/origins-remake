package dev.raceapi.race;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Registry of {@link Power} types. Java mods register a factory here;
 * JSON race definitions reference powers by these ids.
 * <p>
 * Powers can also be defined entirely in JSON via
 * {@code data/<namespace>/raceapi/powers/*.json} (see {@code PowerDataLoader}).
 * Code-registered powers always win over JSON powers with the same id.
 */
public final class PowerRegistry {

    private static final Map<Identifier, Supplier<Power>> FACTORIES = new LinkedHashMap<>();
    private static final Map<Identifier, Power> JSON_POWERS = new LinkedHashMap<>();

    private PowerRegistry() {
    }

    public static void register(Identifier id, Supplier<Power> factory) {
        Supplier<Power> previous = FACTORIES.putIfAbsent(id, factory);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate power id: " + id);
        }
    }

    /** Registers a JSON-defined power. Overwrites previous JSON definition, never Java ones. */
    public static void registerJson(Identifier id, Power power) {
        if (FACTORIES.containsKey(id)) {
            // a JSON file shadowing a built-in power would vanish silently —
            // surface it through the same channel as other datapack problems
            dev.raceapi.data.ParseErrors.error(
                    "power '" + id + "' is defined in JSON but a Java-registered power with this id exists (Java wins)");
            return;
        }
        JSON_POWERS.put(id, power);
    }

    /** Called by the datapack loader before re-reading JSON power definitions. */
    public static void clearJson() {
        JSON_POWERS.clear();
    }

    public static Optional<Power> create(Identifier id) {
        return Optional.ofNullable(createOrNull(id));
    }

    @Nullable
    public static Power createOrNull(Identifier id) {
        Supplier<Power> factory = FACTORIES.get(id);
        if (factory != null) {
            return factory.get();
        }
        return JSON_POWERS.get(id);
    }

    public static boolean contains(Identifier id) {
        return FACTORIES.containsKey(id) || JSON_POWERS.containsKey(id);
    }

    /** All registered power ids (code first, then JSON) in registration order. */
    public static Collection<Identifier> getIds() {
        Collection<Identifier> result = new ArrayList<>(FACTORIES.keySet());
        result.addAll(JSON_POWERS.keySet());
        return result;
    }
}
