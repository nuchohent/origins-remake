package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class ActionRestrictionPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final boolean restrictSprint;
    private final boolean restrictJump;
    private final boolean restrictSwim;
    private final boolean restrictFlight;
    private final boolean restrictAttack;

    public ActionRestrictionPower(Identifier id, int difficulty,
                                  boolean restrictSprint, boolean restrictJump,
                                  boolean restrictSwim, boolean restrictFlight,
                                  boolean restrictAttack) {
        this.id = id;
        this.difficulty = difficulty;
        this.restrictSprint = restrictSprint;
        this.restrictJump = restrictJump;
        this.restrictSwim = restrictSwim;
        this.restrictFlight = restrictFlight;
        this.restrictAttack = restrictAttack;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.action_restriction.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.action_restriction.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    @Override
    public void onTick(ServerPlayer player) {
        if (restrictSprint && player.isSprinting()) {
            player.setSprinting(false);
        }
        if (restrictFlight && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (restrictSwim) {
            Vec3 motion = player.getDeltaMovement();
            if (player.isInWater() && motion.y > 0) {
                player.setDeltaMovement(motion.x, Math.min(motion.y, 0.05), motion.z);
            }
        }
    }

    @Override
    public void onJump(ServerPlayer player) {
        if (restrictJump) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
        }
    }

    @Override
    public void onAttack(ServerPlayer player, LivingEntity target, float damage) {
        if (restrictAttack) {
            // Cancel the attack by zeroing damage
        }
    }

    public boolean isSprintRestricted() { return restrictSprint; }
    public boolean isJumpRestricted() { return restrictJump; }
    public boolean isSwimRestricted() { return restrictSwim; }
    public boolean isFlightRestricted() { return restrictFlight; }
    public boolean isAttackRestricted() { return restrictAttack; }
}
