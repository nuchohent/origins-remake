package dev.raceapi.race;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

/**
 * Global registry of all {@link Race} implementations.
 * <p>
 * Races registered in code ({@link #register(Race)}) always win over JSON-defined
 * races with the same id; JSON races are wiped and re-read on every datapack reload.
 */
public final class RaceRegistry {

    private static final Map<Identifier, Race> JAVA_RACES = new LinkedHashMap<>();
    private static final Map<Identifier, Race> JSON_RACES = new LinkedHashMap<>();

    private RaceRegistry() {
    }

    /** Registers a code-defined race. Throws on duplicate id. */
    public static void register(Race race) {
        Identifier id = race.getId();
        Race previous = JAVA_RACES.putIfAbsent(id, race);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate race id: " + id);
        }
    }

    /** Registers a JSON-defined race. Overwrites previous JSON definition, never Java ones. */
    public static void registerJson(Race race) {
        if (JAVA_RACES.containsKey(race.getId())) {
            // a JSON file shadowing a built-in race would vanish silently —
            // surface it through the same channel as other datapack problems
            dev.raceapi.data.ParseErrors.error(
                    "race '" + race.getId() + "' is defined in JSON but a Java-registered race with this id exists (Java wins)");
            return;
        }
        JSON_RACES.put(race.getId(), race);
    }

    /** Called by the datapack loader before re-reading JSON definitions. */
    public static void clearJson() {
        JSON_RACES.clear();
    }

    public static Optional<Race> get(Identifier id) {
        Race race = getOrNull(id);
        return race == null ? Optional.empty() : Optional.of(race);
    }

    @Nullable
    public static Race getOrNull(Identifier id) {
        Race race = JAVA_RACES.get(id);
        return race != null ? race : JSON_RACES.get(id);
    }

    public static boolean contains(Identifier id) {
        return JAVA_RACES.containsKey(id) || JSON_RACES.containsKey(id);
    }

    /** All registered races (code first, then JSON) in registration order. */
    public static Collection<Race> all() {
        Collection<Race> result = new ArrayList<>(JAVA_RACES.values());
        result.addAll(JSON_RACES.values());
        return result;
    }

    /** Races visible in the selection GUI (not hidden). */
    public static Collection<Race> playable() {
        return all().stream().filter(race -> !race.isHidden()).toList();
    }

    /**
     * All distinct origin layers across every registered race, in first-seen
     * order (default {@code "origin"} first when any race uses it).
     */
    public static Set<String> layers() {
        Set<String> layers = new LinkedHashSet<>();
        for (Race race : all()) {
            layers.add(race.getLayer());
        }
        return layers;
    }

    /** Races that belong to the given layer. */
    public static Collection<Race> layer(String layer) {
        return all().stream().filter(race -> race.getLayer().equals(layer)).toList();
    }

    /** Non-hidden races of the given layer (what the GUI shows for it). */
    public static Collection<Race> playable(String layer) {
        return layer(layer).stream().filter(race -> !race.isHidden()).toList();
    }
}
