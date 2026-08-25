package dev.originsx.skilltree.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.originsx.skilltree.SkillTreeMod;
import dev.originsx.skilltree.tree.SkillTree;
import dev.originsx.skilltree.tree.TreeNode;
import dev.originsx.skilltree.tree.TreeManager;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads skill tree definitions from {@code data/<namespace>/skilltree/*.json}.
 * <pre>{@code
 * {
 *   "race": "raceapi:dryad",
 *   "title": "tree.originsx_skilltree.dryad",
 *   "nodes": [
 *     {
 *       "id": "dryad_day_boost",
 *       "index": 0,
 *       "power": {"type": "day_boost", "difficulty": 1},
 *       "x": 0, "y": 0,
 *       "requires": [],
 *       "cost": [2, 3, 5]
 *     }
 *   ]
 * }
 * }</pre>
 */
public class SkillTreeLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    private static final FileToIdConverter CONVERTER = FileToIdConverter.json("skilltree");

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier id = CONVERTER.fileToId(entry.getKey());
            try (InputStream stream = entry.getValue().open()) {
                map.put(id, JsonParser.parseReader(new InputStreamReader(stream)));
            } catch (Exception e) {
                SkillTreeMod.LOGGER.error("Failed to read skill tree {}: {}", id, e.getMessage());
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        List<SkillTree> trees = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> entry : objects.entrySet()) {
            Identifier id = entry.getKey();
            try {
                SkillTree tree = parse(id, entry.getValue().getAsJsonObject());
                if (tree != null) {
                    trees.add(tree);
                }
            } catch (Exception e) {
                SkillTreeMod.LOGGER.error("Failed to parse skill tree {}: {}", id, e.getMessage());
            }
        }
        TreeManager.reload(trees);
    }

    private static SkillTree parse(Identifier treeId, JsonObject json) {
        String raceStr = requiredString(json, "race");
        Identifier raceId = raceStr == null ? null : Identifier.tryParse(raceStr);
        if (raceId == null) {
            SkillTreeMod.LOGGER.error("Skill tree {} is missing a valid 'race' field", treeId);
            return null;
        }
        if (!json.has("nodes") || !json.get("nodes").isJsonArray()) {
            SkillTreeMod.LOGGER.error("Skill tree {} has no 'nodes' array", treeId);
            return null;
        }
        String title = "";
        if (json.has("title") && json.get("title").isJsonPrimitive()) {
            title = json.get("title").getAsString();
        }
        List<TreeNode> nodes = new ArrayList<>();
        Map<Integer, TreeNode> byIndex = new HashMap<>();
        var array = json.getAsJsonArray("nodes");
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            TreeNode node = parseNode(treeId, element.getAsJsonObject());
            if (node != null) {
                nodes.add(node);
                byIndex.put(node.index(), node);
            }
        }
        return new SkillTree(treeId, raceId, title, List.copyOf(nodes), Map.copyOf(byIndex),
                json.toString());
    }

    private static TreeNode parseNode(Identifier treeId, JsonObject json) {
        String nodeId = requiredString(json, "id");
        if (!json.has("index") || !json.get("index").isJsonPrimitive()) {
            SkillTreeMod.LOGGER.error("Node '{}' in tree {} is missing its power index", nodeId, treeId);
            return null;
        }
        int index = json.get("index").getAsInt();
        if (!json.has("power") || !json.get("power").isJsonObject()) {
            SkillTreeMod.LOGGER.error("Node '{}' in tree {} has no inline 'power' definition", nodeId, treeId);
            return null;
        }
        int x = json.has("x") ? json.get("x").getAsInt() : 0;
        int y = json.has("y") ? json.get("y").getAsInt() : 0;
        List<String> requires = new ArrayList<>();
        if (json.has("requires") && json.get("requires").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("requires")) {
                if (element.isJsonPrimitive()) {
                    requires.add(element.getAsString());
                }
            }
        }
        int[] cost = new int[]{1, 2, 4};
        if (json.has("cost") && json.get("cost").isJsonArray()) {
            var costArray = json.getAsJsonArray("cost");
            int[] parsed = new int[costArray.size()];
            for (int i = 0; i < parsed.length; i++) {
                parsed[i] = Math.max(0, costArray.get(i).getAsInt());
            }
            cost = parsed;
        }
        return new TreeNode(nodeId, index, json.getAsJsonObject("power").deepCopy(), x, y,
                List.copyOf(requires), cost);
    }

    private static String requiredString(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }
}
