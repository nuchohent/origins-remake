package dev.originsx.skilltree.tree;

import dev.raceapi.power.PowerFactory;
import dev.raceapi.race.Power;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of loaded skill trees plus a cache of parsed tier
 * clones (weak power copies). Clones are rebuilt whenever datapacks reload,
 * because they capture registry holders that become stale afterwards.
 */
public final class TreeManager {

    /** First tree registered for a race wins. */
    private static volatile Map<Identifier, SkillTree> BY_RACE = Map.of();
    private static volatile List<SkillTree> ALL = List.of();

    /** Cache key: {@code treeId/nodeId#tier} → parsed weak copy. */
    private static final Map<String, Power> CLONES = new ConcurrentHashMap<>();

    private TreeManager() {
    }

    /** Replaces all trees after a datapack (re)load and drops stale clones. */
    public static void reload(List<SkillTree> trees) {
        Map<Identifier, SkillTree> byRace = new HashMap<>();
        for (SkillTree tree : trees) {
            byRace.putIfAbsent(tree.raceId(), tree);
        }
        ALL = List.copyOf(trees);
        BY_RACE = Map.copyOf(byRace);
        CLONES.clear();
    }

    public static SkillTree treeFor(Identifier raceId) {
        return raceId == null ? null : BY_RACE.get(raceId);
    }

    public static Collection<SkillTree> trees() {
        return ALL;
    }

    /**
     * Parses (and caches) the clone of a node's power for tiers 1..3
     * (tier 3 is an unscaled full-strength copy).
     * Returns {@code null} if the definition failed to parse.
     */
    public static Power scaledClone(SkillTree tree, TreeNode node, int tier) {
        double factor = switch (tier) {
            case 1 -> TierScaler.TIER_1;
            case 2 -> TierScaler.TIER_2;
            default -> 1.0;
        };
        String key = nodeKey(tree, node) + "#" + tier;
        return CLONES.computeIfAbsent(key, k -> {
            Identifier cloneId = TreeNode.nodeId(tree.treeId(), node.id());
            return PowerFactory.create(cloneId, TierScaler.scale(node.power(), factor));
        });
    }

    /** Stable progress key of a node: {@code <treeId>/<nodeId>}. */
    public static String nodeKey(SkillTree tree, TreeNode node) {
        return tree.treeId() + "/" + node.id();
    }
}
