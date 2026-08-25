package dev.raceapi.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects human-readable datapack load errors so they can be inspected with
 * {@code /raceapi validate}. Cleared at the start of the apply phase of every
 * datapack reload (prepare phases run in parallel and may record errors
 * concurrently, so the list is synchronized) and populated by the data loaders
 * and the power/condition parsers.
 */
public final class ParseErrors {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParseErrors.class);
    private static final List<String> ERRORS = Collections.synchronizedList(new ArrayList<>());

    private ParseErrors() {
    }

    /** Clears all collected errors. Called at the start of the reload apply phase. */
    public static void clear() {
        ERRORS.clear();
    }

    /** Records an error message and logs it at ERROR level. */
    public static void error(String message) {
        ERRORS.add(message);
        LOGGER.error("[raceapi datapack] {}", message);
    }

    /** Whether any errors were recorded since the last clear. */
    public static boolean hasErrors() {
        return !ERRORS.isEmpty();
    }

    /** Immutable snapshot of all recorded errors, in order. */
    public static List<String> all() {
        synchronized (ERRORS) {
            return Collections.unmodifiableList(new ArrayList<>(ERRORS));
        }
    }
}
