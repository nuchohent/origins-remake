package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Reflects a portion of incoming melee damage back at the attacker (thorns).
 * Damage reflection of a fixed fraction is not available via vanilla commands.
 */
public class ThornsPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double fraction;

    public ThornsPower(Identifier id, int difficulty) {
        this(id, difficulty, 0.25);
    }

    public ThornsPower(Identifier id, int difficulty, double fraction) {
        this.id = id;
        this.difficulty = difficulty;
        this.fraction = fraction;
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
        // HurtGuard prevents two thorns players from reflecting damage back
        // and forth in an endless nested recursion
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player && amount > 0
                && HurtGuard.tryEnter()) {
            try {
                attacker.hurt(player.damageSources().thorns(player), (float) (amount * fraction));
            } finally {
                HurtGuard.exit();
            }
        }
        return amount;
    }
}
