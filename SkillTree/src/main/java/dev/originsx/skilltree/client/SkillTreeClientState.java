package dev.originsx.skilltree.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.originsx.skilltree.net.SyncSkillStatePayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side cache of the player's skill tree state, filled from
 * {@link SyncSkillStatePayload}.
 */
@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
public final class SkillTreeClientState {

    /** Client mirror of a tree node (rendering-only subset). */
    public record ClientNode(String id, int index, int x, int y,
                             List<String> requires, int[] cost,
                             String displayName, String description,
                             String icon, JsonObject power) {
    }

    private static Identifier raceId;
    private static String title = "";
    private static String rawJson = "";
    private static List<ClientNode> nodes = List.of();
    private static final Map<String, Integer> TIERS = new HashMap<>();

    private SkillTreeClientState() {
    }

    public static Identifier raceId() {
        return raceId;
    }

    public static String title() {
        return title;
    }

    /** Raw tree JSON as sent by the server; empty when no tree is loaded. */
    public static String rawJson() {
        return rawJson;
    }

    public static List<ClientNode> nodes() {
        return nodes;
    }

    public static ClientNode node(String nodeId) {
        for (ClientNode node : nodes) {
            if (node.id().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    public static int tierOf(String nodeId) {
        return TIERS.getOrDefault(nodeId, 0);
    }

    /** Whether every required node is fully upgraded. */
    public static boolean requirementsMet(ClientNode node) {
        for (String required : node.requires()) {
            if (tierOf(required) < 3) {
                return false;
            }
        }
        return true;
    }

    public static void apply(SyncSkillStatePayload payload) {
        TIERS.clear();
        for (String line : payload.tiersData().split("\n")) {
            int eq = line.lastIndexOf('=');
            if (eq > 0) {
                try {
                    TIERS.put(line.substring(0, eq), Integer.parseInt(line.substring(eq + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (payload.raceId().isEmpty() || payload.treeJson().isEmpty()) {
            raceId = null;
            title = "";
            rawJson = "";
            nodes = List.of();
            return;
        }
        raceId = Identifier.tryParse(payload.raceId());
        rawJson = payload.treeJson();
        ParsedTree parsed = parseTree(payload.treeJson());
        title = parsed.title();
        nodes = parsed.nodes();
        SkillTreeScreen.notifyStateApplied();
    }

    private static ClientNode parseNode(JsonObject json) {
        if (!json.has("id") || !json.has("power")) {
            return null;
        }
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
            JsonArray array = json.getAsJsonArray("cost");
            int[] parsed = new int[array.size()];
            for (int i = 0; i < parsed.length; i++) {
                parsed[i] = Math.max(0, array.get(i).getAsInt());
            }
            cost = parsed;
        }
        JsonObject power = json.getAsJsonObject("power");
        String type = power.has("type") && power.get("type").isJsonPrimitive()
                ? power.get("type").getAsString() : "";
        String typePath = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        String name = stringOr(power, "display_name",
                type.isEmpty() ? "" : "power.raceapi." + typePath + ".name");
        String description = stringOr(power, "description",
                type.isEmpty() ? "" : "power.raceapi." + typePath + ".desc");
        return new ClientNode(json.get("id").getAsString(),
                json.has("index") ? json.get("index").getAsInt() : -1,
                json.has("x") ? json.get("x").getAsInt() : 0,
                json.has("y") ? json.get("y").getAsInt() : 0,
                List.copyOf(requires), cost, name, description,
                stringOr(json, "icon", ""), power.deepCopy());
    }

    /** Parses a raw tree JSON document into renderable nodes (also used by the editor). */
    public static ParsedTree parseTree(String treeJson) {
        try {
            JsonObject json = JsonParser.parseString(treeJson).getAsJsonObject();
            String t = json.has("title") && json.get("title").isJsonPrimitive()
                    ? json.get("title").getAsString() : "";
            List<ClientNode> parsed = new ArrayList<>();
            if (json.has("nodes") && json.get("nodes").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("nodes")) {
                    if (element.isJsonObject()) {
                        ClientNode node = parseNode(element.getAsJsonObject());
                        if (node != null) {
                            parsed.add(node);
                        }
                    }
                }
            }
            return new ParsedTree(t, List.copyOf(parsed));
        } catch (Exception e) {
            return new ParsedTree("", List.of());
        }
    }

    public record ParsedTree(String title, List<ClientNode> nodes) {
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : fallback;
    }
}
