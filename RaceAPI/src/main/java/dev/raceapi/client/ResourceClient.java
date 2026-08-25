package dev.raceapi.client;

/**
 * Client-side mirror of the race resource pool, updated by
 * {@link dev.raceapi.network.ResourcePayload} and read by the HUD renderer.
 */
public final class ResourceClient {

    private static double current;
    private static double max;

    private ResourceClient() {
    }

    public static void set(double current, double max) {
        ResourceClient.current = current;
        ResourceClient.max = max;
    }

    public static double current() {
        return current;
    }

    public static double max() {
        return max;
    }

    public static boolean hasResource() {
        return max > 0;
    }

    /** Clears the mirrored state (leaving a world/server). */
    public static void reset() {
        current = 0;
        max = 0;
    }
}
