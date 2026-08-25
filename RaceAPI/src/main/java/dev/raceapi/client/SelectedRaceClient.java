package dev.raceapi.client;

import dev.raceapi.race.Race;
import dev.raceapi.race.RaceRegistry;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side mirror of the currently selected race, updated from
 * {@link dev.raceapi.network.SyncRacePayload}.
 */
public final class SelectedRaceClient {

    @Nullable
    private static Identifier current;

    /** Whether the server has already sent the race-sync payload on this connection. */
    private static boolean synced;

    private SelectedRaceClient() {
    }

    public static void set(@Nullable Identifier raceId) {
        current = raceId;
        synced = true;
    }

    /** Clears the cached race when leaving a world, so the next join waits for a fresh sync. */
    public static void reset() {
        current = null;
        synced = false;
        CooldownClient.clear();
        PowerStateClient.reset();
    }

    public static boolean isSynced() {
        return synced;
    }

    @Nullable
    public static Identifier getOrNull() {
        return current;
    }

    @Nullable
    public static Race getRace() {
        return current == null ? null : RaceRegistry.getOrNull(current);
    }

    public static boolean hasRace() {
        return current != null && RaceRegistry.contains(current);
    }
}
