package dev.raceapi.power.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry of condition types and factory for parsing them from JSON.
 * <p>
 * Other mods can register custom conditions via {@link #register(String, Function)}.
 * <p>
 * Built-in condition types:
 * <ul>
 *   <li>{@code in_water} / {@code not_in_water} — player is submerged</li>
 *   <li>{@code eye_in_water} — water level reaches the player's eyes</li>
 *   <li>{@code on_ground} / {@code in_air} — player standing on ground / airborne</li>
 *   <li>{@code is_day} / {@code is_night} — world time of day</li>
 *   <li>{@code time_between} — overworld clock ticks between {@code min} (inclusive) and {@code max}</li>
 *   <li>{@code moon_phase} — current moon phase name ({@code full_moon}, {@code new_moon}, ...)</li>
 *   <li>{@code is_raining} / {@code thundering} — weather at the player's position</li>
 *   <li>{@code is_sprinting} / {@code is_crouching} / {@code is_swimming} / {@code climbing} — movement state</li>
 *   <li>{@code is_on_fire} — player is burning</li>
 *   <li>{@code is_falling} — player is in a falling state</li>
 *   <li>{@code health_below} / {@code health_above} — health threshold (0.0–1.0 fraction)</li>
 *   <li>{@code is_full_health} — player has full health</li>
 *   <li>{@code hunger_below} / {@code hunger_above} — food level threshold (0–20 points)</li>
 *   <li>{@code air_below} / {@code air_above} — breath supply threshold (0.0–1.0 fraction)</li>
 *   <li>{@code xp_level_below} / {@code xp_level_above} — experience level threshold</li>
 *   <li>{@code has_effect} — player has a specific potion effect</li>
 *   <li>{@code light_level_below} / {@code light_level_above} — effective light at player position</li>
 *   <li>{@code held_item} / {@code offhand_item} — item in hand matching an id or tag</li>
 *   <li>{@code wearing_item} — item in a specific armor slot matching an id or tag</li>
 *   <li>{@code has_armor} — player is wearing at least one armor piece</li>
 *   <li>{@code armor_count_above} / {@code armor_count_below} — number of worn armor pieces</li>
 *   <li>{@code standing_on} — block under the player matches an id or tag</li>
 *   <li>{@code in_biome} — player is in a biome matching a tag</li>
 *   <li>{@code biome_id} — player is in a specific biome</li>
 *   <li>{@code in_dimension} — player is in a specific dimension</li>
 *   <li>{@code below_y} / {@code above_y} — player Y position relative to threshold</li>
 *   <li>{@code entities_nearby_above} / {@code entities_nearby_below} — living entity count in radius</li>
 *   <li>{@code riding} — player is riding (optionally a specific entity type)</li>
 *   <li>{@code gamemode} — player game mode ({@code survival}, {@code creative}, ...)</li>
 *   <li>{@code scoreboard_above} / {@code scoreboard_below} — scoreboard objective value</li>
 *   <li>{@code not} — negates another condition</li>
 *   <li>{@code all_of} — true while ALL sub-conditions in the {@code conditions} array hold</li>
 *   <li>{@code any_of} — true while ANY sub-condition in the {@code conditions} array holds</li>
 * </ul>
 */
public final class Conditions {

    private static final Logger LOGGER = LoggerFactory.getLogger(Conditions.class);
    private static final Map<String, Function<JsonObject, Condition>> TYPES = new LinkedHashMap<>();

    private Conditions() {
    }

    /**
     * Registers a condition type. Call this from your mod's constructor.
     *
     * @param type    the condition_type string (e.g. "my_custom_condition")
     * @param factory receives the full JSON object, should return a Condition
     */
    public static void register(String type, Function<JsonObject, Condition> factory) {
        if (TYPES.putIfAbsent(type, factory) != null) {
            throw new IllegalArgumentException("Duplicate condition type: " + type);
        }
    }

    /** All registered condition type names, in registration order. */
    public static Collection<String> getTypeNames() {
        return TYPES.keySet();
    }

    /** Whether a condition type name is registered. */
    public static boolean contains(String type) {
        return TYPES.containsKey(type);
    }

    public static Condition parse(JsonObject json) {
        String type = json.has("condition_type") ? json.get("condition_type").getAsString() : "";
        if (type.isEmpty()) {
            LOGGER.error("Condition missing 'condition_type' field");
            return player -> false;
        }

        Function<JsonObject, Condition> factory = TYPES.get(type);
        if (factory == null) {
            LOGGER.error("Unknown condition type '{}'", type);
            return player -> false;
        }
        try {
            return factory.apply(json);
        } catch (Exception e) {
            LOGGER.error("Condition '{}' failed to parse", type, e);
            return player -> false;
        }
    }

    private static Condition healthCondition(JsonObject json, boolean below) {
        double threshold = getDouble(json, "threshold", 0.5);
        return below
                ? player -> player.getHealth() / player.getMaxHealth() < threshold
                : player -> player.getHealth() / player.getMaxHealth() > threshold;
    }

    private static Condition lightCondition(JsonObject json, boolean below) {
        int threshold = getInt(json, "threshold", 7);
        return below
                ? player -> player.level().getMaxLocalRawBrightness(player.blockPosition()) < threshold
                : player -> player.level().getMaxLocalRawBrightness(player.blockPosition()) > threshold;
    }

    private static Condition hasEffectCondition(JsonObject json) {
        String effectId = getString(json, "effect", "");
        Identifier loc = Identifier.tryParse(effectId);
        if (loc == null) {
            LOGGER.error("Condition has_effect: unknown effect '{}'", effectId);
            return player -> false;
        }
        var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(loc);
        if (holder.isEmpty()) {
            LOGGER.error("Condition has_effect: effect '{}' not found", effectId);
            return player -> false;
        }
        var effectHolder = holder.get();
        return player -> player.getEffect(effectHolder) != null;
    }

    private static Condition biomeCondition(JsonObject json) {
        String tagId = getString(json, "tag", "");
        // tolerate a leading '#' so "#minecraft:is_forest" and "minecraft:is_forest" both work
        Identifier loc = Identifier.tryParse(tagId.replaceFirst("^#", ""));
        if (loc == null) {
            LOGGER.error("Condition in_biome: invalid tag '{}'", tagId);
            return player -> false;
        }
        TagKey<Biome> tag = TagKey.create(Registries.BIOME, loc);
        return player -> player.level().getBiome(player.blockPosition()).is(tag);
    }

    private static Condition dimensionCondition(JsonObject json) {
        String dimId = getString(json, "dimension", "");
        Identifier loc = Identifier.tryParse(dimId);
        if (loc == null) {
            LOGGER.error("Condition in_dimension: invalid dimension '{}'", dimId);
            return player -> false;
        }
        return player -> player.level().dimension().identifier().equals(loc);
    }

    /**
     * Matches an {@link net.minecraft.world.item.ItemStack} against an
     * {@code item} id (e.g. {@code minecraft:diamond_sword}) or an
     * {@code item_tag} (e.g. {@code minecraft:swords}). At least one of the
     * two fields must resolve.
     */
    private static java.util.function.Predicate<net.minecraft.world.item.ItemStack> itemMatcher(JsonObject json) {
        String itemId = getString(json, "item", "");
        String tagId = getString(json, "item_tag", "");
        Identifier itemLoc = itemId.isEmpty() ? null : Identifier.tryParse(itemId);
        TagKey<net.minecraft.world.item.Item> tag = tagId.isEmpty() ? null
                : TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                        Identifier.tryParse(tagId.startsWith("#") ? tagId.substring(1) : tagId));
        if (itemId.isEmpty() && tag == null) {
            LOGGER.error("Condition needs either 'item' or 'item_tag' field");
            return stack -> false;
        }
        if (!itemId.isEmpty() && itemLoc == null) {
            LOGGER.error("Condition: invalid item id '{}'", itemId);
            return stack -> false;
        }
        var knownItem = itemLoc == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemLoc);
        if (knownItem != null && knownItem.isEmpty()) {
            LOGGER.error("Condition: unknown item '{}'", itemId);
            return stack -> false;
        }
        return stack -> {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            if (tag != null && stack.typeHolder().is(tag)) {
                return true;
            }
            return knownItem != null && stack.is(knownItem.get().value());
        };
    }

    private static Condition heldItemCondition(JsonObject json) {
        var matcher = itemMatcher(json);
        return player -> matcher.test(player.getMainHandItem());
    }

    private static Condition offhandItemCondition(JsonObject json) {
        var matcher = itemMatcher(json);
        return player -> matcher.test(player.getOffhandItem());
    }

    private static Condition wearingItemCondition(JsonObject json) {
        String slotStr = getString(json, "slot", "").toUpperCase(java.util.Locale.ROOT);
        net.minecraft.world.entity.EquipmentSlot slot;
        try {
            slot = net.minecraft.world.entity.EquipmentSlot.valueOf(slotStr);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Condition wearing_item: invalid slot '{}' (use head/chest/legs/feet)", getString(json, "slot", ""));
            return player -> false;
        }
        var matcher = itemMatcher(json);
        return player -> matcher.test(player.getItemBySlot(slot));
    }

    private static Condition armorCountCondition(JsonObject json, boolean above) {
        int threshold = getInt(json, "threshold", 1);
        return above
                ? player -> wornArmorCount(player) > threshold
                : player -> wornArmorCount(player) < threshold;
    }

    private static int wornArmorCount(ServerPlayer player) {
        int count = 0;
        for (net.minecraft.world.entity.EquipmentSlot s : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET}) {
            if (!player.getItemBySlot(s).isEmpty()) count++;
        }
        return count;
    }

    private static Condition hungerCondition(JsonObject json, boolean below) {
        int threshold = getInt(json, "threshold", 10);
        return below
                ? player -> player.getFoodData().getFoodLevel() < threshold
                : player -> player.getFoodData().getFoodLevel() > threshold;
    }

    private static Condition airCondition(JsonObject json, boolean below) {
        double threshold = getDouble(json, "threshold", 0.5);
        return below
                ? player -> player.getMaxAirSupply() <= 0
                        || (double) player.getAirSupply() / player.getMaxAirSupply() < threshold
                : player -> (double) player.getAirSupply() / player.getMaxAirSupply() > threshold;
    }

    private static Condition xpLevelCondition(JsonObject json, boolean above) {
        int threshold = getInt(json, "threshold", 0);
        return above
                ? player -> player.experienceLevel > threshold
                : player -> player.experienceLevel < threshold;
    }

    /**
     * Counts living entities within {@code radius} blocks around the player.
     * Optionally filtered by an {@code entity} type id or an {@code entity_tag}
     * tag. The player themselves is never counted.
     */
    private static Condition entitiesNearbyCondition(JsonObject json, boolean above) {
        double radius = getDouble(json, "radius", 8.0);
        int threshold = getInt(json, "threshold", 1);
        boolean excludeSelf = !json.has("include_self") || !json.get("include_self").getAsBoolean();

        final Identifier entityLoc;
        final TagKey<net.minecraft.world.entity.EntityType<?>> entityTag;
        String entityId = getString(json, "entity", "");
        String tagId = getString(json, "entity_tag", "");
        entityLoc = entityId.isEmpty() ? null : Identifier.tryParse(entityId);
        entityTag = tagId.isEmpty() ? null
                : TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                        Identifier.tryParse(tagId.startsWith("#") ? tagId.substring(1) : tagId));
        if (!entityId.isEmpty() && entityLoc == null) {
            LOGGER.error("entities_nearby: invalid entity id '{}'", entityId);
            return player -> false;
        }

        return player -> {
            net.minecraft.world.phys.Vec3 c = player.position();
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
            int count = 0;
            for (net.minecraft.world.entity.LivingEntity e : player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class, box,
                    e -> e.isAlive() && (!excludeSelf || e != player))) {
                if (entityLoc != null
                        && !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(entityLoc)) {
                    continue;
                }
                if (entityTag != null && !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .wrapAsHolder(e.getType()).is(entityTag)) {
                    continue;
                }
                count++;
            }
            return above ? count > threshold : count < threshold;
        };
    }

    private static Condition standingOnCondition(JsonObject json) {
        String blockId = getString(json, "block", "");
        String tagId = getString(json, "block_tag", "");
        Identifier blockLoc = blockId.isEmpty() ? null : Identifier.tryParse(blockId);
        TagKey<net.minecraft.world.level.block.Block> tag = tagId.isEmpty() ? null
                : TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                        Identifier.tryParse(tagId.startsWith("#") ? tagId.substring(1) : tagId));
        if (blockLoc == null && tag == null) {
            LOGGER.error("standing_on needs either 'block' or 'block_tag' field");
            return player -> false;
        }
        var knownBlock = blockLoc == null ? null : net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(blockLoc);
        if (knownBlock != null && knownBlock.isEmpty()) {
            LOGGER.error("standing_on: unknown block '{}'", blockId);
            return player -> false;
        }
        return player -> {
            var state = player.level().getBlockState(player.blockPosition().below());
            if (tag != null && state.is(tag)) {
                return true;
            }
            return knownBlock != null && state.is(knownBlock.get().value());
        };
    }

    private static Condition biomeIdCondition(JsonObject json) {
        String biomeId = getString(json, "biome", "");
        Identifier loc = Identifier.tryParse(biomeId);
        if (loc == null) {
            LOGGER.error("Condition biome_id: invalid biome '{}'", biomeId);
            return player -> false;
        }
        return player -> player.level().getBiome(player.blockPosition())
                .unwrapKey()
                .map(key -> key.identifier().equals(loc))
                .orElse(false);
    }

    private static Condition moonPhaseCondition(JsonObject json) {
        String phaseName = getString(json, "phase", "full_moon").toLowerCase(java.util.Locale.ROOT);
        net.minecraft.world.level.MoonPhase target = null;
        for (net.minecraft.world.level.MoonPhase phase : net.minecraft.world.level.MoonPhase.values()) {
            if (phase.getSerializedName().equals(phaseName)) {
                target = phase;
                break;
            }
        }
        if (target == null) {
            LOGGER.error("Condition moon_phase: unknown phase '{}' (full_moon, waning_gibbous, third_quarter, "
                    + "waning_crescent, new_moon, waxing_crescent, first_quarter, waxing_gibbous)", phaseName);
            return player -> false;
        }
        net.minecraft.world.level.MoonPhase expected = target;
        return player -> player.level().environmentAttributes()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, player.blockPosition()) == expected;
    }

    private static Condition timeBetweenCondition(JsonObject json) {
        int min = getInt(json, "min", 0);
        int max = getInt(json, "max", 24000);
        return player -> {
            long tick = Math.floorMod(player.level().getOverworldClockTime(), 24000L);
            return tick >= min && tick < max;
        };
    }

    private static Condition scoreboardCondition(JsonObject json, boolean above) {
        String objective = getString(json, "objective", "");
        int threshold = getInt(json, "threshold", 0);
        if (objective.isEmpty()) {
            LOGGER.error("scoreboard condition needs an 'objective' field");
            return player -> false;
        }
        return above
                ? player -> readScore(player, objective) > threshold
                : player -> readScore(player, objective) < threshold;
    }

    private static int readScore(ServerPlayer player, String objectiveName) {
        var objective = player.level().getScoreboard().getObjective(objectiveName);
        if (objective == null) {
            return 0;
        }
        return player.level().getScoreboard()
                .getOrCreatePlayerScore(player, objective)
                .get();
    }

    private static Condition gamemodeCondition(JsonObject json) {
        String mode = getString(json, "mode", "survival").toLowerCase(java.util.Locale.ROOT);
        net.minecraft.world.level.GameType gameType = net.minecraft.world.level.GameType.byName(mode, null);
        if (gameType == null) {
            LOGGER.error("Condition gamemode: unknown mode '{}'", mode);
            return player -> false;
        }
        net.minecraft.world.level.GameType expected = gameType;
        return player -> player.gameMode() == expected;
    }

    private static Condition ridingCondition(JsonObject json) {
        String entityId = getString(json, "entity", "");
        Identifier loc = entityId.isEmpty() ? null : Identifier.tryParse(entityId);
        if (!entityId.isEmpty() && loc == null) {
            LOGGER.error("Condition riding: invalid entity id '{}'", entityId);
            return player -> false;
        }
        return player -> {
            net.minecraft.world.entity.Entity vehicle = player.getVehicle();
            if (vehicle == null) {
                return false;
            }
            return loc == null || net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(vehicle.getType()).equals(loc);
        };
    }

    /**
     * Builds a human-readable (translatable) description of the condition from
     * its JSON, e.g. {@code At night} or {@code Holding #minecraft:swords}.
     * Composite conditions are joined recursively. Unknown types fall back to
     * their raw type name so custom registered conditions still render.
     */
    public static net.minecraft.network.chat.Component describe(JsonObject json) {
        String type = json.has("condition_type") && json.get("condition_type").isJsonPrimitive()
                ? json.get("condition_type").getAsString() : "";
        String key = "condition.raceapi." + type;
        try {
            switch (type) {
                case "in_water": case "not_in_water": case "on_ground": case "in_air":
                case "is_day": case "is_night": case "is_sprinting": case "is_crouching":
                case "is_swimming": case "climbing": case "is_on_fire": case "is_falling":
                case "is_raining": case "thundering": case "has_armor": case "is_full_health":
                case "eye_in_water":
                    return net.minecraft.network.chat.Component.translatable(key);
                case "health_below": case "health_above": {
                    double t = getDouble(json, "threshold", 0.5);
                    return net.minecraft.network.chat.Component.translatable(key, Math.round(t * 100.0));
                }
                case "air_below": case "air_above": {
                    double t = getDouble(json, "threshold", 0.5);
                    return net.minecraft.network.chat.Component.translatable(key, Math.round(t * 100.0));
                }
                case "hunger_below": case "hunger_above":
                case "light_level_below": case "light_level_above":
                case "xp_level_above": case "xp_level_below":
                case "armor_count_above": case "armor_count_below":
                    return net.minecraft.network.chat.Component.translatable(key, getInt(json, "threshold", 0));
                case "below_y": case "above_y":
                    return net.minecraft.network.chat.Component.translatable(key, getInt(json, "threshold", 0));
                case "entities_nearby_above": case "entities_nearby_below":
                    return net.minecraft.network.chat.Component.translatable(key,
                            getInt(json, "threshold", 1), wholeNumber(getDouble(json, "radius", 8.0)));
                case "time_between":
                    return net.minecraft.network.chat.Component.translatable(key,
                            getInt(json, "min", 0), getInt(json, "max", 24000));
                case "gamemode":
                    return net.minecraft.network.chat.Component.translatable(key,
                            getString(json, "mode", "survival"));
                case "scoreboard_above": case "scoreboard_below":
                    return net.minecraft.network.chat.Component.translatable(key,
                            getString(json, "objective", ""), getInt(json, "threshold", 0));
                case "held_item": case "offhand_item": case "wearing_item": case "standing_on":
                    return net.minecraft.network.chat.Component.translatable(key, matcherTargetLabel(json, type));
                case "in_biome":
                    return net.minecraft.network.chat.Component.translatable(key,
                            "#" + getString(json, "tag", "").replaceFirst("^#", ""));
                case "biome_id":
                    return net.minecraft.network.chat.Component.translatable(key, registryNameLabel(
                            "biome", getString(json, "biome", "")));
                case "in_dimension":
                    return net.minecraft.network.chat.Component.translatable(key, registryNameLabel(
                            "dimension", getString(json, "dimension", "")));
                case "moon_phase":
                    return net.minecraft.network.chat.Component.translatable(key,
                            net.minecraft.network.chat.Component.translatable("condition.raceapi.moon_phase."
                                    + getString(json, "phase", "full_moon")));
                case "riding": {
                    String entity = getString(json, "entity", "");
                    if (entity.isEmpty()) {
                        return net.minecraft.network.chat.Component.translatable(key);
                    }
                    return net.minecraft.network.chat.Component.translatable(key + ".specific",
                            entityTypeLabel(entity));
                }
                case "has_effect": {
                    String effectId = getString(json, "effect", "");
                    Identifier loc = Identifier.tryParse(effectId);
                    if (loc != null) {
                        var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(loc);
                        if (holder.isPresent()) {
                            return net.minecraft.network.chat.Component.translatable(key,
                                    holder.get().value().getDisplayName());
                        }
                    }
                    return net.minecraft.network.chat.Component.translatable(key,
                            net.minecraft.network.chat.Component.literal(effectId));
                }
                case "not": {
                    JsonObject inner = json.has("inner") && json.get("inner").isJsonObject()
                            ? json.getAsJsonObject("inner") : new JsonObject();
                    return net.minecraft.network.chat.Component.translatable(key, describe(inner));
                }
                case "all_of": case "any_of": {
                    List<net.minecraft.network.chat.Component> parts = new ArrayList<>();
                    for (JsonObject element : subConditionJsons(json)) {
                        parts.add(describe(element));
                    }
                    if (parts.isEmpty()) {
                        return net.minecraft.network.chat.Component.literal(type);
                    }
                    net.minecraft.network.chat.Component joined = parts.get(0);
                    String joinKey = "all_of".equals(type) ? "condition.raceapi.and" : "condition.raceapi.or";
                    for (int i = 1; i < parts.size(); i++) {
                        joined = net.minecraft.network.chat.Component.translatable(joinKey, joined, parts.get(i));
                    }
                    return joined;
                }
                default:
                    return net.minecraft.network.chat.Component.literal(type.isEmpty() ? "unknown" : type);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to describe condition '{}'", type, e);
            return net.minecraft.network.chat.Component.literal(type);
        }
    }

    /** Renders whole doubles as integers so "8.0 blocks" becomes "8 blocks". */
    private static Number wholeNumber(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value) ? (long) value : value;
    }

    /** Label for the matched target of item/block conditions: an item/block/entity name or a #tag. */
    private static net.minecraft.network.chat.Component matcherTargetLabel(JsonObject json, String type) {
        switch (type) {
            case "standing_on": {
                String tag = getString(json, "block_tag", "");
                if (!tag.isEmpty()) {
                    return net.minecraft.network.chat.Component.literal("#" + tag.replaceFirst("^#", ""));
                }
                String blockId = getString(json, "block", "");
                Identifier loc = Identifier.tryParse(blockId);
                if (loc != null) {
                    var holder = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(loc);
                    if (holder.isPresent()) {
                        return holder.get().value().getName();
                    }
                }
                return net.minecraft.network.chat.Component.literal(blockId);
            }
            default: {
                String tag = getString(json, "item_tag", "");
                if (!tag.isEmpty()) {
                    return net.minecraft.network.chat.Component.literal("#" + tag.replaceFirst("^#", ""));
                }
                String itemId = getString(json, "item", "");
                Identifier loc = Identifier.tryParse(itemId);
                if (loc != null) {
                    var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc);
                    if (holder.isPresent()) {
                        // NOTE: never construct an ItemStack here — during early
                        // data load item components are not bound yet and the
                        // constructor throws. The description id renders fine.
                        return net.minecraft.network.chat.Component.translatable(
                                holder.get().value().getDescriptionId());
                    }
                }
                return net.minecraft.network.chat.Component.literal(itemId);
            }
        }
    }

    private static net.minecraft.network.chat.Component entityTypeLabel(String entityId) {
        Identifier loc = Identifier.tryParse(entityId);
        if (loc != null) {
            var holder = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(loc);
            if (holder.isPresent()) {
                return holder.get().value().getDescription();
            }
        }
        return net.minecraft.network.chat.Component.literal(entityId);
    }

    private static net.minecraft.network.chat.Component registryNameLabel(String prefix, String id) {
        Identifier loc = Identifier.tryParse(id);
        if (loc == null) {
            return net.minecraft.network.chat.Component.literal(id);
        }
        return net.minecraft.network.chat.Component.translatable(prefix + "." + loc.getNamespace() + "." + loc.getPath());
    }

    private static Condition notCondition(JsonObject json) {
        JsonObject inner = json.has("inner") && json.get("inner").isJsonObject()
                ? json.getAsJsonObject("inner") : new JsonObject();
        Condition innerCondition = parse(inner);
        return player -> !innerCondition.test(player);
    }

    private static List<JsonObject> subConditionJsons(JsonObject json) {
        List<JsonObject> result = new ArrayList<>();
        if (json.has("conditions") && json.get("conditions").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("conditions")) {
                if (element.isJsonObject()) {
                    result.add(element.getAsJsonObject());
                }
            }
        }
        return result;
    }

    /**
     * Parses the sub-conditions of a composite ({@code conditions} array).
     * Each entry uses the same schema as a top-level condition, so composites
     * nest freely. Throws on empty input - {@link #parse(JsonObject)} turns
     * that into a never-true condition with an error log.
     */
    private static List<Condition> subConditions(JsonObject json) {
        List<Condition> result = new ArrayList<>();
        for (JsonObject element : subConditionJsons(json)) {
            result.add(parse(element));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("composite condition needs a non-empty 'conditions' array");
        }
        return result;
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
    }

    static {
        register("in_water", json -> ServerPlayer::isInWater);
        register("not_in_water", json -> player -> !player.isInWater());
        register("on_ground", json -> ServerPlayer::onGround);
        register("in_air", json -> player -> !player.onGround());
        register("is_day", json -> player -> Math.floorMod(player.level().getOverworldClockTime(), 24000L) < 12000);
        register("is_night", json -> player -> Math.floorMod(player.level().getOverworldClockTime(), 24000L) >= 12000);
        register("is_sprinting", json -> ServerPlayer::isSprinting);
        register("is_crouching", json -> ServerPlayer::isCrouching);
        register("is_on_fire", json -> ServerPlayer::isOnFire);
        register("health_below", json -> healthCondition(json, true));
        register("health_above", json -> healthCondition(json, false));
        register("has_effect", Conditions::hasEffectCondition);
        register("light_level_below", json -> lightCondition(json, true));
        register("light_level_above", json -> lightCondition(json, false));
        register("in_biome", Conditions::biomeCondition);
        register("in_dimension", Conditions::dimensionCondition);
        register("not", Conditions::notCondition);
        register("all_of", json -> {
            List<Condition> parts = subConditions(json);
            return player -> {
                for (Condition condition : parts) {
                    if (!condition.test(player)) {
                        return false;
                    }
                }
                return true;
            };
        });
        register("any_of", json -> {
            List<Condition> parts = subConditions(json);
            return player -> {
                for (Condition condition : parts) {
                    if (condition.test(player)) {
                        return true;
                    }
                }
                return false;
            };
        });
        register("is_swimming", json -> ServerPlayer::isSwimming);
        register("is_raining", json -> player -> player.level().isRainingAt(player.blockPosition()));
        register("below_y", json -> {
            int threshold = getInt(json, "threshold", 0);
            return player -> player.getY() < threshold;
        });
        register("above_y", json -> {
            int threshold = getInt(json, "threshold", 128);
            return player -> player.getY() > threshold;
        });
        register("is_falling", json -> player -> player.getDeltaMovement().y < -0.1 && !player.onGround());
        register("has_armor", json -> player -> {
            for (net.minecraft.world.entity.EquipmentSlot s : new net.minecraft.world.entity.EquipmentSlot[]{
                    net.minecraft.world.entity.EquipmentSlot.HEAD,
                    net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS,
                    net.minecraft.world.entity.EquipmentSlot.FEET}) {
                if (!player.getItemBySlot(s).isEmpty()) return true;
            }
            return false;
        });
        register("is_full_health", json -> player -> player.getHealth() >= player.getMaxHealth());
        register("held_item", Conditions::heldItemCondition);
        register("offhand_item", Conditions::offhandItemCondition);
        register("wearing_item", Conditions::wearingItemCondition);
        register("armor_count_above", json -> armorCountCondition(json, true));
        register("armor_count_below", json -> armorCountCondition(json, false));
        register("hunger_below", json -> hungerCondition(json, true));
        register("hunger_above", json -> hungerCondition(json, false));
        register("air_below", json -> airCondition(json, true));
        register("air_above", json -> airCondition(json, false));
        register("xp_level_above", json -> xpLevelCondition(json, true));
        register("xp_level_below", json -> xpLevelCondition(json, false));
        register("entities_nearby_above", json -> entitiesNearbyCondition(json, true));
        register("entities_nearby_below", json -> entitiesNearbyCondition(json, false));
        register("standing_on", Conditions::standingOnCondition);
        register("biome_id", Conditions::biomeIdCondition);
        register("moon_phase", Conditions::moonPhaseCondition);
        register("thundering", json -> player -> player.level().isThundering());
        register("eye_in_water", json -> player -> player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER));
        register("climbing", json -> net.minecraft.world.entity.LivingEntity::onClimbable);
        register("time_between", Conditions::timeBetweenCondition);
        register("scoreboard_above", json -> scoreboardCondition(json, true));
        register("scoreboard_below", json -> scoreboardCondition(json, false));
        register("gamemode", Conditions::gamemodeCondition);
        register("riding", Conditions::ridingCondition);
    }
}
