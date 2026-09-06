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
import dev.raceapi.race.Selection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side manager that applies/removes races on players and keeps
 * {@link RaceSavedData} in sync.
 * <p>
 * Since 2.0 players may select one race <em>per origin layer</em>; powers
 * across all selected layers stack. {@link #getRace(ServerPlayer)} returns a
 * composite {@link Race} view of the whole selection so single-race consumers
 * keep working unchanged; {@link #getSelection(ServerPlayer)} exposes the
 * per-layer breakdown.
 */
public final class RaceManager {

    private static final Identifier RACE_SCALE_ID = Identifier.fromNamespaceAndPath("raceapi", "race_scale");

    /**
     * The per-layer race objects actually attached to each player. Kept so a
     * datapack reload (which replaces every {@code Race} instance with a fresh
     * object) can remove the stale powers' modifiers/effects and re-apply the
     * new ones. Layer keys mirror the persisted selection.
     */
    private static final Map<UUID, Map<String, Race>> ATTACHED_RACES = new HashMap<>();

    private RaceManager() {
    }

    /** Sets a player's race in the race's own layer. Pass null to clear all layers. */
    public static void setRace(ServerPlayer player, @Nullable Identifier raceId) {
        if (raceId == null) {
            clearPlayer(player);
            return;
        }
        Race race = RaceRegistry.getOrNull(raceId);
        if (race == null) {
            throw new IllegalArgumentException("Unknown race: " + raceId);
        }
        setRace(player, race.getLayer(), raceId);
    }

    /** Sets a player's race for a specific layer. Pass null to clear that layer. */
    public static void setRace(ServerPlayer player, String layer, @Nullable Identifier raceId) {
        Map<String, Race> attached = ATTACHED_RACES.get(player.getUUID());
        Race oldRace = attached == null ? null : attached.get(layer);
        Race newRace = raceId == null ? null : RaceRegistry.getOrNull(raceId);
        if (newRace == null && raceId != null) {
            throw new IllegalArgumentException("Unknown race: " + raceId);
        }
        if (oldRace == newRace) {
            SyncRacePayload.send(player, selection(player));
            return;
        }
        if (oldRace != null) {
            // grant flags reset ONLY on an explicit switch to another race
            // ("once" = once per race SELECTION); clearing keeps the flags so
            // start items cannot be farmed by select/clear/select
            if (newRace != null) {
                for (Power power : PowerPipeline.effective(player, oldRace)) {
                    if (power.getWrapped() instanceof dev.raceapi.power.GrantItemPower grant) {
                        dev.raceapi.data.GrantFlagsData.get(dev.raceapi.util.RaceUtils.serverLevel(player))
                                .unmark(player.getUUID(), grant.getId().toString());
                    }
                }
            }
            removeLayerPowers(player, oldRace);
        }

        if (newRace == null) {
            if (attached != null) {
                attached.remove(layer);
                if (attached.isEmpty()) {
                    ATTACHED_RACES.remove(player.getUUID());
                }
            }
        } else {
            ATTACHED_RACES.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>()).put(layer, newRace);
            applyLayerPowers(player, newRace);
        }

        persist(player);
        refreshScale(player);
        dev.raceapi.player.Resources.clear(player);
        syncAfterChange(player);

        Race oldComposite = oldRace == null ? null : selectionOf(attached, oldRace).asRace();
        Race newComposite = selection(player).asRace();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new RaceChangedEvent(player, oldComposite, newComposite));
    }

    /** Removes every selected layer from a player (full clear). */
    private static void clearPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Map<String, Race> attached = ATTACHED_RACES.remove(uuid);
        if (attached != null) {
            for (Race race : attached.values()) {
                removeLayerPowers(player, race);
            }
        }
        RaceSavedData.get(RaceUtils.serverLevel(player)).clearAll(uuid);
        dev.raceapi.player.Resources.clear(player);
        refreshScale(player);
        syncAfterChange(player);
        Race oldComposite = attached == null ? null
                : new Selection(new LinkedHashMap<>(attached)).asRace();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new RaceChangedEvent(player, oldComposite, null));
    }

    /** Clears a single layer's selection. */
    public static void clearLayer(ServerPlayer player, String layer) {
        Map<String, Race> attached = ATTACHED_RACES.get(player.getUUID());
        Race oldRace = attached == null ? null : attached.get(layer);
        if (oldRace != null) {
            removeLayerPowers(player, oldRace);
            attached.remove(layer);
            if (attached.isEmpty()) {
                ATTACHED_RACES.remove(player.getUUID());
            }
        }
        RaceSavedData.get(RaceUtils.serverLevel(player)).set(player.getUUID(), layer, "");
        refreshScale(player);
        syncAfterChange(player);
        Race oldComposite = oldRace == null ? null : selectionOf(attached, oldRace).asRace();
        Race newComposite = selection(player).asRace();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new RaceChangedEvent(player, oldComposite, newComposite));
    }

    /**
     * Builds the selection that {@code attached} holds <em>after</em> removing
     * {@code removedRace} from it (used to report the pre-change composite when
     * the map has already been mutated by the caller).
     */
    private static Selection selectionOf(Map<String, Race> attached, Race removedRace) {
        Selection before = new Selection(
                attached == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attached));
        before.put(removedRace.getLayer(), removedRace);
        return before;
    }

    /** Applies the persisted race selection (e.g. after login). */
    public static void applyPersistedRace(ServerPlayer player) {
        applyPersistedRace(player, true);
    }

    /**
     * Applies the persisted race selection. Pass {@code syncRegistry=false}
     * when the race registry cannot have changed (e.g. a skill-tree node unlock
     * re-attach): skips the full registry sync and only re-attaches powers.
     */
    public static void applyPersistedRace(ServerPlayer player, boolean syncRegistry) {
        // dedicated servers: the client needs every race definition for its
        // selection screen — sync before the selected-race payload
        if (syncRegistry) {
            dev.raceapi.network.SyncRacesPayload.sendTo(player);
        }
        // Clear any leftover effects from a previous session before re-applying
        clearAllRaceEffects(player);
        Selection saved = selection(player);
        if (saved.isEmpty()) {
            ATTACHED_RACES.remove(player.getUUID());
            SyncRacePayload.send(player, new Selection());
            return;
        }
        ATTACHED_RACES.put(player.getUUID(), persistedMap(saved));
        refreshScale(player);
        for (Race race : saved.races()) {
            applyLayerPowers(player, race);
        }
        syncCooldowns(player, saved);
        dev.raceapi.player.Resources.sync(player);
        SyncRacePayload.send(player, saved);
    }

    /**
     * Re-applies a player's race selection after a datapack reload. The registry
     * now holds fresh {@code Race} objects, so stale modifiers/effects from the
     * previously attached layers are removed and the updated definitions attach.
     */
    public static void reapplyAfterReload(ServerPlayer player) {
        Map<String, Race> attached = ATTACHED_RACES.get(player.getUUID());
        Selection current = selection(player);
        if (current.isEmpty()) {
            if (attached != null) {
                for (Race race : attached.values()) {
                    removeLayerPowers(player, race);
                }
            }
            RaceSavedData.get(RaceUtils.serverLevel(player)).clearAll(player.getUUID());
            ATTACHED_RACES.remove(player.getUUID());
            refreshScale(player);
            SyncRacePayload.send(player, new Selection());
            return;
        }
        if (attached != null) {
            for (Race race : attached.values()) {
                removeLayerPowers(player, race);
            }
        }
        for (Race race : current.races()) {
            applyLayerPowers(player, race);
        }
        ATTACHED_RACES.put(player.getUUID(), persistedMap(current));
        refreshScale(player);
        SyncRacePayload.send(player, current);
    }

    /** Cleans up transient race effects when the player disconnects. */
    public static void clearTransientPowers(ServerPlayer player) {
        Selection selection = selection(player);
        if (!selection.isEmpty()) {
            for (Race race : selection.races()) {
                removeLayerPowers(player, race);
            }
        }
        clearAllRaceEffects(player);
        ATTACHED_RACES.remove(player.getUUID());
    }

    /** The composite race of every selected layer, or null when no race. */
    @Nullable
    public static Race getRace(ServerPlayer player) {
        return selection(player).asRace();
    }

    /** The per-layer selection currently persisted for a player (fully resolved). */
    public static Selection getSelection(ServerPlayer player) {
        return selection(player);
    }

    /** The race id selected in a specific layer, or null. */
    @Nullable
    public static Identifier getRaceId(ServerPlayer player, String layer) {
        Race race = selection(player).allLayers().get(layer);
        return race == null ? null : race.getId();
    }

    private static Selection selection(ServerPlayer player) {
        RaceSavedData data = RaceSavedData.get(RaceUtils.serverLevel(player));
        Selection selection = new Selection();
        for (String layer : RaceRegistry.layers()) {
            String id = data.get(player.getUUID(), layer);
            if (id.isEmpty()) {
                continue;
            }
            Race race = RaceRegistry.getOrNull(Identifier.tryParse(id));
            if (race != null) {
                selection.put(race.getLayer(), race);
            }
        }
        return selection;
    }

    private static Map<String, Race> persistedMap(Selection selection) {
        Map<String, Race> map = new LinkedHashMap<>();
        for (Race race : selection.races()) {
            map.put(race.getLayer(), race);
        }
        return map;
    }

    private static void persist(ServerPlayer player) {
        Selection selection = selection(player);
        RaceSavedData data = RaceSavedData.get(RaceUtils.serverLevel(player));
        for (String layer : RaceRegistry.layers()) {
            Race race = selection.allLayers().get(layer);
            data.set(player.getUUID(), layer, race == null ? "" : race.getId().toString());
        }
    }

    private static void syncAfterChange(ServerPlayer player) {
        Selection selection = selection(player);
        SyncRacePayload.send(player, selection);
        syncCooldowns(player, selection);
        dev.raceapi.player.Resources.sync(player);
    }

    private static void applyLayerPowers(ServerPlayer player, Race race) {
        race.onSelect(player);
        for (Power power : PowerPipeline.effective(player, race)) {
            power.onAttach(player);
        }
    }

    private static void syncCooldowns(ServerPlayer player, Selection selection) {
        for (Race race : selection.races()) {
            for (Power power : PowerPipeline.effective(player, race)) {
                if (power.hasBinding() && power.getCooldownTicks() > 0) {
                    int remaining = power.getRemainingCooldownTicks(player);
                    CooldownPayload.send(player, power.getId().toString(), power.getCooldownTicks(), remaining);
                }
            }
        }
    }

    private static void removeLayerPowers(ServerPlayer player, Race race) {
        race.onRemove(player);
        for (Power power : PowerPipeline.effective(player, race)) {
            power.onRemove(player);
        }
        sweepRaceModifiers(player);
        dev.raceapi.player.Resources.sync(player);
    }

    /**
     * Recomputes the aggregate scale modifier from the currently attached layers
     * (kept current after every selection change and re-apply).
     */
    public static void refreshScale(ServerPlayer player) {
        Map<String, Race> attached = ATTACHED_RACES.get(player.getUUID());
        applyRaceScale(player, aggregateScale(attached == null ? Map.of() : attached));
    }

    /** Largest |scale-1| across the layers; 1.0 when a morph race is present. */
    private static float aggregateScale(Map<String, Race> layers) {
        float scale = 1.0f;
        for (Race race : layers.values()) {
            if (isMorphRace(race)) {
                return 1.0f;
            }
            float s = race.getScale();
            if (s != 1.0f && Math.abs(s - 1.0f) > Math.abs(scale - 1.0f)) {
                scale = s;
            }
        }
        return scale;
    }

    /**
     * A "morph" race has a {@code model_entity} (a mob it transforms into).
     * Such races keep a full normal player hitbox/playability (the mob's small
     * size is purely visual, client-side), so the race's own {@code scale} is
     * not applied server-side — otherwise the tiny hitbox causes the low
     * friction / "slides on ice" physics glitch.
     */
    private static boolean isMorphRace(Race race) {
        return race.getSourceJson().has("model_entity");
    }

    private static void applyRaceScale(ServerPlayer player, float scale) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance == null) {
            return;
        }
        instance.removeModifier(RACE_SCALE_ID);
        if (scale != 1.0f) {
            double diff = scale - 1.0;
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(RACE_SCALE_ID, diff, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeRaceScale(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance != null) {
            instance.removeModifier(RACE_SCALE_ID);
        }
    }

    /**
     * Removes every {@code raceapi} attribute modifier, including orphans left
     * by power clones no longer present in the effective list on detach.
     */
    private static void sweepRaceModifiers(ServerPlayer player) {
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
    }

    /**
     * Hard-clears all potion effects and attribute modifiers that any race power
     * could have added, resets attributes and abilities. Called as a safety net
     * before applying a new race to prevent effect stacking.
     */
    private static void clearAllRaceEffects(ServerPlayer player) {
        removeRaceScale(player);

        // First properly remove the old race's powers if still attached
        Map<String, Race> oldAttached = ATTACHED_RACES.remove(player.getUUID());
        if (oldAttached != null) {
            for (Race race : oldAttached.values()) {
                race.onRemove(player);
                for (Power power : PowerPipeline.effective(player, race)) {
                    power.onRemove(player);
                }
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
        sweepRaceModifiers(player);

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
}