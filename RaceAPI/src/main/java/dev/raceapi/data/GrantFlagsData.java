package dev.raceapi.data;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks one-time grants (e.g. {@code grant_item} starting items) per player.
 * World saved data instead of player persistent data: the plain persistent
 * tag is NOT copied to the respawned player entity, so flags stored there were
 * lost on death and starting items were handed out again after every respawn.
 */
public class GrantFlagsData extends SavedData {

    public static final SavedDataType<GrantFlagsData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("raceapi", "raceapi_grant_flags"),
            GrantFlagsData::new,
            Codec.unboundedMap(Codec.STRING, Codec.BOOL)
                    .xmap(GrantFlagsData::fromMap, GrantFlagsData::toMap));

    private final Map<String, Boolean> flags = new HashMap<>();

    public static GrantFlagsData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static String key(java.util.UUID playerId, String grantId) {
        return playerId + "|" + grantId;
    }

    public boolean has(java.util.UUID playerId, String grantId) {
        return flags.getOrDefault(key(playerId, grantId), false);
    }

    public void mark(java.util.UUID playerId, String grantId) {
        flags.put(key(playerId, grantId), true);
        setDirty();
    }

    public void unmark(java.util.UUID playerId, String grantId) {
        if (flags.remove(key(playerId, grantId)) != null) {
            setDirty();
        }
    }

    public void clear(java.util.UUID playerId) {
        String prefix = playerId + "|";
        flags.keySet().removeIf(k -> k.startsWith(prefix));
        setDirty();
    }

    private static GrantFlagsData fromMap(Map<String, Boolean> map) {
        GrantFlagsData data = new GrantFlagsData();
        data.flags.putAll(map);
        return data;
    }

    private static Map<String, Boolean> toMap(GrantFlagsData data) {
        return new HashMap<>(data.flags);
    }
}
