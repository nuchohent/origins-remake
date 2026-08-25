package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared cooldown tracking for active powers. The last-use game time is stored
 * on the player's persistent NBT so cooldowns survive a re-apply of the race.
 */
public final class PowerCooldowns {

    private PowerCooldowns() {
    }

    private static String key(Identifier id) {
        return "raceapi_cd_" + id;
    }

    private static String pendingKey(Identifier id) {
        return "raceapi_pend_" + id;
    }

    /**
     * Records the use and returns true if the power was ready, false if still cooling down.
     */
    public static boolean tryUse(ServerPlayer player, Identifier id, int cooldownTicks) {
        CompoundTag data = player.getPersistentData();
        long lastUsed = data.getLongOr(key(id), 0L);
        long gameTime = RaceUtils.serverLevel(player).getGameTime();
        if (gameTime - lastUsed < cooldownTicks) {
            return false;
        }
        data.putLong(key(id), gameTime);
        data.remove(pendingKey(id));
        return true;
    }

    /**
     * Records a use right now, ignoring any running cooldown. For penalties
     * that must extend the lockout from the current moment (e.g. a parry
     * missed its window) — {@link #tryUse} would reject the write while the
     * base cooldown is still running.
     */
    public static void forceUse(ServerPlayer player, Identifier id) {
        CompoundTag data = player.getPersistentData();
        data.putLong(key(id), RaceUtils.serverLevel(player).getGameTime());
        data.remove(pendingKey(id));
    }

    /** Remaining cooldown ticks for the player (0 = ready). */
    public static int remaining(ServerPlayer player, Identifier id, int cooldownTicks) {
        long lastUsed = player.getPersistentData().getLongOr(key(id), 0L);
        if (lastUsed == 0) {
            return 0;
        }
        long elapsed = RaceUtils.serverLevel(player).getGameTime() - lastUsed;
        return (int) Math.max(0, cooldownTicks - elapsed);
    }

    /**
     * Stored last-use tick (0 = never used); lets callers detect a fresh
     * {@link #tryUse}/{@link #forceUse} write made during a hook call.
     */
    public static long lastUseTick(ServerPlayer player, Identifier id) {
        return player.getPersistentData().getLongOr(key(id), 0L);
    }

    /**
     * Marks that {@code onKeyPressed} took over the press without entering
     * the cooldown yet (e.g. a charge-and-release ability waiting for its
     * second press to complete). The keybind pipeline keeps the resource cost
     * across all presses of one activation while this flag is set; follow-up
     * presses are not billed again.
     */
    public static void markPendingUse(ServerPlayer player, Identifier id) {
        player.getPersistentData().putBoolean(pendingKey(id), true);
    }

    /** Whether {@link #markPendingUse} is currently set for the power. */
    public static boolean hasPendingUse(ServerPlayer player, Identifier id) {
        return player.getPersistentData().getBoolean(pendingKey(id)).orElse(false);
    }

    /** Drops the pending-activation flag (e.g. the race was removed mid-activation). */
    public static void clearPendingUse(ServerPlayer player, Identifier id) {
        player.getPersistentData().remove(pendingKey(id));
    }
}
