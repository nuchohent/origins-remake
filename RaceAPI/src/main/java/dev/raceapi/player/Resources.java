package dev.raceapi.player;

import dev.raceapi.api.PowerPipeline;
import dev.raceapi.network.ResourcePayload;
import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import dev.raceapi.power.ResourcePower;

/**
 * Server-authoritative race resource pool (mana/stamina). The current value is
 * persisted in the player's {@code PlayerPersisted} data so it survives relogs
 * AND death (NeoForge copies that subtag onto the respawned player); the pool
 * size comes from the race's first effective {@link ResourcePower}. Every
 * change is synced to the client for the HUD.
 * <p>
 * The value is only reset when the race actually changes (see
 * {@code RaceManager.setRace} via {@link #clear}) — logout, respawn and reload
 * re-attach cycles keep the pool intact.
 */
public final class Resources {

    private static final String CURRENT_KEY = "raceapi_resource_current";
    private static final String SYNCED_KEY = "raceapi_resource_synced";

    private Resources() {
    }

    /**
     * The {@code PlayerPersisted} subtag: unlike the persistent-data root it
     * is copied to the respawned player on death by NeoForge.
     */
    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = root.getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, tag);
        }
        return tag;
    }

    /** The resource pool size granted by the player's race (0 = no resource). */
    public static double max(ServerPlayer player) {
        dev.raceapi.race.Selection selection = RaceManager.getSelection(player);
        if (selection.isEmpty()) {
            return 0;
        }
        for (Race race : selection.races()) {
            for (Power power : PowerPipeline.effective(player, race)) {
                if (power.getWrapped() instanceof ResourcePower resource) {
                    return resource.getMax();
                }
            }
        }
        return 0;
    }

    /** The stored value clamped into [0, max]; 0 when the race has no resource. */
    public static double current(ServerPlayer player) {
        double max = max(player);
        if (max <= 0) {
            return 0;
        }
        double stored = persisted(player).getDoubleOr(CURRENT_KEY, max);
        return Math.max(0.0, Math.min(max, stored));
    }

    /** Whether the race grants a resource pool at all. */
    public static boolean hasResource(ServerPlayer player) {
        return max(player) > 0;
    }

    /**
     * Spends {@code amount} units; returns false (and spends nothing) when the
     * player cannot afford it.
     */
    public static boolean tryConsume(ServerPlayer player, double amount) {
        double cur = current(player);
        if (cur < amount) {
            return false;
        }
        set(player, cur - amount);
        return true;
    }

    /** Adds regen, clamped to the pool. */
    public static void add(ServerPlayer player, double amount) {
        set(player, current(player) + amount);
    }

    /** Forces a full HUD resync (e.g. after respawn or race change). */
    public static void sync(ServerPlayer player) {
        ResourcePayload.send(player, current(player), max(player));
    }

    private static void set(ServerPlayer player, double value) {
        CompoundTag data = persisted(player);
        double max = max(player);
        if (max <= 0) {
            data.putDouble(CURRENT_KEY, 0.0);
            return;
        }
        double clamped = Math.max(0.0, Math.min(max, value));
        data.putDouble(CURRENT_KEY, clamped);
        // throttle: only resend when the integer display value changes
        int shown = (int) Math.floor(clamped);
        int sent = data.getInt(SYNCED_KEY).orElse(-1);
        if (shown != sent) {
            data.putInt(SYNCED_KEY, shown);
            ResourcePayload.send(player, clamped, max);
        }
    }

    /** Drops stored state (called when a race is removed). */
    public static void clear(ServerPlayer player) {
        persisted(player).putDouble(CURRENT_KEY, 0.0);
    }
}
