package dev.originsx.skilltree.progress;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists skill tree progress: player UUID + node key ({@code <treeId>/<nodeId>})
 * → unlocked tier (0 = locked, 3 = fully upgraded). Stored in the overworld's
 * data storage, mirroring {@code raceapi}'s own saved data.
 */
public class ProgressData extends SavedData {

    public static final SavedDataType<ProgressData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("originsx_skilltree", "skill_progress"),
            ProgressData::new,
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .xmap(ProgressData::fromMap, ProgressData::toMap));

    private final Map<String, Integer> tiers = new HashMap<>();

    private static volatile boolean migrated;

    public static ProgressData get(ServerLevel level) {
        // always the overworld's storage: progress is global, not per-dimension
        ServerLevel overworld = level.getServer().overworld();
        ProgressData data = overworld.getDataStorage().computeIfAbsent(TYPE);
        if (!migrated) {
            migrated = true;
            migrateStaleDimensionCopies(level, overworld, data);
        }
        return data;
    }

    /**
     * Older versions stored progress in each dimension's own storage, so nodes
     * unlocked in the Overworld "disappeared" in the Nether/End. Merge any
     * stale per-dimension copies into the overworld (highest tier wins) and
     * clear them, so the merge happens exactly once per copy.
     */
    private static void migrateStaleDimensionCopies(ServerLevel level, ServerLevel overworld, ProgressData data) {
        for (ServerLevel other : level.getServer().getAllLevels()) {
            if (other == overworld) {
                continue;
            }
            ProgressData stale = other.getDataStorage().get(TYPE);
            if (stale != null && !stale.tiers.isEmpty()) {
                stale.tiers.forEach((key, value) -> data.tiers.merge(key, value, Math::max));
                data.setDirty();
                stale.tiers.clear();
                stale.setDirty();
            }
        }
    }

    /** Unlocked tier of a node for the player (0 if never touched). */
    public int tier(UUID uuid, String nodeKey) {
        return tiers.getOrDefault(uuid + "|" + nodeKey, 0);
    }

    /** All non-zero tiers of the player keyed by node key. */
    public Map<String, Integer> tiersFor(UUID uuid) {
        Map<String, Integer> out = new HashMap<>();
        String prefix = uuid + "|";
        tiers.forEach((key, value) -> {
            if (key.startsWith(prefix) && value > 0) {
                out.put(key.substring(prefix.length()), value);
            }
        });
        return out;
    }

    public void setTier(UUID uuid, String nodeKey, int tier) {
        if (tier <= 0) {
            tiers.remove(uuid + "|" + nodeKey);
        } else {
            tiers.put(uuid + "|" + nodeKey, tier);
        }
        setDirty();
    }

    public void reset(UUID uuid) {
        String prefix = uuid + "|";
        tiers.keySet().removeIf(key -> key.startsWith(prefix));
        setDirty();
    }

    private static ProgressData fromMap(Map<String, Integer> map) {
        ProgressData data = new ProgressData();
        data.tiers.putAll(map);
        return data;
    }

    private static Map<String, Integer> toMap(ProgressData data) {
        return new HashMap<>(data.tiers);
    }
}
