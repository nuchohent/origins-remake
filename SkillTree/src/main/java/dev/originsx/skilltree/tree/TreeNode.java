package dev.originsx.skilltree.tree;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * One unlockable node of a {@link SkillTree}.
 *
 * @param id       unique node id within its tree
 * @param index    index of the gated power in the race's {@code powers} array
 * @param power    inline power definition (same schema as raceapi power JSON);
 *                 parsed and numerically scaled per tier
 * @param x        grid column on the skill tree canvas
 * @param y        grid row on the skill tree canvas
 * @param requires node ids that must reach tier 3 before this node unlocks
 * @param cost     shard costs for tiers 1, 2 and 3
 */
public record TreeNode(String id, int index, JsonObject power, int x, int y,
                       List<String> requires, int[] cost) {

    public static final Identifier nodeId(Identifier treeId, String nodeId) {
        return Identifier.fromNamespaceAndPath(treeId.getNamespace(), treeId.getPath() + "/" + nodeId);
    }
}
