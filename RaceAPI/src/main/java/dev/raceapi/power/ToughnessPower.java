package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Passive power that reduces incoming damage using the {@link #onHurt} hook.
 */
public class ToughnessPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double reduction;

    public ToughnessPower(Identifier id, int difficulty) {
        this(id, difficulty, 0.25);
    }

    public ToughnessPower(Identifier id, int difficulty, double reduction) {
        this.id = id;
        this.difficulty = difficulty;
        this.reduction = reduction;
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
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        return (float) (amount * (1.0 - reduction));
    }
}
