package dev.raceapi.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link dev.raceapi.race.Power} implementation class so it can be
 * registered with {@link RaceApi#registerPowers(Class[])}.
 * The value must be a full id, e.g. {@code "mymod:fiery_body"}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoPower {

    String value();
}
