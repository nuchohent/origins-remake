package dev.raceapi.power;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.network.CooldownPayload;
import dev.raceapi.race.Power;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LifeLinkPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final int cooldownTicks;
    private final int durationTicks;
    private final float redirectFraction;
    private final int pickRange;
    private final Map<UUID, LinkData> links = new HashMap<>();

    public LifeLinkPower(Identifier id, int difficulty, int cooldownTicks,
                         int durationTicks, float redirectFraction, int pickRange) {
        this.id = id;
        this.difficulty = difficulty;
        this.cooldownTicks = cooldownTicks;
        this.durationTicks = durationTicks;
        this.redirectFraction = redirectFraction;
        this.pickRange = pickRange;
    }

    @Override public Identifier getId() { return id; }
    @Override public Component getDisplayName() { return Component.translatable("power.raceapi.life_link.name"); }
    @Override public Component getDescription() { return Component.translatable("power.raceapi.life_link.desc"); }
    @Override public int getDifficulty() { return difficulty; }
    @Override public boolean hasBinding() { return true; }
    @Override public int getCooldownTicks() { return cooldownTicks; }

    @Override
    public int getRemainingCooldownTicks(ServerPlayer player) {
        return PowerCooldowns.remaining(player, id, cooldownTicks);
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        if (!PowerCooldowns.tryUse(player, id, cooldownTicks)) return;
        CooldownPayload.send(player, id.toString(), cooldownTicks, cooldownTicks);

        ServerLevel level = RaceUtils.serverLevel(player);

        // Entity.pick() only raycasts blocks; it can never return an ENTITY
        // hit result. Use ProjectileUtil against the eye-line instead, and
        // fall back to the block pick only to cap the search distance.
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 viewVec = player.getViewVector(1.0f);
        Vec3 reach = eyePos.add(viewVec.x * pickRange, viewVec.y * pickRange, viewVec.z * pickRange);
        HitResult entityHit = ProjectileUtil.getEntityHitResult(player, eyePos, reach,
                player.getBoundingBox().expandTowards(viewVec.scale(pickRange)).inflate(1.0),
                e -> e instanceof LivingEntity && e != player && !e.isSpectator(),
                pickRange * pickRange);

        LivingEntity target;
        if (entityHit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            target = le;
        } else {
            player.sendSystemMessage(Component.translatable("power.raceapi.life_link.no_target"));
            return;
        }

        links.put(player.getUUID(), new LinkData(target.getUUID(), durationTicks));
        if (target instanceof ServerPlayer sp) {
            links.put(sp.getUUID(), new LinkData(player.getUUID(), durationTicks));
        }

        level.sendParticles(ParticleTypes.HEART,
                (player.getX() + target.getX()) / 2,
                (player.getY() + target.getY()) / 2 + 1,
                (player.getZ() + target.getZ()) / 2,
                10, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.5f);

        player.sendSystemMessage(Component.translatable("power.raceapi.life_link.linked",
                target.getName().getString()));
    }

    @Override
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        LinkData link = links.get(uuid);
        if (link == null) return;

        link.ticksLeft--;
        if (link.ticksLeft <= 0) {
            links.remove(uuid);
            links.remove(link.partnerId);
            player.sendSystemMessage(Component.translatable("power.raceapi.life_link.broken"));
            return;
        }

        ServerLevel level = RaceUtils.serverLevel(player);
        if (level.getServer().getPlayerList().getPlayer(link.partnerId) instanceof ServerPlayer partner) {
            if (level.getGameTime() % 10 == 0) {
                double mx = (player.getX() + partner.getX()) / 2;
                double my = (player.getY() + partner.getY()) / 2 + 1;
                double mz = (player.getZ() + partner.getZ()) / 2;
                level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), mx, my, mz, 2, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        float redirected = redirectDamage(player, amount, source);
        return Math.max(0.0f, amount - redirected);
    }

    public float redirectDamage(ServerPlayer player, float amount, DamageSource source) {
        LinkData link = links.get(player.getUUID());
        if (link == null || link.ticksLeft <= 0) return 0;

        if (source.getEntity() instanceof ServerPlayer sp && sp.getUUID().equals(link.partnerId)) {
            return 0;
        }

        ServerLevel level = RaceUtils.serverLevel(player);
        var partner = level.getServer().getPlayerList().getPlayer(link.partnerId);
        // HurtGuard prevents mutual links from ping-ponging damage back and
        // forth in a nested recursion
        if (partner instanceof ServerPlayer p && p.isAlive() && HurtGuard.tryEnter()) {
            try {
                float redirected = amount * redirectFraction;
                p.hurt(level.damageSources().playerAttack(player), redirected);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
                return redirected;
            } finally {
                HurtGuard.exit();
            }
        }
        return 0;
    }

    public boolean isLinked(ServerPlayer player) {
        LinkData link = links.get(player.getUUID());
        return link != null && link.ticksLeft > 0;
    }

    @Override
    public void onRemove(ServerPlayer player) {
        UUID uuid = player.getUUID();
        LinkData link = links.remove(uuid);
        if (link != null) {
            links.remove(link.partnerId);
        }
    }

    private static class LinkData {
        final UUID partnerId;
        int ticksLeft;

        LinkData(UUID partnerId, int ticksLeft) {
            this.partnerId = partnerId;
            this.ticksLeft = ticksLeft;
        }
    }
}
