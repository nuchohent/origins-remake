package dev.raceapi.race;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A playable race. Implement this interface directly for full control,
 * or use {@link SimpleRace} / JSON definitions for quick setup.
 * <p>
 * All methods that mutate player state ({@link #onSelect(ServerPlayer)},
 * {@link #onRemove(ServerPlayer)}) are invoked on the server thread.
 */
public interface Race {

    /** Unique registry id, e.g. {@code originsx:avian}. */
    Identifier getId();

    Component getDisplayName();

    Component getDescription();

    /** Powers granted by this race. */
    List<Power> getPowers();

    /** Icon shown in the selection GUI. Empty stack falls back to the player head. */
    default ItemStack getIcon() {
        return ItemStack.EMPTY;
    }

    /**
     * Difficulty rating of this race. Positive values mean harder to play,
     * negative mean easier. The GUI renders it as a visual scale.
     */
    default int getDifficulty() {
        return 0;
    }

    /** Scale of the player model in the selection preview and in game. */
    default float getScale() {
        return 1.0f;
    }

    /** Bounding box width in blocks. */
    default double getWidth() {
        return 0.6;
    }

    /** Bounding box height in blocks. */
    default double getHeight() {
        return 1.8;
    }

    /** Called when the race is granted to a player. */
    default void onSelect(ServerPlayer player) {
    }

    /** Called when the race is removed from a player. */
    default void onRemove(ServerPlayer player) {
    }

    /** Hide this race from the selection GUI (e.g. secret/dev races). */
    default boolean isHidden() {
        return false;
    }

    /** Whether this race can be selected by players in the GUI. */
    default boolean isPlayable() {
        return !isHidden();
    }

    /**
     * Maps a difficulty value to its translation key. Positive values mean
     * harder to play, negative mean easier.
     */
    static String difficultyKey(int difficulty) {
        if (difficulty <= -3) return "race.difficulty.very_easy";
        else if (difficulty < 0) return "race.difficulty.easy";
        else if (difficulty == 0) return "race.difficulty.neutral";
        else if (difficulty <= 2) return "race.difficulty.hard";
        else return "race.difficulty.very_hard";
    }

    /** Human-readable difficulty label based on difficulty value. */
    default Component getDifficultyText() {
        return Component.translatable(difficultyKey(getDifficulty()));
    }

    /** Number of powers this race has. */
    default int getPowerCount() {
        return getPowers().size();
    }
}
