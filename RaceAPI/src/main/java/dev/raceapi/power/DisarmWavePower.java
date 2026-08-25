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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class DisarmWavePower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double range;
    private final int cooldownTicks;
    private final float hitDamage;
    private final double knockupStrength;

    public DisarmWavePower(Identifier id, int difficulty, double range,
                           int cooldownTicks, float hitDamage, double knockupStrength) {
        this.id = id;
        this.difficulty = difficulty;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.hitDamage = hitDamage;
        this.knockupStrength = knockupStrength;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.disarm_wave.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.disarm_wave.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (!player.onGround()) {
            player.sendSystemMessage(Component.translatable("power.raceapi.disarm_wave.need_ground"));
            return;
        }
        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;
        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);

        ServerLevel level = RaceUtils.serverLevel(player);
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle().normalize();

        for (double t = 1; t <= range; t += 0.5) {
            Vec3 check = pos.add(look.x * t, 0.1, look.z * t);
            level.sendParticles(ParticleTypes.CLOUD, check.x, check.y + 0.3, check.z,
                    1, 0.1, 0.1, 0.1, 0.02);

            net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
                    check.x - 0.8, check.y, check.z - 0.8,
                    check.x + 0.8, check.y + 1.5, check.z + 0.8);

            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    e -> e != player && e.isAlive())) {
                target.push(0, knockupStrength, 0);
                target.hurtMarked = true;
                target.hurt(level.damageSources().playerAttack(player), hitDamage);

                ItemStack offhand = target.getOffhandItem().copy();
                if (!offhand.isEmpty()) {
                    target.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    ItemEntity drop = new ItemEntity(level,
                            target.getX(), target.getY() + 0.5, target.getZ(), offhand);
                    level.addFreshEntity(drop);
                }

                level.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + 1, target.getZ(),
                        10, 0.5, 0.5, 0.5, 0.1);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f);
                player.sendSystemMessage(Component.translatable("power.raceapi.disarm_wave.hit",
                        target.getName().getString()));
                return;
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.5f, 0.5f);
    }
}
