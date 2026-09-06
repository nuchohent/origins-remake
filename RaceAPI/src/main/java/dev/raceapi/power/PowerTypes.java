package dev.raceapi.power;

import com.google.gson.JsonObject;
import dev.raceapi.race.Power;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of power types. Each power type name (the {@code type} field in a
 * power definition JSON) maps to a {@link PowerParser} that builds the power.
 * <p>
 * Built-in types are registered here; third-party mods can add their own power
 * types with {@link #register(String, PowerParser)}. When a power type is
 * registered as <em>dynamic display</em>, its name/description come from the
 * power itself (e.g. attribute/effect/conditional powers build their own text)
 * and it is never wrapped with a default translated label.
 */
public final class PowerTypes {

    private static final Map<String, PowerParser> PARSERS = new LinkedHashMap<>();
    private static final Map<String, Boolean> DYNAMIC = new LinkedHashMap<>();

    private PowerTypes() {
    }

    /** Registers a power type. Throws on a duplicate type name. */
    public static void register(String type, PowerParser parser) {
        register(type, parser, false);
    }

    /** Registers a power type with an explicit dynamic-display flag. */
    public static void register(String type, PowerParser parser, boolean dynamicDisplay) {
        if (PARSERS.putIfAbsent(type, parser) != null) {
            throw new IllegalArgumentException("Duplicate power type: " + type);
        }
        DYNAMIC.put(type, dynamicDisplay);
    }

    /** Returns the parser for a type name, or {@code null} if not registered. */
    public static PowerParser get(String type) {
        return PARSERS.get(type);
    }

    /** Whether a power type name is registered. */
    public static boolean contains(String type) {
        return PARSERS.containsKey(type);
    }

    /** Whether a type builds its own display text instead of using the default label. */
    public static boolean isDynamicDisplay(String type) {
        return DYNAMIC.getOrDefault(type, false);
    }

    /** All registered power type names, in registration order. */
    public static Collection<String> getTypeNames() {
        return PARSERS.keySet();
    }

    static {
        register("attribute", PowerTypes::attribute, true);
        register("status_effect", PowerTypes::statusEffect, true);
        register("dash", (id, json, difficulty) -> new DashPower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 3), PowerFactory.getDouble(json, "strength", 1.8)));
        register("blink", (id, json, difficulty) -> new BlinkPower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 4), PowerFactory.getDouble(json, "range", 10.0)));
        register("double_jump", (id, json, difficulty) -> new DoubleJumpPower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 2), PowerFactory.getDouble(json, "boost", 0.7)));
        register("fire_aura", (id, json, difficulty) -> new FireAuraPower(id, difficulty,
                getDistance(json, "radius", 3.0), PowerFactory.getSeconds(json, "fire_ticks", 3)));
        register("step_height", (id, json, difficulty) -> new StepHeightPower(id, difficulty,
                PowerFactory.getDouble(json, "amount", 1.0)));
        register("lifesteal", (id, json, difficulty) -> new LifestealPower(id, difficulty,
                PowerFactory.getDouble(json, "fraction", 0.25)));
        register("safe_landing", (id, json, difficulty) -> new SafeLandingPower(id, difficulty));
        register("creative_flight", (id, json, difficulty) -> new CreativeFlightPower(id, difficulty));
        register("toughness", (id, json, difficulty) -> new ToughnessPower(id, difficulty,
                PowerFactory.getDouble(json, "reduction", 0.25)));
        register("night_boost", (id, json, difficulty) ->
                new TimeEffectPower(id, difficulty, MobEffects.STRENGTH, 0, true));
        register("day_boost", (id, json, difficulty) ->
                new TimeEffectPower(id, difficulty, MobEffects.SPEED, 0, false));
        register("light_sensitive", (id, json, difficulty) -> new LightSensitivePower(id, difficulty));
        register("ravenous", (id, json, difficulty) -> new RavenousPower(id, difficulty,
                json.has("amplifier")
                        ? PowerFactory.getDouble(json, "amplifier", 1) * 0.3
                        : PowerFactory.getDouble(json, "exhaustion", 0.5)));
        register("wall_jump", (id, json, difficulty) -> new WallJumpPower(id, difficulty));
        register("spider_climb", (id, json, difficulty) -> new SpiderClimbPower(id, difficulty));
        register("sprint_jump", (id, json, difficulty) -> new SprintJumpPower(id, difficulty));
        register("bouncy", (id, json, difficulty) -> new BouncyPower(id, difficulty));
        register("frost_touch", (id, json, difficulty) -> new FrostTouchPower(id, difficulty));
        register("venom_touch", (id, json, difficulty) -> new VenomTouchPower(id, difficulty));
        register("thorns", (id, json, difficulty) -> new ThornsPower(id, difficulty,
                PowerFactory.getDouble(json, "fraction", 0.25)));
        register("heavy_hitter", (id, json, difficulty) -> new HeavyHitterPower(id, difficulty));
        register("magnet", (id, json, difficulty) -> new MagnetPower(id, difficulty,
                getDistance(json, "radius", 4.0)));
        register("purified", (id, json, difficulty) -> new PurifiedPower(id, difficulty));
        register("detector", (id, json, difficulty) -> new DetectorPower(id, difficulty,
                getDistance(json, "radius", 6.0)));
        register("aqua_haste", (id, json, difficulty) -> new AquaHastePower(id, difficulty));
        register("frost_aura", (id, json, difficulty) -> new FrostAuraPower(id, difficulty,
                getDistance(json, "radius", 3.0),
                PowerFactory.getSeconds(json, "slowness_duration", 3),
                PowerFactory.getInt(json, "slowness_amplifier", 0)));
        register("hyper_inertia", (id, json, difficulty) -> new HyperInertiaPower(id, difficulty,
                PowerFactory.getDouble(json, "turn_threshold", 0.7), PowerFactory.getDouble(json, "accel_factor", 0.15)));
        register("density_anchor", (id, json, difficulty) -> new DensityAnchorPower(id, difficulty,
                PowerFactory.getDouble(json, "fall_multiplier", 2.5), PowerFactory.getDouble(json, "sink_speed", -0.5)));
        register("airborne_fragility", (id, json, difficulty) -> new AirborneFragilityPower(id, difficulty,
                PowerFactory.getDouble(json, "damage_multiplier", 2.0), PowerFactory.getSeconds(json, "jump_disable_ticks", 2)));
        register("directional_exposure", (id, json, difficulty) -> new DirectionalExposurePower(id, difficulty,
                (float) PowerFactory.getDouble(json, "multiplier", 1.75),
                Math.cos(Math.toRadians(PowerFactory.getDouble(json, "rear_angle", 60)))));
        register("metal_intolerance", (id, json, difficulty) -> new MetalIntolerancePower(id, difficulty,
                PowerFactory.getDouble(json, "penalty_per_item", -0.10)));
        register("inverse_regeneration", (id, json, difficulty) -> new InverseRegenerationPower(id, difficulty,
                (float) PowerFactory.getDouble(json, "damage_per_tick", 0.5),
                PowerFactory.getSeconds(json, "damage_tick_interval", 4),
                PowerFactory.getInt(json, "food_threshold", 18),
                (float) PowerFactory.getDouble(json, "regen_punishment", 1.5)));
        register("thermal_shock", (id, json, difficulty) -> new ThermalShockPower(id, difficulty,
                PowerFactory.getSeconds(json, "shock_cooldown", 5),
                PowerFactory.getSeconds(json, "ability_disable_ticks", 5),
                (float) PowerFactory.getDouble(json, "shock_damage", 2.0)));
        register("life_tether", (id, json, difficulty) -> new LifeTetherPower(id, difficulty,
                getDistance(json, "range", 15.0),
                (float) PowerFactory.getDouble(json, "health_loss_fraction", 0.30),
                PowerFactory.getSeconds(json, "slowness_duration", 3),
                PowerFactory.getInt(json, "slowness_amplifier", 1)));
        register("conditional", PowerTypes::conditional, true);
        register("effect_removal", PowerTypes::effectRemoval);
        register("action_restriction", PowerTypes::actionRestriction);
        register("kinetic_slam", (id, json, difficulty) -> new KineticSlamPower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 8),
                getDistance(json, "radius", 4.0),
                (float) PowerFactory.getDouble(json, "damage", 14.0),
                (float) PowerFactory.getDouble(json, "self_damage_fraction", 0.25),
                PowerFactory.getDouble(json, "knockback_strength", 1.5)));
        register("time_trace", (id, json, difficulty) -> new TimeTracePower(id, difficulty,
                PowerFactory.getSeconds(json, "auto_return_ticks", 3),
                PowerFactory.getSeconds(json, "cooldown", 30)));
        register("phase_dash", (id, json, difficulty) -> new PhaseDashPower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 3),
                getDistance(json, "distance", 6.0),
                PowerFactory.getDouble(json, "dash_velocity", 2.5),
                (float) PowerFactory.getDouble(json, "contact_damage", 6.0)));
        register("kinetic_counter", (id, json, difficulty) -> new KineticCounterPower(id, difficulty,
                PowerFactory.getSeconds(json, "parry_window", 0.8),
                PowerFactory.getSeconds(json, "stun_duration", 1),
                PowerFactory.getSeconds(json, "base_cooldown", 2),
                PowerFactory.getSeconds(json, "miss_cooldown", 3),
                PowerFactory.getDouble(json, "pushback_strength", 2.0)));
        register("magnetic_hook", (id, json, difficulty) -> new MagneticHookPower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 2),
                getDistance(json, "range", 16.0),
                PowerFactory.getDouble(json, "pull_speed", 1.2),
                (float) PowerFactory.getDouble(json, "pull_damage", 2.0)));
        register("life_link", (id, json, difficulty) -> new LifeLinkPower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 4),
                PowerFactory.getSeconds(json, "duration", 5),
                (float) PowerFactory.getDouble(json, "redirect_fraction", 0.4),
                getDistanceInt(json, "pick_range", 10)));
        register("gravity_pulse", (id, json, difficulty) -> new GravitationalPulsePower(id, difficulty,
                getDistance(json, "radius", 8.0),
                PowerFactory.getDouble(json, "force", 2.0),
                PowerFactory.getSeconds(json, "cooldown", 4),
                PowerFactory.getSeconds(json, "max_charge_ticks", 2),
                (float) PowerFactory.getDouble(json, "damage", 2.0)));
        register("overdrive", (id, json, difficulty) -> new OverdrivePower(id, difficulty,
                (float) PowerFactory.getDouble(json, "self_damage", 3.0),
                getDistance(json, "aura_radius", 1.5),
                (float) PowerFactory.getDouble(json, "aura_damage", 2.0),
                PowerFactory.getSeconds(json, "aura_fire_ticks", 0.1)));
        register("disarm_wave", (id, json, difficulty) -> new DisarmWavePower(id, difficulty,
                getDistance(json, "range", 8.0),
                PowerFactory.getSeconds(json, "cooldown", 25),
                (float) PowerFactory.getDouble(json, "hit_damage", 5.0),
                PowerFactory.getDouble(json, "knockup_strength", 1.2)));
        register("teleport_strike", (id, json, difficulty) -> new TeleportStrikePower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 8),
                getDistance(json, "range", 16.0),
                (float) PowerFactory.getDouble(json, "damage", 6.0)));
        register("earthquake", (id, json, difficulty) -> new EarthquakePower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 10),
                getDistance(json, "radius", 5.0),
                (float) PowerFactory.getDouble(json, "damage", 8.0),
                (float) PowerFactory.getDouble(json, "self_damage", 2.0),
                PowerFactory.getDouble(json, "knockup_strength", 0.8),
                PowerFactory.getInt(json, "slowness_duration", 2),
                PowerFactory.getInt(json, "slowness_amplifier", 0)));
        register("shadow_step", (id, json, difficulty) -> new ShadowStepPower(id, difficulty,
                PowerFactory.getSeconds(json, "cooldown", 6),
                getDistance(json, "range", 12.0),
                (float) PowerFactory.getDouble(json, "damage", 4.0),
                PowerFactory.getSeconds(json, "darkness_duration", 0),
                PowerFactory.getSeconds(json, "weakness_duration", 3)));
        register("size_control", (id, json, difficulty) -> new SizeControlPower(id, difficulty,
                PowerFactory.getDouble(json, "scale", 1.0),
                PowerFactory.getBool(json, "permanent", true),
                PowerFactory.getSeconds(json, "duration", 10),
                PowerFactory.getSeconds(json, "cooldown", 2)));
        register("reach", (id, json, difficulty) -> new ReachPower(id, difficulty,
                PowerFactory.getDouble(json, "reach_multiplier", 1.0)));
        register("damage_immunity", (id, json, difficulty) -> new DamageImmunityPower(id, difficulty,
                PowerFactory.getString(json, "damage_type", "fire")));
        register("grant_item", (id, json, difficulty) -> {
            String itemId = PowerFactory.getString(json, "item", "minecraft:stone");
            int count = Math.max(1, Math.min(64, PowerFactory.getInt(json, "count", 1)));
            net.minecraft.resources.Identifier itemIdentifier = net.minecraft.resources.Identifier.tryParse(itemId);
            if (itemIdentifier == null) {
                PowerFactory.LOGGER.error("Power {}: invalid item id '{}'", id, itemId);
                return null;
            }
            return new GrantItemPower(id, difficulty, itemIdentifier, count);
        });
        register("resource", (id, json, difficulty) -> new ResourcePower(id, difficulty,
                PowerFactory.getDouble(json, "max", 100.0), PowerFactory.getDouble(json, "regen", 1.0),
                PowerFactory.getString(json, "resource_kind", "mana"),
                PowerFactory.getBool(json, "consume_sprint", true),
                PowerFactory.getDouble(json, "sprint_cost", 5.0),
                PowerFactory.getBool(json, "consume_attack", true),
                PowerFactory.getDouble(json, "attack_cost", 10.0)), true);
        register("on_kill", (id, json, difficulty) -> new KillRewardPower(id, difficulty,
                PowerFactory.getDouble(json, "heal", 4.0),
                PowerFactory.getDouble(json, "resource_gain", 20.0),
                PowerFactory.getInt(json, "strength_seconds", 5)));
        register("on_eat", (id, json, difficulty) -> new EatRewardPower(id, difficulty,
                PowerFactory.getDouble(json, "heal", 2.0),
                PowerFactory.getDouble(json, "resource_gain", 10.0)));
        register("summon", (id, json, difficulty) -> {
            String entityStr = PowerFactory.getString(json, "entity", "minecraft:wolf");
            net.minecraft.resources.Identifier entityIdentifier = net.minecraft.resources.Identifier.tryParse(entityStr);
            if (entityIdentifier == null) {
                PowerFactory.LOGGER.error("Power {}: invalid entity id '{}'", id, entityStr);
                return null;
            }
            return new SummonPower(id, difficulty,
                    PowerFactory.getSeconds(json, "cooldown", 10),
                    entityIdentifier,
                    PowerFactory.getInt(json, "count", 1));
        });
        register("on_interact", (id, json, difficulty) -> {
            String triggerMode = PowerFactory.getString(json, "trigger_mode", "any");
            Identifier triggerItem = null;
            String itemStr = PowerFactory.getString(json, "trigger_item", "");
            if (!itemStr.isEmpty()) {
                triggerItem = Identifier.tryParse(itemStr);
                if (triggerItem == null) {
                    PowerFactory.LOGGER.error("Power {}: invalid trigger_item id '{}'", id, itemStr);
                }
            }
            Identifier triggerEntity = null;
            String entityStr = PowerFactory.getString(json, "trigger_entity", "");
            if (!entityStr.isEmpty()) {
                triggerEntity = Identifier.tryParse(entityStr);
                if (triggerEntity == null) {
                    PowerFactory.LOGGER.error("Power {}: invalid trigger_entity id '{}'", id, entityStr);
                }
            }
            Identifier triggerBlock = null;
            String blockStr = PowerFactory.getString(json, "trigger_block", "");
            if (!blockStr.isEmpty()) {
                triggerBlock = Identifier.tryParse(blockStr);
                if (triggerBlock == null) {
                    PowerFactory.LOGGER.error("Power {}: invalid trigger_block id '{}'", id, blockStr);
                }
            }

            String action = PowerFactory.getString(json, "action", "effect");
            switch (action) {
                case "effect", "heal", "damage", "damage_target", "heal_target", "summon", "teleport" -> {
                }
                default -> {
                    dev.raceapi.data.ParseErrors.error("Power " + id + ": unknown action '" + action + "'");
                    return null;
                }
            }
            Identifier effectId = null;
            if (action.equals("effect")) {
                String effectStr = PowerFactory.getString(json, "effect", "minecraft:speed");
                effectId = Identifier.tryParse(effectStr);
                if (effectId == null || net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(effectId).isEmpty()) {
                    dev.raceapi.data.ParseErrors.error("Power " + id + ": unknown effect '" + effectStr + "' for action 'effect'");
                    return null;
                }
            }
            Identifier summonId = null;
            if (action.equals("summon")) {
                String summonStr = PowerFactory.getString(json, "summon_entity", "minecraft:wolf");
                summonId = Identifier.tryParse(summonStr);
                if (summonId == null || net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(summonId).isEmpty()) {
                    dev.raceapi.data.ParseErrors.error("Power " + id + ": unknown summon_entity '" + summonStr + "' for action 'summon'");
                    return null;
                }
            }

            return new OnInteractPower(id, difficulty,
                    triggerMode, triggerItem, triggerEntity, triggerBlock,
                    action, effectId,
                    PowerFactory.getSeconds(json, "effect_duration", 5),
                    PowerFactory.getInt(json, "effect_amplifier", 1) - 1,
                    (float) PowerFactory.getDouble(json, "heal_amount", 2.0),
                    (float) PowerFactory.getDouble(json, "damage_amount", 2.0),
                    summonId,
                    PowerFactory.getInt(json, "summon_count", 1),
                    PowerFactory.getSeconds(json, "cooldown", 0));
        });
    }

    private static Power attribute(Identifier id, JsonObject json, int difficulty) {
        Holder<Attribute> attribute = PowerFactory.parseAttribute(PowerFactory.getString(json, "attribute", ""));
        if (attribute == null) {
            PowerFactory.LOGGER.error("Power {}: unknown attribute '{}'", id, PowerFactory.getString(json, "attribute", ""));
            return null;
        }
        AttributeModifier.Operation operation = PowerFactory.parseOperation(PowerFactory.getString(json, "operation", "add_value"));
        return new AttributePower(id, attribute, PowerFactory.getDouble(json, "amount", 1.0), operation,
                PowerFactory.getBool(json, "hidden", false), difficulty);
    }

    private static Power statusEffect(Identifier id, JsonObject json, int difficulty) {
        Holder<MobEffect> effect = PowerFactory.parseEffect(PowerFactory.getString(json, "effect", ""));
        if (effect == null) {
            PowerFactory.LOGGER.error("Power {}: unknown effect '{}'", id, PowerFactory.getString(json, "effect", ""));
            return null;
        }
        // JSON durations are expressed in SECONDS; the power engine works in ticks.
        int durationTicks = PowerFactory.getSeconds(json, "duration", 20);
        int intervalTicks = PowerFactory.getSeconds(json, "interval", 15);
        // The JSON "amplifier" is a user-facing LEVEL (1..n); the engine expects
        // the raw amplifier (level - 1) so level 4 produces an effect level 4.
        int amplifier = Math.max(0, PowerFactory.getInt(json, "amplifier", 1) - 1);
        return new StatusEffectPower(id, effect, amplifier,
                durationTicks, intervalTicks,
                PowerFactory.getBool(json, "hidden", false), difficulty, PowerFactory.getBool(json, "infinite", true));
    }

    private static Power conditional(Identifier id, JsonObject json, int difficulty) {
        JsonObject innerJson = json.has("power") && json.get("power").isJsonObject()
                ? json.getAsJsonObject("power") : new JsonObject();
        Power innerPower = PowerFactory.create(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_inner"), innerJson);
        if (innerPower == null) {
            PowerFactory.LOGGER.error("Conditional power {}: failed to parse inner power", id);
            return null;
        }
        Power negatePower = null;
        if (json.has("negate_power") && json.get("negate_power").isJsonObject()) {
            negatePower = PowerFactory.create(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_negate"),
                    json.getAsJsonObject("negate_power"));
        }
        String conditionLabel = PowerFactory.getString(json, "condition_label", "");
        net.minecraft.network.chat.Component label = conditionLabel.isEmpty()
                ? dev.raceapi.power.condition.Conditions.describe(json)
                : net.minecraft.network.chat.Component.literal(conditionLabel);
        return new ConditionalPower(id, difficulty,
                dev.raceapi.power.condition.Conditions.parse(json), innerPower, negatePower,
                label);
    }

    private static Power effectRemoval(Identifier id, JsonObject json, int difficulty) {
        Holder<MobEffect> effect = PowerFactory.parseEffect(PowerFactory.getString(json, "effect", ""));
        if (effect == null) {
            PowerFactory.LOGGER.error("Power {}: unknown effect '{}'", id, PowerFactory.getString(json, "effect", ""));
            return null;
        }
        return new EffectRemovalPower(id, difficulty, effect);
    }

    private static Power actionRestriction(Identifier id, JsonObject json, int difficulty) {
        return new ActionRestrictionPower(id, difficulty,
                PowerFactory.getBool(json, "restrict_sprint", false),
                PowerFactory.getBool(json, "restrict_jump", false),
                PowerFactory.getBool(json, "restrict_swim", false),
                PowerFactory.getBool(json, "restrict_flight", false),
                PowerFactory.getBool(json, "restrict_attack", false));
    }

    private static double getDistance(JsonObject json, String key, double fallback) {
        return Math.min(64.0, PowerFactory.getDouble(json, key, fallback));
    }

    private static int getDistanceInt(JsonObject json, String key, int fallback) {
        return Math.min(64, PowerFactory.getInt(json, key, fallback));
    }
}
