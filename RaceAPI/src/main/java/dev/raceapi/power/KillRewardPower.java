package dev.raceapi.power;

import dev.raceapi.player.Resources;
import dev.raceapi.race.Power;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Passive reward on kill: heals the player, optionally grants the race
 * resource and/or a Strength buff. JSON type {@code on_kill}.
 */
public final class KillRewardPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double heal;
    private final double resourceGain;
    private final int strengthSeconds;

    public KillRewardPower(Identifier id, int difficulty, double heal,
                           double resourceGain, int strengthSeconds) {
        this.id = id;
        this.difficulty = difficulty;
        this.heal = Math.max(0.0, heal);
        this.resourceGain = Math.max(0.0, resourceGain);
        this.strengthSeconds = Math.max(0, strengthSeconds);
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.on_kill.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.on_kill.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onKill(ServerPlayer player, LivingEntity victim) {
        if (heal > 0) {
            player.heal((float) heal);
        }
        if (resourceGain > 0 && Resources.hasResource(player)) {
            Resources.add(player, resourceGain);
        }
        if (strengthSeconds > 0) {
            var strength = BuiltInRegistries.MOB_EFFECT.get(
                    Identifier.fromNamespaceAndPath("minecraft", "strength"));
            if (strength.isPresent()) {
                player.addEffect(new MobEffectInstance(strength.get(), strengthSeconds * 20, 0));
            }
        }
    }
}
