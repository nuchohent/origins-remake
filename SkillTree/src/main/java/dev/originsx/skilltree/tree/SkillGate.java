package dev.originsx.skilltree.tree;

import dev.originsx.skilltree.progress.ProgressData;
import dev.raceapi.api.PowerPipeline;
import dev.raceapi.player.RaceManager;
import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges Race API and the skill trees: installs the {@link PowerPipeline}
 * effective-powers hook so every race-power flow (attach, remove, tick, damage,
 * keybinds...) sees the tier-filtered power list, and validates/consumes
 * unlock requests.
 */
public final class SkillGate {

    private SkillGate() {
    }

    /** Installs the global hook. Called once from the mod constructor. */
    public static void install() {
        PowerPipeline.setEffectivePowers(SkillGate::effective);
    }

    /**
     * The tier-adjusted power list of {@code race} for {@code player}. Keeps the
     * size and order of {@code race.getPowers()}; powers without a tree node are
     * passed through untouched.
     */
    public static List<Power> effective(ServerPlayer player, Race race) {
        SkillTree tree = TreeManager.treeFor(race.getId());
        if (tree == null) {
            return race.getPowers();
        }
        List<Power> original = race.getPowers();
        ProgressData progress = ProgressData.get(player.level());
        Map<String, Integer> tiers = progress.tiersFor(player.getUUID());
        List<Power> out = new ArrayList<>(original.size());
        for (int i = 0; i < original.size(); i++) {
            TreeNode node = tree.byIndex().get(i);
            if (node == null) {
                out.add(original.get(i));
                continue;
            }
            int tier = tiers.getOrDefault(TreeManager.nodeKey(tree, node), 0);
            // Every unlocked tier (including 3) runs the node's own inline power;
            // tier 3 clones are unscaled, so they are full strength.
            Power clone = tier == 0 ? null : TreeManager.scaledClone(tree, node, tier);
            out.add(TieredPower.of(original.get(i), clone, tier));
        }
        return out;
    }

    public enum UnlockResult {
        SUCCESS,
        NO_TREE,
        UNKNOWN_NODE,
        MAX_TIER,
        REQUIREMENTS_NOT_MET,
        NOT_ENOUGH_SHARDS
    }

    /**
     * Validates and performs one unlock/upgrade step. On success consumes the
     * shard cost and returns the new tier; the caller is responsible for
     * re-applying the player's race and syncing state.
     */
    public static UnlockResult tryUnlock(ServerPlayer player, String nodeId, int[] newTierOut) {
        var race = RaceManager.getRace(player);
        SkillTree tree = race == null ? null : TreeManager.treeFor(race.getId());
        if (race == null || tree == null) {
            return UnlockResult.NO_TREE;
        }
        TreeNode node = tree.node(nodeId);
        if (node == null) {
            return UnlockResult.UNKNOWN_NODE;
        }
        ProgressData progress = ProgressData.get(player.level());
        UUID uuid = player.getUUID();
        int tier = progress.tier(uuid, TreeManager.nodeKey(tree, node));
        if (tier >= 3) {
            return UnlockResult.MAX_TIER;
        }
        for (String requiredId : node.requires()) {
            TreeNode required = tree.node(requiredId);
            if (required == null
                    || progress.tier(uuid, TreeManager.nodeKey(tree, required)) < 3) {
                return UnlockResult.REQUIREMENTS_NOT_MET;
            }
        }
        int nextTier = tier + 1;
        int cost = tree.cost(node, nextTier);
        if (cost < 0) {
            return UnlockResult.MAX_TIER;
        }
        if (!Shards.has(player, cost)) {
            return UnlockResult.NOT_ENOUGH_SHARDS;
        }
        Shards.consume(player, cost);
        progress.setTier(uuid, TreeManager.nodeKey(tree, node), nextTier);
        newTierOut[0] = nextTier;
        return UnlockResult.SUCCESS;
    }
}