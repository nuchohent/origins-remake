package dev.raceapi.power;

import com.google.gson.JsonObject;
import dev.raceapi.race.Power;
import net.minecraft.resources.Identifier;

/**
 * Parses a {@link Power} from its JSON definition.
 * <p>
 * Implementations are registered against a power type name via
 * {@link PowerTypes#register(String, PowerParser)}. The {@code difficulty}
 * field is read by the framework and passed in, so parsers only deal with
 * their own type-specific parameters.
 */
@FunctionalInterface
public interface PowerParser {

    /**
     * Parses a power. Return {@code null} (and log the reason) if the JSON is
     * invalid for this power type.
     *
     * @param id         the power's registry id
     * @param json       the full power definition JSON
     * @param difficulty the difficulty already extracted from the JSON
     */
    Power parse(Identifier id, JsonObject json, int difficulty);
}
