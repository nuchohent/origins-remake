package dev.raceapi.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link dev.raceapi.race.Race} implementation class so it can be
 * registered with {@link RaceApi#registerRaces(Class[])}.
 * The value must be a full id, e.g. {@code "mymod:avian"}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoRace {

    String value();
}
