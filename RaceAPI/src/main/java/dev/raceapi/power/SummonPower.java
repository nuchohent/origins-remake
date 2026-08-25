package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Active power that summons one or more entities near the player on key press.
 * The {@code entity} field specifies the entity type id (e.g. {@code minecraft:wolf}).
 */
public class SummonPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final Identifier entityTypeId;
    private final int count;

    public SummonPower(Identifier id, int difficulty, int cooldownTicks, Identifier entityTypeId, int count) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.entityTypeId = entityTypeId;
        this.count = Math.max(1, count);
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.summon.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.summon.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public boolean hasBinding() {
        return true;
    }

    @Override
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        if (level == null) {
            return;
        }
        var holder = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
        if (holder.isEmpty()) {
            return;
        }
        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) {
            return;
        }
        EntityType<?> entityType = holder.get().value();
        BlockPos center = player.blockPosition();
        for (int i = 0; i < count; i++) {
            double offsetX = (level.getRandom().nextDouble() - 0.5) * 3.0;
            double offsetZ = (level.getRandom().nextDouble() - 0.5) * 3.0;
            BlockPos spawnPos = center.offset((int) Math.floor(offsetX), 0, (int) Math.floor(offsetZ));
            entityType.spawn(level, spawnPos, EntitySpawnReason.MOB_SUMMONED);
        }
    }
}
