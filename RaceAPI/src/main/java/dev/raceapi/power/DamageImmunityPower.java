package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Grants immunity to a specific type of damage. The {@code damage_type} field
 * in the JSON selects which tag or type to check against.
 */
public class DamageImmunityPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final String damageType;

    public DamageImmunityPower(Identifier id, int difficulty, String damageType) {
        this.id = id;
        this.difficulty = difficulty;
        this.damageType = damageType;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.damage_immunity.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.damage_immunity.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public float onHurt(ServerPlayer player, DamageSource source, float amount) {
        if (isImmune(source)) {
            return 0;
        }
        return amount;
    }

    private boolean isImmune(DamageSource source) {
        return switch (damageType) {
            case "fire" -> source.is(DamageTypeTags.IS_FIRE);
            case "fall" -> source.is(DamageTypeTags.IS_FALL);
            case "drowning" -> source.is(DamageTypeTags.IS_DROWNING);
            case "explosion" -> source.is(DamageTypeTags.IS_EXPLOSION);
            case "projectile" -> source.is(DamageTypeTags.IS_PROJECTILE);
            case "lightning" -> source.is(DamageTypes.LIGHTNING_BOLT);
            case "freeze" -> source.is(DamageTypeTags.IS_FREEZING);
            case "starvation" -> source.is(DamageTypes.STARVE);
            case "suffocation" -> source.is(DamageTypes.IN_WALL);
            case "void" -> source.is(DamageTypes.FELL_OUT_OF_WORLD);
            case "magic" -> source.is(DamageTypes.MAGIC);
            case "wither" -> source.is(DamageTypes.WITHER);
            case "cactus" -> source.is(DamageTypes.CACTUS);
            case "generic" -> source.is(DamageTypes.GENERIC);
            case "player_attack" -> source.is(DamageTypeTags.IS_PLAYER_ATTACK);
            case "sonic_boom" -> source.is(DamageTypes.SONIC_BOOM);
            case "dragon_breath" -> source.is(DamageTypes.DRAGON_BREATH);
            default -> false;
        };
    }
}
