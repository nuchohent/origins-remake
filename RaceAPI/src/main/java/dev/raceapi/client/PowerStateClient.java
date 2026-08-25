package dev.raceapi.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of toggle power states (ON/OFF), updated by
 * {@link dev.raceapi.network.PowerStatePayload} and read by the HUD renderer
 * to draw the "power: ON" indicators instead of chat spam.
 */
public final class PowerStateClient {

    private static final Map<String, Boolean> STATES = new ConcurrentHashMap<>();

    private PowerStateClient() {
    }

    public static void set(String powerId, boolean on) {
        STATES.put(powerId, on);
    }

    public static boolean isOn(String powerId) {
        return STATES.getOrDefault(powerId, false);
    }

    /** Power ids with a synced state (ON or OFF). */
    public static Map<String, Boolean> all() {
        return Map.copyOf(STATES);
    }

    /** Clears the mirrored state (leaving a world/server). */
    public static void reset() {
        STATES.clear();
    }
}
