package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Grants an infinite potion effect only while the right time of day is active
 * (day or night). The effect is removed the moment the condition flips, which is
 * something the vanilla effect command cannot do.
 */
public class TimeEffectPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final Holder<MobEffect> effect;
    private final int amplifier;
    private final boolean night;

    public TimeEffectPower(Identifier id, int difficulty, Holder<MobEffect> effect, int amplifier, boolean night) {
        this.id = id;
        this.difficulty = difficulty;
        this.effect = effect;
        this.amplifier = amplifier;
        this.night = night;
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
    public void onRemove(ServerPlayer player) {
        removeGranted(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        long clock = Math.floorMod(player.level().getOverworldClockTime(), 24000L);
        boolean active = night ? clock >= 12000 : clock < 12000;
        if (active) {
            if (!player.hasEffect(effect)) {
                player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, false, true));
                player.getPersistentData().putInt(stateKey(), amplifier);
            }
        } else if (player.hasEffect(effect)) {
            removeGranted(player);
        }
    }

    private void removeGranted(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        String key = stateKey();
        if (!data.contains(key)) {
            return;
        }
        int grantedAmplifier = data.getIntOr(key, amplifier);
        data.remove(key);
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() == grantedAmplifier && current.isInfiniteDuration()) {
            player.removeEffect(effect);
        }
    }

    private String stateKey() {
        return "raceapi_eff_" + id + "_" + BuiltInRegistries.MOB_EFFECT.getKey(effect.value());
    }
}
