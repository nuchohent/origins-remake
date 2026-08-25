package dev.originsx.skilltree.tree;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * A skill tree bound to one race. Nodes reference their race's powers by
 * <em>index</em> (position in the race JSON {@code powers} array) and carry a
 * full inline power definition used to build the weaker tier copies.
 *
 * @param treeId  id of this tree definition (the datapack file id)
 * @param raceId  race this tree belongs to
 * @param title   optional translation key / literal shown as the screen title
 * @param nodes   nodes in definition order
 * @param byIndex node for each power index of the race ({@code null} = that
 *                power is not gated and always works at full strength)
 * @param raw     original JSON text, sent verbatim to clients for rendering
 */
public record SkillTree(Identifier treeId, Identifier raceId, String title, List<TreeNode> nodes,
                        Map<Integer, TreeNode> byIndex, String raw) {

    /** Returns the tier cost in shards for unlocking {@code tier} (1..3), or -1. */
    public int cost(TreeNode node, int tier) {
        if (tier < 1 || tier > 3 || node.cost().length < tier) {
            return -1;
        }
        return node.cost()[tier - 1];
    }

    public TreeNode node(String nodeId) {
        for (TreeNode node : nodes) {
            if (node.id().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }
}
