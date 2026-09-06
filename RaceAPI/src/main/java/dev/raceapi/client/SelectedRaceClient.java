package dev.raceapi.client;

import dev.raceapi.race.Race;
import dev.raceapi.race.RaceRegistry;
import dev.raceapi.race.Selection;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side mirror of the player's selected races, updated from
 * {@link dev.raceapi.network.SyncRacePayload}. Stores one race id per origin
 * layer; {@link #getRace()} exposes the composite view of the whole selection
 * so single-race consumers keep working.
 */
public final class SelectedRaceClient {

    private static final Map<String, Identifier> LAYERS = new HashMap<>();

    /** Whether the server has already sent the race-sync payload on this connection. */
    private static boolean synced;

    private SelectedRaceClient() {
    }

    /** Replaces the whole selection with a layer id → race id map. */
    public static void set(Map<String, String> layerToId) {
        LAYERS.clear();
        if (layerToId != null) {
            for (Map.Entry<String, String> entry : layerToId.entrySet()) {
                Identifier id = entry.getValue() == null ? null : Identifier.tryParse(entry.getValue());
                if (id != null) {
                    LAYERS.put(entry.getKey(), id);
                }
            }
        }
        synced = true;
    }

    /** Legacy single-race setter: selects the race in its own layer. */
    public static void set(@Nullable Identifier raceId) {
        LAYERS.clear();
        if (raceId != null) {
            Race race = RaceRegistry.getOrNull(raceId);
            LAYERS.put(race == null ? "origin" : race.getLayer(), raceId);
        }
        synced = true;
    }

    /** Clears the cached selection when leaving a world, so the next join waits for a fresh sync. */
    public static void reset() {
        LAYERS.clear();
        synced = false;
        CooldownClient.clear();
        PowerStateClient.reset();
    }

    public static boolean isSynced() {
        return synced;
    }

    /** The race id of the first selected layer, or null when nothing is selected. */
    @Nullable
    public static Identifier getOrNull() {
        return LAYERS.isEmpty() ? null : LAYERS.values().iterator().next();
    }

    /** The id selected in a specific layer, or null. */
    @Nullable
    public static Identifier selectedIn(String layer) {
        return LAYERS.get(layer);
    }

    /** Every selected layer with its resolved race id. */
    public static Map<String, Identifier> allLayers() {
        return new HashMap<>(LAYERS);
    }

    /**
     * The composite race of the whole selection, or null when the player has
     * no race selected yet.
     */
    @Nullable
    public static Race getRace() {
        return buildSelection().asRace();
    }

    /** The resolved per-layer selection. */
    public static Selection getSelection() {
        return buildSelection();
    }

    public static boolean hasRace() {
        return !LAYERS.isEmpty();
    }

    private static Selection buildSelection() {
        Selection selection = new Selection();
        for (Map.Entry<String, Identifier> entry : LAYERS.entrySet()) {
            Race race = RaceRegistry.getOrNull(entry.getValue());
            if (race != null) {
                selection.put(race.getLayer(), race);
            }
        }
        return selection;
    }
}