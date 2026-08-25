package dev.originsx.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonObject;
import dev.originsx.OriginsX;
import dev.originsx.client.gui.RaceCreatorScreen;
import dev.originsx.client.gui.RaceInfoHUD;
import dev.originsx.client.gui.RaceSelectionScreen;
import dev.raceapi.client.CooldownClient;
import dev.raceapi.client.SelectedRaceClient;
import dev.raceapi.network.JumpPayload;
import dev.raceapi.network.PowerKeyPayload;
import dev.raceapi.power.ActionRestrictionPower;
import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OriginsXClient {

    /** How many separate active-power keybinds exist (one per active-power slot). */
    public static final int MAX_ACTIVE_SLOTS = 9;

    private static final KeyMapping.Category ORIGINSX_CATEGORY =
            KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(OriginsX.MOD_ID, "originsx"));

    public static final KeyMapping OPEN_RACE_HUD = new KeyMapping(
            "key.originsx.open_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            ORIGINSX_CATEGORY);

    public static final KeyMapping HUD_SETTINGS = new KeyMapping(
            "key.originsx.hud_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_0 = new KeyMapping(
            "key.originsx.active_power",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_1 = new KeyMapping(
            "key.originsx.active_power.1",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_2 = new KeyMapping(
            "key.originsx.active_power.2",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_3 = new KeyMapping(
            "key.originsx.active_power.3",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_4 = new KeyMapping(
            "key.originsx.active_power.4",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_5 = new KeyMapping(
            "key.originsx.active_power.5",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_6 = new KeyMapping(
            "key.originsx.active_power.6",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_7 = new KeyMapping(
            "key.originsx.active_power.7",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            ORIGINSX_CATEGORY);

    public static final KeyMapping ACTIVE_POWER_8 = new KeyMapping(
            "key.originsx.active_power.8",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            ORIGINSX_CATEGORY);

    private static final KeyMapping[] ACTIVE_POWER_KEYS = {
            ACTIVE_POWER_0, ACTIVE_POWER_1, ACTIVE_POWER_2, ACTIVE_POWER_3,
            ACTIVE_POWER_4, ACTIVE_POWER_5, ACTIVE_POWER_6, ACTIVE_POWER_7,
            ACTIVE_POWER_8
    };

    private static final int AUTO_OPEN_RETRY_CAP = 300;

    private static int autoOpenRetries = 0;

    private static boolean autoOpenScheduled = false;

    private OriginsXClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ModEvents::registerKeyMappings);
    }

    public static void openSelectionScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.setScreenAndShow(RaceSelectionScreen.create());
    }

    public static void openCreatorScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.setScreenAndShow(RaceCreatorScreen.create());
    }

    public static void openCreatorScreen(String editId, JsonObject editData, Path editPackDir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.setScreenAndShow(RaceCreatorScreen.createForEdit(editId, editData, editPackDir));
    }

    /** Whether the local player has cheat/OP permissions (used to show the create-race button). */
    public static boolean isCheatsEnabled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        return mc.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    /**
     * Opens the race selection screen when the player joins a world/server
     * without a race. Waits for the server's race-sync payload so that
     * players who already picked a race are not bothered again on rejoin.
     */
    public static void tryAutoOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // the server-side login event can fire before the client player
            // entity exists — retry instead of giving up silently
            if (autoOpenRetries < AUTO_OPEN_RETRY_CAP) {
                autoOpenRetries++;
                mc.execute(OriginsXClient::tryAutoOpen);
            }
            return;
        }
        if (!SelectedRaceClient.isSynced()) {
            if (autoOpenRetries < AUTO_OPEN_RETRY_CAP) {
                autoOpenRetries++;
                mc.execute(OriginsXClient::tryAutoOpen);
            }
            return;
        }
        autoOpenRetries = 0;
        if (!SelectedRaceClient.hasRace()) {
            openSelectionScreen();
        }
    }

    /** The bound power of the current race at the given slot, or null. */
    public static Power boundPowerAt(Race race, int slot) {
        if (race == null) {
            return null;
        }
        List<Power> bound = new ArrayList<>();
        for (Power power : race.getPowers()) {
            if (power.hasBinding()) {
                bound.add(power);
            }
        }
        return slot >= 0 && slot < bound.size() ? bound.get(slot) : null;
    }

    /** Slot index of a power within the race's bound powers, or -1. */
    public static int boundSlot(Race race, Power power) {
        if (race == null || power == null) {
            return -1;
        }
        int index = 0;
        for (Power p : race.getPowers()) {
            if (p.hasBinding()) {
                if (p.getId().equals(power.getId())) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    /** The keybind label (e.g. "V") for an active-power slot. */
    public static Component slotKeyComponent(int slot) {
        if (slot >= 0 && slot < ACTIVE_POWER_KEYS.length) {
            return ACTIVE_POWER_KEYS[slot].getTranslatedKeyMessage();
        }
        return Component.empty();
    }

    /** Translation key name for an active-power slot (used for HUD labels). */
    public static String slotKeyName(int slot) {
        return slot <= 0 ? "key.originsx.active_power" : "key.originsx.active_power." + slot;
    }

    public static final class ModEvents {
        private ModEvents() {
        }

        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_RACE_HUD);
            event.register(HUD_SETTINGS);
            for (KeyMapping key : ACTIVE_POWER_KEYS) {
                event.register(key);
            }
        }
    }

    @EventBusSubscriber(modid = OriginsX.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void registerClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(net.minecraft.commands.Commands.literal("race")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(
                                OriginsXClient::openSelectionScreen);
                        return 1;
                    }));
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (OPEN_RACE_HUD.consumeClick()) {
                RaceInfoHUD.toggle();
            }
            if (HUD_SETTINGS.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.level != null) {
                    mc.setScreenAndShow(dev.originsx.client.gui.HudSettingsScreen.create());
                }
            }
            for (int slot = 0; slot < ACTIVE_POWER_KEYS.length; slot++) {
                if (ACTIVE_POWER_KEYS[slot].consumeClick()) {
                    ClientPacketDistributor.sendToServer(new PowerKeyPayload(slot));
                }
            }
        }

        /**
         * The server never runs {@code jumpFromGround()} for a non-passenger
         * player, so jump-triggered powers would never fire. Report the
         * local player's jump to the server instead.
         * <p>
         * Jump *restriction* also has to happen here, client-side: by the
         * time the server hears about the jump (via the packet below) the
         * client has already applied the upward velocity and moved, so
         * zeroing the server's delta movement in {@code ActionRestrictionPower}
         * has no visible effect. Cancelling the vertical velocity locally,
         * before the packet is even sent, is what actually stops the jump.
         */
        @SubscribeEvent
        public static void onLocalJump(LivingEvent.LivingJumpEvent event) {
            if (event.getEntity() instanceof LocalPlayer player) {
                ActionRestrictionPower restriction = activeRestriction(player);
                if (restriction != null && restriction.isJumpRestricted()) {
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.x, 0, motion.z);
                    return;
                }
                ClientPacketDistributor.sendToServer(new JumpPayload());
            }
        }

        /**
         * Sprint and swim-up restriction have the exact same problem as jump:
         * both are driven by client-side prediction (sprint's speed boost and
         * swim's upward motion are computed locally from input, not from the
         * server-authoritative position), so {@code ActionRestrictionPower}'s
         * server-side {@code onTick} handling never actually stops them for
         * the player experiencing it. Enforce them here every client tick
         * instead, before the client applies its own movement for the tick.
         */
        @SubscribeEvent
        public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;

            // movement-affecting powers run CLIENT-side: the local player's
            // motion is client-authoritative, server-side edits never stick
            Race race = SelectedRaceClient.getRace();
            if (race != null) {
                for (Power power : race.getPowers()) {
                    // only powers that actually HAVE a synced toggle state are
                    // gated by it (absent entry = not a toggle = always active)
                    Boolean toggleState = dev.raceapi.client.PowerStateClient.all()
                            .get(power.getId().toString());
                    if (toggleState != null && !toggleState) {
                        continue;
                    }
                    Power wrapped = power.getWrapped();
                    if (wrapped instanceof dev.raceapi.power.HyperInertiaPower inertia) {
                        // acceleration only while a movement key is held, so
                        // releasing the keys lets friction stop the player
                        var move = player.input.getMoveVector();
                        boolean hasInput = player.input.hasForwardImpulse()
                                || move.x != 0.0f || move.y != 0.0f;
                        inertia.applyClient(player, hasInput);
                    } else if (wrapped instanceof dev.raceapi.power.SpiderClimbPower climb) {
                        climb.applyClient(player);
                    } else if (wrapped instanceof dev.raceapi.power.WallJumpPower wallJump) {
                        wallJump.applyClient(player);
                    } else if (wrapped instanceof dev.raceapi.power.DensityAnchorPower anchor) {
                        anchor.applyClient(player);
                    }
                }
            }
            // empty stamina pool = no sprint (drain itself is server-side)
            if (dev.raceapi.client.ResourceClient.hasResource()
                    && dev.raceapi.client.ResourceClient.current() <= 0.01
                    && player.isSprinting()) {
                player.setSprinting(false);
            }

            ActionRestrictionPower restriction = activeRestriction(player);
            if (restriction == null) return;

            if (restriction.isSprintRestricted() && player.isSprinting()) {
                player.setSprinting(false);
            }
            if (restriction.isSwimRestricted() && player.isInWater()) {
                Vec3 motion = player.getDeltaMovement();
                if (motion.y > 0.05) {
                    player.setDeltaMovement(motion.x, Math.min(motion.y, 0.05), motion.z);
                }
            }
        }

        @Nullable
        private static ActionRestrictionPower activeRestriction(LocalPlayer player) {
            Race race = SelectedRaceClient.getRace();
            if (race == null) return null;
            for (Power power : race.getPowers()) {
                if (power.getWrapped() instanceof ActionRestrictionPower restriction) {
                    return restriction;
                }
            }
            return null;
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
            // Server-side event: covers singleplayer (integrated server shares
            // this JVM). Dedicated servers need the client-side hook below.
            autoOpenRetries = 0;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && !autoOpenScheduled) {
                autoOpenScheduled = true;
                mc.execute(OriginsXClient::tryAutoOpen);
            }
        }

        @SubscribeEvent
        public static void onClientLoggingIn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            // Client-side join hook: fires on dedicated servers too, where the
            // server PlayerLoggedInEvent never reaches this JVM
            autoOpenRetries = 0;
            Minecraft mc = Minecraft.getInstance();
            if (!autoOpenScheduled) {
                autoOpenScheduled = true;
                mc.execute(OriginsXClient::tryAutoOpen);
            }
        }

        @SubscribeEvent
        public static void onClientLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            SelectedRaceClient.reset();
            dev.raceapi.client.ResourceClient.reset();
            autoOpenScheduled = false;
        }

        /**
         * Draws the configurable HUD indicators (cooldown bars + race resource
         * bar); layout, colors and custom textures live in HudConfig.
         */
        @SubscribeEvent
        public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            // toast messages render on top of the world HUD while no menu is open
            if (mc.gui.screen() == null && event.getName().equals(VanillaGuiLayers.HOTBAR)) {
                dev.originsx.client.gui.UiToaster.render(event.getGuiGraphics());
            }
            if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) {
                return;
            }
            if (mc.player == null || mc.level == null) {
                return;
            }
            dev.originsx.client.hud.HudRenderer.render(event.getGuiGraphics(), mc);
        }

        /**
         * Toasts draw after the screen contents, so they are always on top of
         * any OriginsX (or vanilla) menu.
         */
        @SubscribeEvent
        public static void onScreenRender(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
            dev.originsx.client.gui.UiToaster.render(event.getGuiGraphics());
        }
    }
}
