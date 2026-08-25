package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Continuously removes a specific potion effect from the player every tick.
 * This grants immunity to that effect — useful for "poison immune", "no slowness", etc.
 * <p>
 * JSON parameters:
 * <ul>
 *   <li>{@code effect} (required) — effect id, e.g. {@code minecraft:poison}</li>
 * </ul>
 */
public class EffectRemovalPower implements Power {

    private static final Logger LOGGER = LoggerFactory.getLogger(EffectRemovalPower.class);

    private final Identifier id;
    private final int difficulty;
    private final Holder<MobEffect> effect;

    public EffectRemovalPower(Identifier id, int difficulty, Holder<MobEffect> effect) {
        this.id = id;
        this.difficulty = difficulty;
        this.effect = effect;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.effect_removal.name", effect.value().getDisplayName());
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.effect_removal.desc", effect.value().getDisplayName());
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (player.hasEffect(effect)) {
            player.removeEffect(effect);
        }
    }
}
