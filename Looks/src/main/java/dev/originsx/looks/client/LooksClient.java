package dev.originsx.looks.client;

import com.google.gson.JsonArray;
import dev.originsx.looks.LooksMod;
import dev.raceapi.race.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client entry: registers the cosmetics render layer + render-state modifier,
 * keeps the live-preview override from the editor, and opens the editor.
 */
public final class LooksClient {

    /** Per-frame extracted cosmetics carried on the avatar render state. */
    public static final ContextKey<List<CosmeticsStateModifier.Extracted>> RENDER_DATA =
            new ContextKey<>(LooksMod.id("cosmetics"));

    /**
     * Live preview overrides (raceId -> entries) written by the editor on every
     * field change. Rendered INSTEAD of the race's saved cosmetics so the
     * player sees edits instantly, without saving.
     */
    private static final Map<String, List<Cosmetics.Entry>> OVERRIDES = new HashMap<>();

    /**
     * Race currently being edited, or null. While set, the local player renders
     * that race's override even if a different race is selected.
     */
    @Nullable
    private static String previewRaceId;

    /** Parse cache for race JSON; keys die with their Race instance on /reload. */
    private static final WeakHashMap<Race, List<Cosmetics.Entry>> PARSED = new WeakHashMap<>();

    private LooksClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(LooksClient::onAddLayers);
        modBus.addListener(dev.originsx.looks.client.CosmeticsStateModifier::onRegisterModifiers);
        dev.originsx.looks.client.EntityForm.init(modBus);
        // client state must not leak into the next world
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                LooksClient.GameEvents::onLoggingOut);
    }

    @EventBusSubscriber(modid = LooksMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            PlayerRaceClient.clear();
            clearPreview();
        }
    }

    // ------------------------------------------------------------------
    //  Editor entry point (called reflectively from OriginsX)
    // ------------------------------------------------------------------

    /** Opens the appearance editor for one custom race. */
    public static void openEditor(String raceId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        mc.setScreenAndShow(new LooksScreen(raceId == null ? "" : raceId));
    }

    // ------------------------------------------------------------------
    //  Live preview overrides
    // ------------------------------------------------------------------

    /** Sets/updates the live preview entries of {@code raceId} and forces them onto the local player. */
    public static void setPreview(String raceId, List<Cosmetics.Entry> entries) {
        previewRaceId = raceId;
        OVERRIDES.put(raceId, List.copyOf(entries));
    }

    /** Drops the preview override: saved cosmetics take over again. */
    public static void clearPreviewFor(String raceId) {
        OVERRIDES.remove(raceId);
        if (raceId.equals(previewRaceId)) {
            previewRaceId = null;
        }
    }

    public static void clearPreview() {
        OVERRIDES.clear();
        previewRaceId = null;
    }

    // ------------------------------------------------------------------
    //  Cosmetics resolution (used by the render-state modifier)
    // ------------------------------------------------------------------

    /**
     * Cosmetics to draw on an avatar entity:
     * <ul>
     *   <li>the edited race's live override wins while the editor is open</li>
     *   <li>otherwise the SELECTED race's cosmetics (local player or any other
     *       player via the race broadcast)</li>
     * </ul>
     */
    public static List<Cosmetics.Entry> resolveFor(Avatar entity) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer local = mc.player;

        if (entity == local && previewRaceId != null) {
            List<Cosmetics.Entry> override = OVERRIDES.get(previewRaceId);
            if (override != null) {
                return override;
            }
        }

        String raceIdStr = raceIdOf(entity);
        if (raceIdStr == null || raceIdStr.isEmpty()) {
            return List.of();
        }
        List<Cosmetics.Entry> override = OVERRIDES.get(raceIdStr);
        if (override != null) {
            return override;
        }
        Identifier raceId = Identifier.tryParse(raceIdStr);
        Race race = raceId == null ? null : dev.raceapi.race.RaceRegistry.getOrNull(raceId);
        return race == null ? List.of() : ofRace(race);
    }

    private static String raceIdOf(Avatar entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player) {
            Identifier id = dev.raceapi.client.SelectedRaceClient.getOrNull();
            return id == null ? null : id.toString();
        }
        return PlayerRaceClient.get(entity.getUUID());
    }

    /** Parsed cosmetics of a registered race (cached per Race object). */
    public static List<Cosmetics.Entry> ofRace(Race race) {
        List<Cosmetics.Entry> cached = PARSED.get(race);
        if (cached != null) {
            return cached;
        }
        JsonArray array = race.getSourceJson().has("cosmetics")
                && race.getSourceJson().get("cosmetics").isJsonArray()
                ? race.getSourceJson().getAsJsonArray("cosmetics") : null;
        List<Cosmetics.Entry> entries = Cosmetics.parse(array);
        PARSED.put(race, entries);
        return entries;
    }

    /** Cosmetic count for a race id — used by the editor header ("N items"). */
    public static int countFor(Race race) {
        return ofRace(race).size();
    }

    /**
     * The race's bound entity model (full-transformation form), or null.
     * Read from the race JSON {@code "model_entity"} field (e.g.
     * {@code "minecraft:axolotl"}); the local player with such a race should
     * render as that entity. The editor picks it via the viewport entity mode.
     */
    @Nullable
    public static Identifier modelEntityFor(Race race) {
        if (race == null || !race.getSourceJson().has("model_entity")) {
            return null;
        }
        try {
            return Identifier.tryParse(race.getSourceJson().get("model_entity").getAsString());
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    //  Layer registration
    // ------------------------------------------------------------------

    private static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.world.entity.player.PlayerModelType skin : event.getSkins()) {
            var playerRenderer = event.getPlayerRenderer(skin);
            playerRenderer.addLayer(new CosmeticsLayer(playerRenderer));
            var mannequinRenderer = event.getMannequinRenderer(skin);
            mannequinRenderer.addLayer(new CosmeticsLayer(mannequinRenderer));
        }
    }

    /** Header helper for the editor title row. */
    public static Component raceName(String raceId) {
        Identifier id = Identifier.tryParse(raceId);
        Race race = id == null ? null : dev.raceapi.race.RaceRegistry.getOrNull(id);
        return race != null ? race.getDisplayName() : Component.literal(raceId);
    }
}
