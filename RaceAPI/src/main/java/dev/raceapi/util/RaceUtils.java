package dev.raceapi.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class RaceUtils {
    private RaceUtils() {
    }

    public static ServerLevel serverLevel(Entity entity) {
        return (ServerLevel) entity.level();
    }
}
