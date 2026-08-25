package dev.raceapi.power;

import dev.raceapi.player.Resources;
import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Passive power that grants the race a resource pool (mana/stamina).
 * The pool regenerates by {@code regen} units per second while the race is
 * attached; active powers with a {@code cost} spend it via
 * {@link Resources#tryConsume}. The first resource power of the effective
 * power list defines the pool; extra ones are ignored.
 * <p>
 * A pool of kind {@code stamina} can also drain passively: while the player
 * sprints ({@code consume_sprint}, {@code sprint_cost} per second) and on
 * every melee attack ({@code consume_attack}, charged by the damage pipeline
 * in RaceAPI). Sprinting stops when the pool runs dry.
 */
public final class ResourcePower implements Power {

    public static final String KIND_MANA = "mana";
    public static final String KIND_STAMINA = "stamina";

    private final Identifier id;
    private final int difficulty;
    private final double max;
    private final double regenPerSecond;
    private final String kind;
    private final boolean consumeSprint;
    private final double sprintCostPerSecond;
    private final boolean consumeAttack;
    private final double attackCost;

    public ResourcePower(Identifier id, int difficulty, double max, double regenPerSecond) {
        this(id, difficulty, max, regenPerSecond, KIND_MANA, true, 0.0, true, 0.0);
    }

    public ResourcePower(Identifier id, int difficulty, double max, double regenPerSecond,
                         String kind, boolean consumeSprint, double sprintCostPerSecond,
                         boolean consumeAttack, double attackCost) {
        this.id = id;
        this.difficulty = difficulty;
        this.max = Math.max(1.0, max);
        this.regenPerSecond = Math.max(0.0, regenPerSecond);
        this.kind = KIND_STAMINA.equalsIgnoreCase(kind) ? KIND_STAMINA : KIND_MANA;
        this.consumeSprint = consumeSprint;
        this.sprintCostPerSecond = Math.max(0.0, sprintCostPerSecond);
        this.consumeAttack = consumeAttack;
        this.attackCost = Math.max(0.0, attackCost);
    }

    public double getMax() {
        return max;
    }

    public double getRegenPerSecond() {
        return regenPerSecond;
    }

    /** {@code mana} (default, ability costs only) or {@code stamina} (also sprint/attacks). */
    public String getKind() {
        return kind;
    }

    public boolean isStamina() {
        return KIND_STAMINA.equals(kind);
    }

    public boolean consumesSprint() {
        return consumeSprint;
    }

    public double getSprintCostPerSecond() {
        return sprintCostPerSecond;
    }

    public boolean consumesAttack() {
        return consumeAttack;
    }

    public double getAttackCost() {
        return attackCost;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.resource.name");
    }

    @Override
    public Component getDescription() {
        if (isStamina()) {
            return Component.translatable("power.raceapi.resource.desc_stamina",
                    (int) max, String.format(java.util.Locale.ROOT, "%.1f", regenPerSecond));
        }
        return Component.translatable("power.raceapi.resource.desc",
                (int) max, String.format(java.util.Locale.ROOT, "%.1f", regenPerSecond));
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onTick(ServerPlayer player) {
        // sync/regen at most every 10 ticks; fractional regen accumulates in storage
        if (regenPerSecond > 0 && player.tickCount % 10 == 0) {
            Resources.add(player, regenPerSecond * 10.0 / 20.0);
        }
        // stamina pools drain while sprinting; no stamina = no sprint
        if (isStamina() && consumeSprint && sprintCostPerSecond > 0
                && player.tickCount % 10 == 0 && player.isSprinting()) {
            if (!Resources.tryConsume(player, sprintCostPerSecond / 2.0)) {
                player.setSprinting(false);
            }
        }
    }
}
