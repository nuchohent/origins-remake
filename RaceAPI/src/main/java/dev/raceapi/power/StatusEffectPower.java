package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Grants a potion effect while the race is active. When {@link #infinite} is
 * true the effect uses {@link MobEffectInstance#INFINITE_DURATION} so it never
 * expires on its own; otherwise it is re-applied every {@link #intervalTicks}
 * for {@link #durationTicks}. The effect is only removed when the race is
 * switched or the player logs out. Weakness variants use a negative
 * {@link #getDifficulty()} so the GUI renders them in red.
 */
public class StatusEffectPower implements Power {

    private final Identifier id;
    private final Holder<MobEffect> effect;
    private final int amplifier;
    private final int durationTicks;
    private final int intervalTicks;
    private final boolean infinite;
    private final boolean hidden;
    private final int difficulty;

    public StatusEffectPower(Identifier id, Holder<MobEffect> effect, int amplifier,
                             int durationTicks, int intervalTicks, boolean hidden, int difficulty,
                             boolean infinite) {
        this.id = id;
        this.effect = effect;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
        this.intervalTicks = intervalTicks;
        this.infinite = infinite;
        this.hidden = hidden;
        this.difficulty = difficulty;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return effect.value().getDisplayName();
    }

    @Override
    public Component getDescription() {
        return Component.translatable(effect.value().getDescriptionId());
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public void onAttach(ServerPlayer player) {
        apply(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (infinite) {
            if (!player.hasEffect(effect)) {
                apply(player);
            }
            return;
        }
        if (intervalTicks > 0 && player.tickCount % intervalTicks == 0) {
            apply(player);
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.removeEffect(effect);
    }

    private void apply(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() > amplifier) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect,
                infinite ? MobEffectInstance.INFINITE_DURATION : durationTicks,
                amplifier, false, false, true));
    }
}
