package dev.raceapi;

import dev.raceapi.commands.RaceCommands;
import dev.raceapi.data.PowerDataLoader;
import dev.raceapi.data.RaceDataLoader;
import dev.raceapi.network.RaceNetwork;
import dev.raceapi.player.RaceManager;
import dev.raceapi.power.EffectRemovalPower;
import dev.raceapi.power.AirborneFragilityPower;
import dev.raceapi.power.ActionRestrictionPower;
import dev.raceapi.power.AttributePower;
import dev.raceapi.power.AquaHastePower;
import dev.raceapi.power.BlinkPower;
import dev.raceapi.power.BouncyPower;
import dev.raceapi.power.DisarmWavePower;
import dev.raceapi.power.DashPower;
import dev.raceapi.power.DensityAnchorPower;
import dev.raceapi.power.DetectorPower;
import dev.raceapi.power.DirectionalExposurePower;
import dev.raceapi.power.DoubleJumpPower;
import dev.raceapi.power.FireAuraPower;
import dev.raceapi.power.FrostAuraPower;
import dev.raceapi.power.FrostTouchPower;
import dev.raceapi.power.GravitationalPulsePower;
import dev.raceapi.power.HeavyHitterPower;
import dev.raceapi.power.HyperInertiaPower;
import dev.raceapi.power.InverseRegenerationPower;
import dev.raceapi.power.KineticCounterPower;
import dev.raceapi.power.KineticSlamPower;
import dev.raceapi.power.LifeLinkPower;
import dev.raceapi.power.LifeTetherPower;
import dev.raceapi.power.LifestealPower;
import dev.raceapi.power.LightSensitivePower;
import dev.raceapi.power.MagnetPower;
import dev.raceapi.power.MagneticHookPower;
import dev.raceapi.power.MetalIntolerancePower;
import dev.raceapi.power.NamedPower;
import dev.raceapi.power.OverdrivePower;
import dev.raceapi.power.PhaseDashPower;
import dev.raceapi.power.PurifiedPower;
import dev.raceapi.power.RavenousPower;
import dev.raceapi.power.ResourcePower;
import dev.raceapi.power.SafeLandingPower;
import dev.raceapi.power.SpiderClimbPower;
import dev.raceapi.power.SprintJumpPower;
import dev.raceapi.power.StatusEffectPower;
import dev.raceapi.power.StepHeightPower;
import dev.raceapi.power.ThermalShockPower;
import dev.raceapi.power.ThornsPower;
import dev.raceapi.power.TimeEffectPower;
import dev.raceapi.power.TimeTracePower;
import dev.raceapi.power.ToughnessPower;
import dev.raceapi.power.VenomTouchPower;
import dev.raceapi.power.WallJumpPower;
import dev.raceapi.race.Power;
import dev.raceapi.race.PowerRegistry;
import dev.raceapi.race.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Mod(RaceAPI.MOD_ID)
public final class RaceAPI {
    public static final String MOD_ID = "raceapi";
    public static final String NAME = "Race API";

    private static final Logger LOGGER = LoggerFactory.getLogger(RaceAPI.class);

    public RaceAPI(IEventBus modBus) {
        modBus.addListener(RaceNetwork::register);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onLivingJump);
        NeoForge.EVENT_BUS.addListener(this::onLivingFall);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(this::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(this::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onRightClickEntity);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onUseItemFinish);

        registerBuiltinPowers();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        RaceCommands.register(event.getDispatcher());
    }

    private void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("raceapi", "powers"), new PowerDataLoader());
        event.addListener(Identifier.fromNamespaceAndPath("raceapi", "races"), new RaceDataLoader());
        // runs last: re-attach races so players get fresh power instances,
        // not stale ones captured before the reload
        event.addListener(Identifier.fromNamespaceAndPath("raceapi", "reapply"),
                new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
                    @Override
                    protected Void prepare(net.minecraft.server.packs.resources.ResourceManager rm,
                                           net.minecraft.util.profiling.ProfilerFiller profiler) {
                        return null;
                    }

                    @Override
                    protected void apply(Void v, net.minecraft.server.packs.resources.ResourceManager rm,
                                         net.minecraft.util.profiling.ProfilerFiller profiler) {
                        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                        if (server != null) {
                            // registry sync must reach every player before their
                            // selected-race payload (same ordering as login)
                            var players = server.getPlayerList().getPlayers();
                            for (ServerPlayer player : players) {
                                dev.raceapi.network.SyncRacesPayload.sendTo(player);
                            }
                            for (ServerPlayer player : players) {
                                RaceManager.reapplyAfterReload(player);
                            }
                        }
                    }
                });
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RaceManager.applyPersistedRace(player);
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RaceManager.clearTransientPowers(player);
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RaceManager.applyPersistedRace(player);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isDeadOrDying()) {
                continue;
            }
            var race = RaceManager.getRace(player);
            if (race == null) {
                continue;
            }
            for (Power power : dev.raceapi.api.PowerPipeline.effective(player, race)) {
                try {
                    power.onTick(player);
                } catch (Exception e) {
                    LOGGER.error("Power {} failed for player {}", power.getId(), player.getName().getString(), e);
                }
            }
        }
    }

    private void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fireJump(player);
        }
    }

    /**
     * Fires the {@code onJump} hook of every active power. Called both from the
     * server-side jump event and from {@code JumpPayload} (the server never sees
     * a non-passenger player's jump, so the client reports it via packet).
     */
    public static void fireJump(ServerPlayer player) {
        forEachPower(player, power -> safe(() -> power.onJump(player)));
    }

    private void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            double distance = event.getDistance();
            for (Power power : activePowers(player)) {
                try {
                    distance = power.onFall(player, (float) distance);
                } catch (Exception e) {
                    LOGGER.error("Power {} failed during fall for player {}", power.getId(), player.getName().getString(), e);
                }
            }
            event.setDistance(distance);
        }
    }

    private void onBlockBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            BlockPos pos = event.getPos();
            BlockState state = event.getState();
            forEachPower(player, power -> safe(() -> power.onBreakBlock(player, pos, state)));
        }
    }

    private void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            float speed = event.getNewSpeed();
            for (Power power : activePowers(player)) {
                try {
                    speed = power.onBreakSpeed(player, speed);
                } catch (Exception e) {
                    LOGGER.error("Power {} failed during break-speed for player {}", power.getId(), player.getName().getString(), e);
                }
            }
            event.setNewSpeed(speed);
        }
    }

    private void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var source = event.getSource();
            // kinetic counter: a timed parry cancels the hit and counter-pushes
            for (Power power : activePowers(player)) {
                if (power.getWrapped() instanceof KineticCounterPower counter
                        && counter.isParrying(player)) {
                    boolean parried = counter.tryParry(player, source.getEntity());
                    if (parried) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
            float amount = event.getAmount();
            for (Power power : activePowers(player)) {
                try {
                    amount = power.onHurt(player, source, amount);
                } catch (Exception e) {
                    LOGGER.error("Power {} failed during damage for player {}", power.getId(), player.getName().getString(), e);
                }
            }
            if (amount <= 0) {
                // full immunity must CANCEL the hurt: setting amount 0 would
                // still trigger invulnerability frames, hurt animation and knockback
                event.setCanceled(true);
                return;
            }
            event.setAmount(amount);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            // "Restrict attack" must cancel the damage BEFORE it is dealt;
            // onAttack() runs post-damage and can only apply side effects.
            if (hasAttackRestriction(attacker)) {
                event.setCanceled(true);
                return;
            }
            // stamina pools charge for every melee attack; no stamina = no damage
            if (!tryStaminaAttackCost(attacker)) {
                event.setCanceled(true);
                return;
            }
            if (event.getSource().is(DamageTypeTags.IS_PLAYER_ATTACK)) {
                float amount = event.getAmount();
                for (Power power : activePowers(attacker)) {
                    try {
                        power.onAttack(attacker, event.getEntity(), amount);
                    } catch (Exception e) {
                        LOGGER.error("Power {} failed while dealing damage for player {}", power.getId(), attacker.getName().getString(), e);
                    }
                }
            }
        }
    }

    private static boolean hasAttackRestriction(ServerPlayer attacker) {
        for (Power power : activePowers(attacker)) {
            if (power.getWrapped() instanceof ActionRestrictionPower restriction && restriction.isAttackRestricted()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Charges the stamina cost of a melee attack. Returns false when the
     * attacker has a stamina pool with {@code consume_attack} but not enough
     * stamina left - the caller cancels the attack.
     */
    private static boolean tryStaminaAttackCost(ServerPlayer attacker) {
        for (Power power : activePowers(attacker)) {
            if (power.getWrapped() instanceof ResourcePower resource
                    && resource.isStamina() && resource.consumesAttack()) {
                double cost = resource.getAttackCost();
                if (cost > 0 && !dev.raceapi.player.Resources.tryConsume(attacker, cost)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var hitVec = event.getHitVec();
            forEachPower(player, power -> safe(() ->
                    power.onInteractBlock(player, event.getHand(), hitVec.getBlockPos(), hitVec)));
        }
    }

    private void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var target = event.getTarget();
            forEachPower(player, power -> safe(() ->
                    power.onInteractEntity(player, event.getHand(), target)));
        }
    }

    /** Fires {@link Power#onKill} for the killing player's powers. */
    private void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            var victim = event.getEntity();
            forEachPower(player, power -> safe(() -> power.onKill(player, victim)));
        }
        // life tether: every ONLINE player tethered to the victim loses health
        if (event.getEntity() instanceof ServerPlayer victim) {
            for (ServerPlayer tether : victim.level().getServer().getPlayerList().getPlayers()) {
                if (tether == victim) continue;
                for (Power power : activePowers(tether)) {
                    if (power.getWrapped() instanceof LifeTetherPower lifeTether) {
                        safe(() -> lifeTether.onNearbyPlayerDeath(victim, tether));
                    }
                }
            }
        }
    }

    /** Fires {@link Power#onEat} when a player finishes eating food. */
    private void onUseItemFinish(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
            forEachPower(player, power -> safe(() -> power.onEat(player)));
        }
    }

    private static void forEachPower(ServerPlayer player, java.util.function.Consumer<Power> action) {
        for (Power power : activePowers(player)) {
            action.accept(power);
        }
    }

    private static java.util.List<Power> activePowers(ServerPlayer player) {
        var race = RaceManager.getRace(player);
        return race == null ? java.util.List.of() : dev.raceapi.api.PowerPipeline.effective(player, race);
    }

    private static void safe(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.error("Power hook failed", e);
        }
    }

    private void registerBuiltinPowers() {
        // Permanent potion effects
        register("water_breathing",
                () -> new StatusEffectPower(id("water_breathing"), MobEffects.WATER_BREATHING, 1, 400, 300, false, 1, true));
        register("night_vision",
                () -> new StatusEffectPower(id("night_vision"), MobEffects.NIGHT_VISION, 1, 400, 300, false, 1, true));
        register("speed",
                () -> new StatusEffectPower(id("speed"), MobEffects.SPEED, 1, 400, 300, false, 2, true));
        register("jump_boost",
                () -> new StatusEffectPower(id("jump_boost"), MobEffects.JUMP_BOOST, 1, 400, 300, false, 1, true));
        register("haste",
                () -> new StatusEffectPower(id("haste"), MobEffects.HASTE, 1, 400, 300, false, 2, true));
        register("regeneration",
                () -> new StatusEffectPower(id("regeneration"), MobEffects.REGENERATION, 1, 400, 300, false, 2, true));
        register("strength",
                () -> new StatusEffectPower(id("strength"), MobEffects.STRENGTH, 1, 400, 300, false, 2, true));
        register("resistance",
                () -> new StatusEffectPower(id("resistance"), MobEffects.RESISTANCE, 1, 400, 300, false, 2, true));
        register("fire_resistance",
                () -> new StatusEffectPower(id("fire_resistance"), MobEffects.FIRE_RESISTANCE, 1, 400, 300, false, 2, true));
        register("slowness",
                () -> new StatusEffectPower(id("slowness"), MobEffects.SLOWNESS, 1, 400, 300, false, -1, true));
        register("mining_fatigue",
                () -> new StatusEffectPower(id("mining_fatigue"), MobEffects.MINING_FATIGUE, 1, 400, 300, false, -2, true));

        // Attribute modifiers
        register("max_health_plus",
                () -> new AttributePower(id("max_health_plus"), Attributes.MAX_HEALTH, 4.0,
                        AttributeModifier.Operation.ADD_VALUE, false, 2));
        register("max_health_minus",
                () -> new AttributePower(id("max_health_minus"), Attributes.MAX_HEALTH, -4.0,
                        AttributeModifier.Operation.ADD_VALUE, false, -2));
        register("slow",
                () -> new AttributePower(id("slow"), Attributes.MOVEMENT_SPEED, -0.1,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false, -2));
        register("frail",
                () -> new AttributePower(id("frail"), Attributes.MAX_HEALTH, -0.4,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true, -3));

        // Spectacular / active powers
        register("dash", () -> new DashPower(id("dash"), 1));
        register("blink", () -> new BlinkPower(id("blink"), 1));
        register("safe_landing", () -> new SafeLandingPower(id("safe_landing"), 2));
        register("toughness", () -> new ToughnessPower(id("toughness"), 2));

        // New mechanics
        register("fire_aura", () -> new FireAuraPower(id("fire_aura"), 2));
        register("step_height", () -> new StepHeightPower(id("step_height"), 1));
        register("lifesteal", () -> new LifestealPower(id("lifesteal"), 2));
        register("double_jump", () -> new DoubleJumpPower(id("double_jump"), 2));

        // Time of day (effect only while the sun is up or down)
        register("night_boost", () -> new TimeEffectPower(id("night_boost"), 2, MobEffects.STRENGTH, 0, true));
        register("day_boost", () -> new TimeEffectPower(id("day_boost"), 1, MobEffects.SPEED, 0, false));

        // Weaknesses
        register("light_sensitive", () -> new LightSensitivePower(id("light_sensitive"), -2));
        register("ravenous", () -> new RavenousPower(id("ravenous"), -1));

        // Movement
        register("wall_jump", () -> new WallJumpPower(id("wall_jump"), 1));
        register("spider_climb", () -> new SpiderClimbPower(id("spider_climb"), 2));
        register("sprint_jump", () -> new SprintJumpPower(id("sprint_jump"), 1));
        register("bouncy", () -> new BouncyPower(id("bouncy"), 1));

        // Combat
        register("frost_touch", () -> new FrostTouchPower(id("frost_touch"), 1));
        register("venom_touch", () -> new VenomTouchPower(id("venom_touch"), 1));
        register("thorns", () -> new ThornsPower(id("thorns"), 2));
        register("heavy_hitter", () -> new HeavyHitterPower(id("heavy_hitter"), 2));

        // Utility
        register("magnet", () -> new MagnetPower(id("magnet"), 1));
        register("purified", () -> new PurifiedPower(id("purified"), 1));
        register("detector", () -> new DetectorPower(id("detector"), 2));
        register("aqua_haste", () -> new AquaHastePower(id("aqua_haste"), 1));
        register("frost_aura", () -> new FrostAuraPower(id("frost_aura"), 1, 3.0, 60, 0));

        // Debuffs
        register("hyper_inertia", () -> new HyperInertiaPower(id("hyper_inertia"), -2, 0.7, 0.15));
        register("density_anchor", () -> new DensityAnchorPower(id("density_anchor"), -2, 2.5, -0.5));
        register("airborne_fragility", () -> new AirborneFragilityPower(id("airborne_fragility"), -2, 2.0, 30));
        register("directional_exposure", () -> new DirectionalExposurePower(id("directional_exposure"), -2, 1.75f, Math.cos(Math.toRadians(60))));
        register("metal_intolerance", () -> new MetalIntolerancePower(id("metal_intolerance"), -2, -0.10));
        register("inverse_regeneration", () -> new InverseRegenerationPower(id("inverse_regeneration"), -2, 0.5f, 80, 18, 1.5f));
        register("thermal_shock", () -> new ThermalShockPower(id("thermal_shock"), -2, 100, 100, 2.0f));
        register("life_tether", () -> new LifeTetherPower(id("life_tether"), -2, 15.0, 0.30f, 60, 1));
        register("effect_removal", () -> new EffectRemovalPower(id("effect_removal"), 1,
                net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .get(Identifier.fromNamespaceAndPath("minecraft", "poison"))
                        .orElse(null)));
        register("action_restriction", () -> new ActionRestrictionPower(id("action_restriction"), -2,
                true, false, false, false, false));

        // Active combat powers
        register("kinetic_slam", () -> new KineticSlamPower(id("kinetic_slam"), 3, 4.0, 14.0f, 0.25f, 1.5));
        register("time_trace", () -> new TimeTracePower(id("time_trace"), 2, 60));
        register("phase_dash", () -> new PhaseDashPower(id("phase_dash"), 3, 60, 6.0, 2.5, 6.0f));
        register("kinetic_counter", () -> new KineticCounterPower(id("kinetic_counter"), 3, 16, 20, 40, 60, 2.0));
        register("magnetic_hook", () -> new MagneticHookPower(id("magnetic_hook"), 2, 40, 16.0, 1.2, 2.0f));
        register("life_link", () -> new LifeLinkPower(id("life_link"), 2, 80, 100, 0.4f, 10));
        register("gravity_pulse", () -> new GravitationalPulsePower(id("gravity_pulse"), 3, 8.0, 2.0, 80, 40, 2.0f));
        register("overdrive", () -> new OverdrivePower(id("overdrive"), 3, 3.0f, 1.5, 2.0f, 2));
        register("disarm_wave", () -> new DisarmWavePower(id("disarm_wave"), 2, 8.0, 500, 5.0f, 1.2));
    }

    /**
     * Registers a builtin power wrapped in a {@link NamedPower} with translated
     * name/description keys, so every builtin always has a readable label.
     */
    private static void register(String path, Supplier<Power> factory) {
        PowerRegistry.register(id(path), () -> {
            Power power = factory.get();
            return new NamedPower(power,
                    Component.translatable("power.raceapi." + path + ".name"),
                    Component.translatable("power.raceapi." + path + ".desc"));
        });
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Convenience method: returns the player's current race, or null.
     * Intended for use by other mods that depend on RaceAPI.
     */
    @org.jetbrains.annotations.Nullable
    public static Race getRaceForPlayer(ServerPlayer player) {
        return RaceManager.getRace(player);
    }
}
