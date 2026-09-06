package dev.raceapi.race;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The set of races a player has selected, one per origin layer. Powers across
 * every selected layer stack; difficulty and scale are aggregated. A selection
 * with zero layers is empty (no race).
 * <p>
 * This is the shared client/server model behind the multi-layer system: the
 * server attaches each layer's powers to the player, and {@link #asRace()}
 * gives a single composite {@link Race} view so existing single-race consumers
 * (HUD, power pipelines, sync) keep working unchanged.
 */
public final class Selection {

    private final Map<String, Race> byLayer = new LinkedHashMap<>();

    public Selection() {
    }

    public Selection(Map<String, Race> layers) {
        if (layers != null) {
            byLayer.putAll(layers);
        }
    }

    public boolean isEmpty() {
        return byLayer.isEmpty();
    }

    public boolean hasLayer(String layer) {
        return byLayer.containsKey(layer);
    }

    public void put(String layer, Race race) {
        if (race == null) {
            byLayer.remove(layer);
        } else {
            byLayer.put(layer, race);
        }
    }

    public void clear() {
        byLayer.clear();
    }

    /** A copy of the layer → race map. */
    public Map<String, Race> allLayers() {
        return new LinkedHashMap<>(byLayer);
    }

    public Collection<Race> races() {
        return byLayer.values();
    }

    /** All effective powers across every selected layer, in layer/order. */
    public List<Power> effectivePowers() {
        List<Power> powers = new ArrayList<>();
        for (Race race : byLayer.values()) {
            powers.addAll(race.getPowers());
        }
        return powers;
    }

    /**
     * A single composite {@link Race} view aggregating every selected layer.
     * The id/display reflect the first (primary) layer's race; the power list,
     * difficulty and scale are merged. Empty selections return {@code null}.
     */
    public Race asRace() {
        if (byLayer.isEmpty()) {
            return null;
        }
        final Race primary = byLayer.values().iterator().next();
        final int difficulty = byLayer.values().stream().mapToInt(Race::getDifficulty).sum();
        return new CompositeRace(primary, this);
    }

    /**
     * Selects the largest non-default scale among the selected races (so one
     * layer's "huge" or "tiny" morph still visibly changes the hitbox while
     * additive layers that keep {@code 1.0} do not cancel it).
     */
    private float aggregatedScale() {
        float scale = 1.0f;
        for (Race race : byLayer.values()) {
            float s = race.getScale();
            if (s != 1.0f && Math.abs(s - 1.0f) > Math.abs(scale - 1.0f)) {
                scale = s;
            }
        }
        return scale;
    }

    private final class CompositeRace implements Race {
        private final Race primary;
        private final List<Power> merged;

        private CompositeRace(Race primary, Selection selection) {
            this.primary = primary;
            this.merged = selection.effectivePowers();
        }

        @Override
        public Identifier getId() {
            return primary.getId();
        }

        @Override
        public Component getDisplayName() {
            return primary.getDisplayName();
        }

        @Override
        public Component getDescription() {
            return primary.getDescription();
        }

        @Override
        public List<Power> getPowers() {
            return merged;
        }

        @Override
        public ItemStack getIcon() {
            return primary.getIcon();
        }

        @Override
        public int getDifficulty() {
            int sum = 0;
            for (Race race : races()) {
                sum += race.getDifficulty();
            }
            return sum;
        }

        @Override
        public float getScale() {
            return aggregatedScale();
        }

        @Override
        public String getLayer() {
            return primary.getLayer();
        }
    }
}