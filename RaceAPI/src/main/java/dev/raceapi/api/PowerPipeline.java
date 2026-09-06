package dev.raceapi.api;

import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Extension point that lets other mods substitute the <em>effective</em> power
 * list of a race for a specific player, without touching the race definition
 * itself. Typical use: a progression addon that locks some powers behind
 * unlocks and attaches weaker "tiered" copies of the unlocked ones.
 * <p>
 * The returned list should generally keep the size and order of
 * {@code race.getPowers()} so bind-slot indices stay stable for clients
 * (wrap unavailable powers in a no-op {@code Power} instead of dropping them).
 * <p>
 * Install once from your mod constructor:
 * <pre>{@code
 * PowerPipeline.setEffectivePowers((player, race) -> myWrappedList);
 * }</pre>
 * Only one hook can be installed; the last registration wins. When no hook is
 * installed every flow behaves exactly as before (the plain race powers).
 */
public final class PowerPipeline {

    /** Returns the powers that should actually be attached/queried. */
    public interface EffectivePowers extends BiFunction<ServerPlayer, Race, List<Power>> {
    }

    @Nullable
    private static volatile EffectivePowers hook;

    private PowerPipeline() {
    }

    /** Installs (or replaces) the global effective-powers hook. Pass {@code null} to uninstall. */
    public static void setEffectivePowers(@Nullable EffectivePowers effectivePowers) {
        hook = effectivePowers;
    }

    /** Currently installed hook, or {@code null}. */
    @Nullable
    public static EffectivePowers getHook() {
        return hook;
    }

    /**
     * The powers that should be used for {@code player} and {@code race}:
     * the hook's answer if one is installed, otherwise {@code race.getPowers()}.
     */
    public static List<Power> effective(ServerPlayer player, Race race) {
        EffectivePowers h = hook;
        return h == null ? race.getPowers() : h.apply(player, race);
    }
}
