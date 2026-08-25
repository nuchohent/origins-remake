package dev.raceapi.client;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mirror of active power cooldowns, fed by
 * {@link dev.raceapi.network.CooldownPayload}. Only touched on the client thread.
 */
public final class CooldownClient {

    private static final Map<String, long[]> ACTIVE = new HashMap<>(); // powerId -> {endTick, totalTicks}

    private CooldownClient() {
    }

    public static void set(String powerId, int cooldownTicks, int remainingTicks) {
        long gameTime = gameTime();
        ACTIVE.put(powerId, new long[]{gameTime + remainingTicks, cooldownTicks});
    }

    /** Fraction of cooldown still remaining (0.0 = ready, 1.0 = just used). */
    public static float remaining(String powerId) {
        long[] data = ACTIVE.get(powerId);
        if (data == null) {
            return 0.0f;
        }
        long remainingTicks = data[0] - gameTime();
        if (remainingTicks <= 0) {
            ACTIVE.remove(powerId);
            return 0.0f;
        }
        if (data[1] <= 0) {
            return 1.0f;
        }
        return Math.min(1.0f, (float) remainingTicks / (float) data[1]);
    }

    /** Currently tracked power ids (may include already-expired entries). */
    public static Set<String> active() {
        return ACTIVE.keySet();
    }

    private static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? mc.level.getGameTime() : 0;
    }

    /** Drops all cooldown entries (used when leaving a world/server). */
    public static void clear() {
        ACTIVE.clear();
    }
}
