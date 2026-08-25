package dev.raceapi.player;

import dev.raceapi.util.RaceUtils;
import dev.raceapi.api.PowerPipeline;
import dev.raceapi.data.RaceSavedData;
import dev.raceapi.event.RaceChangedEvent;
import dev.raceapi.network.CooldownPayload;
import dev.raceapi.network.SyncRacePayload;
import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
import dev.raceapi.race.RaceRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side manager that applies/removes races on players and keeps
 * {@link RaceSavedData} in sync.
 */
public final class RaceManager {

    private static final Identifier RACE_SCALE_ID = Identifier.fromNamespaceAndPath("raceapi", "race_scale");

    /**
     * The race objects actually attached to each player. Kept so a datapack
     * reload (which replaces every {@code Race} instance with a fresh object)
     * can remove the stale powers' modifiers/effects and re-apply the new ones.
     */
    private static final Map<UUID, Race> ATTACHED_RACES = new HashMap<>();

    private RaceManager() {
    }

    /** Sets a player's race. Pass null to clear it. */
    public static void setRace(ServerPlayer player, @Nullable Identifier raceId) {
        Race oldRace = ATTACHED_RACES.get(player.getUUID());
        Race newRace = raceId == null ? null : RaceRegistry.getOrNull(raceId);
        if (newRace == null && raceId != null) {
            throw new IllegalArgumentException("Unknown race: " + raceId);
        }
        if (oldRace == newRace) {
            SyncRacePayload.send(player, newRace);
            return;
        }
        if (oldRace != null) {
            // starting items may be granted again if the player picks the race
            // back up - "once" means once per race SELECTION; respawn/relog
            // re-attach must NOT reset the flags (they go through
            // removeRacePowers too, hence the reset here and not there)
            for (Power power : PowerPipeline.effective(player, oldRace)) {
                if (power.getWrapped() instanceof dev.raceapi.power.GrantItemPower grant) {
                    dev.raceapi.data.GrantFlagsData.get(dev.raceapi.util.RaceUtils.serverLevel(player))
                            .unmark(player.getUUID(), grant.getId().toString());
                }
            }
            removeRacePowers(player, oldRace);
            // an actual race switch resets the resource pool (logout/respawn
            // re-attach goes through removeRacePowers too, so the clear lives here)
            dev.raceapi.player.Resources.clear(player);
        }
        RaceSavedData.get(RaceUtils.serverLevel(player)).set(player.getUUID(), raceId == null ? "" : raceId.toString());
        ATTACHED_RACES.put(player.getUUID(), newRace);
        if (newRace != null) {
            applyRacePowers(player, newRace);
        }
        SyncRacePayload.send(player, newRace);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new RaceChangedEvent(player, oldRace, newRace));
    }

    /** Applies the persisted race (e.g. after login). */
    public static void applyPersistedRace(ServerPlayer player) {
        // dedicated servers: the client needs every race definition for its
        // selection screen — sync before the selected-race payload
        dev.raceapi.network.SyncRacesPayload.sendTo(player);
        // Clear any leftover effects from a previous session before re-applying
        clearAllRaceEffects(player);
        String saved = RaceSavedData.get(RaceUtils.serverLevel(player)).get(player.getUUID());
        if (saved.isEmpty()) {
            ATTACHED_RACES.remove(player.getUUID());
            SyncRacePayload.send(player, null);
            return;
        }
        Race race = RaceRegistry.getOrNull(Identifier.tryParse(saved));
        if (race == null) {
            ATTACHED_RACES.remove(player.getUUID());
            SyncRacePayload.send(player, null);
            return;
        }
        applyRacePowers(player, race);
        ATTACHED_RACES.put(player.getUUID(), race);
        SyncRacePayload.send(player, race);
    }

    /**
     * Re-applies a player's race after a datapack reload. The registry now holds
     * fresh {@code Race} objects, so stale modifiers/effects from the previously
     * attached race are removed and the updated definition is attached instead.
     */
    public static void reapplyAfterReload(ServerPlayer player) {
        Race attached = ATTACHED_RACES.get(player.getUUID());
        Race current = getRace(player);
        if (current == null) {
            if (attached != null) {
                removeRacePowers(player, attached);
            }
            ATTACHED_RACES.remove(player.getUUID());
            SyncRacePayload.send(player, null);
            return;
        }
        if (attached != null) {
            removeRacePowers(player, attached);
        }
        applyRacePowers(player, current);
        ATTACHED_RACES.put(player.getUUID(), current);
        SyncRacePayload.send(player, current);
    }

    /** Cleans up transient race effects when the player disconnects. */
    public static void clearTransientPowers(ServerPlayer player) {
        Race race = getRace(player);
        if (race != null) {
            removeRacePowers(player, race);
        }
        clearAllRaceEffects(player);
        ATTACHED_RACES.remove(player.getUUID());
    }

    @Nullable
    public static Race getRace(ServerPlayer player) {
        String saved = RaceSavedData.get(RaceUtils.serverLevel(player)).get(player.getUUID());
        if (saved.isEmpty()) {
            return null;
        }
        return RaceRegistry.getOrNull(Identifier.tryParse(saved));
    }

    private static void applyRacePowers(ServerPlayer player, Race race) {
        applyRaceScale(player, race.getScale());
        race.onSelect(player);
        for (Power power : PowerPipeline.effective(player, race)) {
            power.onAttach(player);
        }
        syncCooldowns(player, race);
        // push the resource pool size (if the race has one) to the HUD
        dev.raceapi.player.Resources.sync(player);
    }

    private static void syncCooldowns(ServerPlayer player, Race race) {
        for (Power power : PowerPipeline.effective(player, race)) {
            if (power.hasBinding() && power.getCooldownTicks() > 0) {
                int remaining = power.getRemainingCooldownTicks(player);
                if (remaining > 0) {
                    CooldownPayload.send(player, power.getId().toString(), power.getCooldownTicks(), remaining);
                }
            }
        }
    }

    private static void removeRacePowers(ServerPlayer player, Race race) {
        removeRaceScale(player);
        race.onRemove(player);
        for (Power power : PowerPipeline.effective(player, race)) {
            power.onRemove(player);
        }
        // NOTE: the resource pool is intentionally NOT reset here — this path
        // also runs on logout/respawn/reload re-attach, and the pool should
        // survive those. RaceManager.setRace clears it on an actual race change.
        dev.raceapi.player.Resources.sync(player);
    }

    /**
     * Hard-clears all potion effects and attribute modifiers that any race power
     * could have added, resets attributes and abilities. Called as a safety net
     * before applying a new race to prevent effect stacking.
     */
    private static void clearAllRaceEffects(ServerPlayer player) {
        removeRaceScale(player);

        // First properly remove the old race's powers if still attached
        Race oldAttached = ATTACHED_RACES.remove(player.getUUID());
        if (oldAttached != null) {
            oldAttached.onRemove(player);
            for (Power power : PowerPipeline.effective(player, oldAttached)) {
                power.onRemove(player);
            }
        }

        // Players without an attached race keep their own vanilla effects:
        // the wipe below is only a safety net against leftovers of a race.
        if (oldAttached == null) {
            return;
        }

        // Remove all potion effects that powers commonly apply
        player.removeEffect(MobEffects.SPEED);
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.HASTE);
        player.removeEffect(MobEffects.MINING_FATIGUE);
        player.removeEffect(MobEffects.STRENGTH);
        player.removeEffect(MobEffects.RESISTANCE);
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.removeEffect(MobEffects.WATER_BREATHING);
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.removeEffect(MobEffects.JUMP_BOOST);
        player.removeEffect(MobEffects.HUNGER);
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.LEVITATION);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.NAUSEA);
        player.removeEffect(MobEffects.WEAKNESS);

        // Sweep every attribute modifier this mod could ever have applied,
        // regardless of which power instance (original or tree clone) made it
        net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.listElements()
                .forEach(holder -> {
                    var inst = player.getAttribute(holder);
                    if (inst != null) {
                        for (net.minecraft.world.entity.ai.attributes.AttributeModifier mod
                                : java.util.List.copyOf(inst.getModifiers())) {
                            if (mod.id().getNamespace().equals("raceapi")) {
                                inst.removeModifier(mod.id());
                            }
                        }
                    }
                });

        // Reset abilities (flight)
        if (player.getAbilities().mayfly && !player.isCreative()
                && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        // Reset fire
        player.clearFire();

        // Reset fall distance
        player.fallDistance = 0.0f;
    }

    private static void applyRaceScale(ServerPlayer player, float scale) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance != null && scale != 1.0f) {
            double diff = scale - 1.0;
            instance.addOrUpdateTransientModifier(new AttributeModifier(RACE_SCALE_ID, diff, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeRaceScale(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance != null) {
            instance.removeModifier(RACE_SCALE_ID);
        }
    }
}
