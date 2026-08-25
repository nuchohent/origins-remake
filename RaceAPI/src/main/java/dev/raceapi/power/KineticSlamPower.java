package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class KineticSlamPower implements Power {

    private static final int DEFAULT_COOLDOWN = 160;

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double radius;
    private final float damage;
    private final float selfDamageFraction;
    private final double knockbackStrength;
    private final Set<UUID> slamming = new HashSet<>();

    public KineticSlamPower(Identifier id, int difficulty, double radius,
                            float damage, float selfDamageFraction, double knockbackStrength) {
        this(id, difficulty, DEFAULT_COOLDOWN, radius, damage, selfDamageFraction, knockbackStrength);
    }

    public KineticSlamPower(Identifier id, int difficulty, int cooldownTicks, double radius,
                            float damage, float selfDamageFraction, double knockbackStrength) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.radius = radius;
        this.damage = damage;
        this.selfDamageFraction = selfDamageFraction;
        this.knockbackStrength = knockbackStrength;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.kinetic_slam.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.kinetic_slam.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (!player.onGround() && !player.isInWater()) {
            boolean clear = true;
            for (int i = 1; i <= 3; i++) {
                if (!player.level().isEmptyBlock(player.blockPosition().below(i))) {
                    clear = false;
                    break;
                }
            }
            if (!clear) return;
            if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) {
                return;
            }
            slamming.add(player.getUUID());
            player.setDeltaMovement(0, -3.5, 0);
            player.hurtMarked = true;
            dev.raceapi.network.CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
        }
    }

    @Override
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!slamming.contains(uuid)) return;

        if (player.onGround()) {
            slamming.remove(uuid);
            ServerLevel level = RaceUtils.serverLevel(player);
            Vec3 pos = player.position();

            AABB box = new AABB(pos.x - radius, pos.y - 1, pos.z - radius,
                    pos.x + radius, pos.y + 2, pos.z + radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive())) {
                double dist = target.distanceTo(player);
                float falloff = (float) (1.0 - dist / radius);
                if (falloff > 0) {
                    target.hurt(level.damageSources().playerAttack(player), damage * falloff);
                    Vec3 knockback = target.position().subtract(pos).normalize().scale(knockbackStrength);
                    knockback = new Vec3(knockback.x, knockbackStrength * 0.4, knockback.z);
                    target.push(knockback.x, knockback.y, knockback.z);
                    target.hurtMarked = true;
                }
            }

            float selfDmg = damage * selfDamageFraction;
            player.hurt(level.damageSources().playerAttack(player), selfDmg);

            level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z,
                    3, 1.0, 0.5, 1.0, 0.1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        slamming.remove(player.getUUID());
    }
}
