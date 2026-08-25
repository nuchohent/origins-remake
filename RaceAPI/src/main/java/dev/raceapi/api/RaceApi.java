package dev.raceapi.api;

import dev.raceapi.race.Power;
import dev.raceapi.race.PowerRegistry;
import dev.raceapi.race.Race;
import dev.raceapi.race.RaceRegistry;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;

/**
 * Convenient entry point for mod developers who want to define races and powers
 * as annotated classes:
 * <pre>{@code
 * @AutoRace("mymod:avian")
 * public class Avian implements Race { ... }
 *
 * @AutoPower("mymod:heavy_bones")
 * public class HeavyBones implements Power { ... }
 * }</pre>
 * Register them from your mod's constructor:
 * <pre>{@code
 * RaceApi.registerRaces(Avian.class);
 * RaceApi.registerPowers(HeavyBones.class);
 * }</pre>
 */
public final class RaceApi {

    private RaceApi() {
    }

    public static void registerRaces(Class<?>... raceClasses) {
        if (raceClasses == null) {
            throw new IllegalArgumentException("raceClasses cannot be null");
        }
        for (Class<?> clazz : raceClasses) {
            if (clazz == null) {
                throw new IllegalArgumentException("Race class element cannot be null");
            }
            if (!Race.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(clazz.getName() + " does not implement " + Race.class.getName());
            }
            AutoRace annotation = clazz.getAnnotation(AutoRace.class);
            if (annotation == null) {

                throw new IllegalArgumentException(clazz.getName() + " is missing @AutoRace annotation");
            }
            Identifier id = Identifier.tryParse(annotation.value());
            if (id == null) {
                throw new IllegalArgumentException("Invalid race id: " + annotation.value());
            }
            Race race = instantiate(clazz, Race.class);
            RaceRegistry.register(race);
        }
    }

    public static void registerPowers(Class<?>... powerClasses) {
        if (powerClasses == null) {
            throw new IllegalArgumentException("powerClasses cannot be null");
        }
        for (Class<?> clazz : powerClasses) {
            if (clazz == null) {
                throw new IllegalArgumentException("Power class element cannot be null");
            }
            if (!Power.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(clazz.getName() + " does not implement " + Power.class.getName());
            }
            AutoPower annotation = clazz.getAnnotation(AutoPower.class);
            if (annotation == null) {

                throw new IllegalArgumentException(clazz.getName() + " is missing @AutoPower annotation");
            }
            Identifier id = Identifier.tryParse(annotation.value());
            if (id == null) {
                throw new IllegalArgumentException("Invalid power id: " + annotation.value());
            }

            
            // Validate constructor upfront to fail fast during registration
            Constructor<?> constructor = getNoArgConstructor(clazz);

            PowerRegistry.register(id, () -> instantiate(constructor, clazz, Power.class));
        }
    }

    private static Constructor<?> getNoArgConstructor(Class<?> clazz) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Failed to register " + clazz.getName() + ": "
                    + "the class needs a no-arg constructor", e);
        } catch (SecurityException e) {
            throw new IllegalStateException("Security restriction preventing access to constructor of " + clazz.getName(), e);
        }
    }

    private static <T> T instantiate(Class<?> clazz, Class<T> type) {
        Constructor<?> constructor = getNoArgConstructor(clazz);
        return instantiate(constructor, clazz, type);
    }

    private static <T> T instantiate(Constructor<?> constructor, Class<?> clazz, Class<T> type) {
        try {

            return type.cast(constructor.newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate " + clazz.getName() + ": "

                    + "constructor invocation threw an exception", e);
        }
    }
}
