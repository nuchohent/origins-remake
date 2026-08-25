package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Weakness: an insatiable hunger. Instead of applying a hunger effect (which
 * fights other powers), the player constantly gains exhaustion — the same
 * mechanic vanilla hunger uses — so their food bar drains steadily. The
 * {@code exhaustionPerSecond} value controls how fast it drains.
 */
public class RavenousPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double exhaustionPerSecond;

    public RavenousPower(Identifier id, int difficulty) {
        this(id, difficulty, 0.5);
    }

    public RavenousPower(Identifier id, int difficulty, double exhaustionPerSecond) {
        this.id = id;
        this.difficulty = difficulty;
        this.exhaustionPerSecond = exhaustionPerSecond;
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
    public void onTick(ServerPlayer player) {
        player.getFoodData().addExhaustion((float) (exhaustionPerSecond / 20.0));
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getFoodData().exhaustionLevel = 0.0f;
    }
}
