package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Magnetically pulls dropped items and experience orbs toward the player.
 * Vanilla has no way to grant item magnetism as a permanent trait.
 */
public class MagnetPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double radius;

    public MagnetPower(Identifier id, int difficulty) {
        this(id, difficulty, 4.0);
    }

    public MagnetPower(Identifier id, int difficulty, double radius) {
        this.id = id;
        this.difficulty = difficulty;
        this.radius = radius;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power." + id.getNamespace() + "." + id.getPath() + ".desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (player.tickCount % 5 != 0) {
            return;
        }
        var level = RaceUtils.serverLevel(player);
        AABB box = player.getBoundingBox().inflate(radius);
        Vec3 target = player.position().add(0, 1, 0);

        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay())) {
            if (blockedFromPlayer(player, item.position())) {
                continue;
            }
            Vec3 delta = target.subtract(item.position());
            if (delta.lengthSqr() < 0.0001) {
                continue;
            }
            item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(delta.normalize().scale(0.35)));
            item.needsSync = true;
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box,
                ExperienceOrb::isAlive)) {
            if (blockedFromPlayer(player, orb.position())) {
                continue;
            }
            Vec3 delta = target.subtract(orb.position());
            if (delta.lengthSqr() < 0.0001) {
                continue;
            }
            orb.setDeltaMovement(orb.getDeltaMovement().scale(0.5).add(delta.normalize().scale(0.3)));
            orb.needsSync = true;
        }
    }

    private static boolean blockedFromPlayer(ServerPlayer player, Vec3 targetPos) {
        return RaceUtils.serverLevel(player)
                .clip(new ClipContext(player.getEyePosition(), targetPos,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.BLOCK;
    }
}
