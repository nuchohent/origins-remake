package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.network.CooldownPayload;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PhaseDashPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final double distance;
    private final double dashVelocity;
    private final float contactDamage;

    public PhaseDashPower(Identifier id, int difficulty, int cooldownTicks,
                          double distance, double dashVelocity, float contactDamage) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.distance = distance;
        this.dashVelocity = dashVelocity;
        this.contactDamage = contactDamage;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.phase_dash.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.phase_dash.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        ServerLevel level = RaceUtils.serverLevel(player);
        // Dash straight ahead — ignore the look pitch so it never goes diagonal.
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0e-6) {
            horizontal = look;
        }
        horizontal = horizontal.normalize();
        Vec3 start = player.position().add(0, player.getBbHeight() / 2, 0);
        Vec3 end = start.add(horizontal.scale(distance));

        // Scan the path. Thin walls (a run of at most 1.5 blocks) are passed
        // through; thick walls block the dash.
        int solidRun = 0;
        boolean blocked = false;
        for (double t = 0; t <= distance; t += 0.5) {
            Vec3 check = start.add(horizontal.scale(t));
            net.minecraft.core.BlockPos checkPos = net.minecraft.core.BlockPos.containing(check);
            if (level.getBlockState(checkPos).blocksMotion()) {
                solidRun++;
                if (solidRun > 3) {
                    blocked = true;
                    break;
                }
            } else {
                solidRun = 0;
            }
        }

        // a blocked dash keeps its slow penalty but does not burn the cooldown
        if (blocked) {
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 30, 5, false, false, true));
            player.sendSystemMessage(Component.translatable("power.raceapi.phase_dash.blocked"));
            return;
        }

        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;

        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);
        player.teleportTo(end.x, end.y, end.z);
        player.fallDistance = 0;
        // keep some forward momentum after the phase (blocks/sec -> per tick)
        player.setDeltaMovement(player.getDeltaMovement().add(horizontal.scale(dashVelocity / 20.0)));
        player.hurtMarked = true;

        AABB pathBox = new AABB(start, end).expandTowards(horizontal.scale(1)).inflate(1);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, pathBox,
                e -> e != player && e.isAlive());
        for (LivingEntity target : targets) {
            target.hurt(level.damageSources().playerAttack(player), contactDamage);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false, true));
        }

        level.sendParticles(ParticleTypes.END_ROD, start.x, start.y, start.z,
                5, horizontal.x * 0.3, 0.1, horizontal.z * 0.3, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.4f);
    }
}
