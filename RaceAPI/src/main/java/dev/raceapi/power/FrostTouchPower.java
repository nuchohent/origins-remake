package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Melee attacks slow the target. Applying a slow on hit is impossible with
 * vanilla commands or effects alone.
 */
public class FrostTouchPower implements Power {

    private final Identifier id;
    private final int difficulty;

    public FrostTouchPower(Identifier id, int difficulty) {
        this.id = id;
        this.difficulty = difficulty;
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
    public void onAttack(ServerPlayer player, LivingEntity target, float damage) {
        if (target != player && target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        }
    }
}
