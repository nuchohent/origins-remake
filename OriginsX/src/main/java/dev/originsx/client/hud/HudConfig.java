package dev.originsx.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side HUD layout config ({@code config/originsx/hud.json}). Players can
 * edit it by hand or through the in-game HUD settings screen: position of the
 * cooldown and resource indicators, their sizes, colors, optional custom
 * textures, and text labels.
 * <p>
 * Positioning model: {@code anchor} picks a screen corner/edge, {@code x}/{@code y}
 * are the distance from that edge in GUI-scaled pixels. For right anchors x
 * counts leftward, for bottom anchors y counts upward - so positive values
 * always move the element inward.
 */
public final class HudConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "originsx/hud.json";

    private static volatile HudConfig instance;

    public Cooldown cooldown = new Cooldown();
    public Bar resource = new Bar();
    public Toggles toggles = new Toggles();

    /** ON/OFF indicators of toggle powers (e.g. Overdrive). */
    public static final class Toggles {
        public boolean enabled = true;
        public String anchor = "bottom_right";
        public int x = 8;
        public int y = 45;
        /** ARGB hex of the "ON" text. */
        public String color = "FF55FF55";
    }

    /** Cooldown bars of active powers. */
    public static final class Cooldown {
        public boolean enabled = true;
        public boolean showLabels = true;
        public String anchor = "bottom_left";
        public int x = 8;
        public int y = 33;
        public int barWidth = 76;
        public int barHeight = 4;
        /** ARGB hex ("FFFF6A00"), used when no texture is set. */
        public String color = "FFFF6A00";
        /** Path to a custom PNG; empty = colored fill. */
        public String texture = "";
    }

    /** The race resource (mana/stamina) bar. */
    public static final class Bar {
        public boolean enabled = true;
        public boolean showText = true;
        public String anchor = "bottom_center";
        public int x = 0;
        // above the vanilla health/hunger rows (hotbar 22 + xp ~8 + two rows ~14)
        public int y = 45;
        public int width = 182;
        public int height = 5;
        public String fillColor = "FF55CCFF";
        public String texture = "";
    }

    public static HudConfig get() {
        if (instance == null) {
            reload();
        }
        return instance;
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    public static synchronized void reload() {
        Path path = file();
        try {
            if (Files.exists(path)) {
                instance = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), HudConfig.class);
            }
        } catch (Exception e) {
            // fall back to defaults below; keep the game running
        }
        if (instance == null) {
            instance = new HudConfig();
        }
        // explicit JSON nulls ("cooldown": null) deserialize to null fields —
        // replace them with fresh defaults before sanitizing
        var defaults = new HudConfig();
        if (instance.cooldown == null) instance.cooldown = defaults.cooldown;
        if (instance.resource == null) instance.resource = defaults.resource;
        if (instance.toggles == null) instance.toggles = defaults.toggles;
        sanitize(instance.cooldown);
        sanitize(instance.resource);
        sanitize(instance.toggles);
    }

    public static void save(HudConfig config) {
        instance = config;
        var defaults = new HudConfig();
        if (config.cooldown == null) config.cooldown = defaults.cooldown;
        if (config.resource == null) config.resource = defaults.resource;
        if (config.toggles == null) config.toggles = defaults.toggles;
        sanitize(config.cooldown);
        sanitize(config.resource);
        sanitize(config.toggles);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        HudTextures.invalidate();
    }

    private static void sanitize(Cooldown c) {
        c.barWidth = clamp(c.barWidth, 4, 400);
        c.barHeight = clamp(c.barHeight, 1, 100);
        c.x = clamp(c.x, -2000, 8000);
        c.y = clamp(c.y, -2000, 8000);
        if (!isAnchor(c.anchor)) {
            c.anchor = "bottom_left";
        }
    }

    private static void sanitize(Bar b) {
        b.width = clamp(b.width, 4, 2000);
        b.height = clamp(b.height, 1, 200);
        b.x = clamp(b.x, -2000, 8000);
        b.y = clamp(b.y, -2000, 8000);
        if (!isAnchor(b.anchor)) {
            b.anchor = "bottom_center";
        }
    }

    private static void sanitize(Toggles t) {
        t.x = clamp(t.x, -2000, 8000);
        t.y = clamp(t.y, -2000, 8000);
        if (!isAnchor(t.anchor)) {
            t.anchor = "bottom_right";
        }
    }

    private static boolean isAnchor(String anchor) {
        return "top_left".equals(anchor) || "top_center".equals(anchor) || "top_right".equals(anchor)
                || "bottom_left".equals(anchor) || "bottom_center".equals(anchor) || "bottom_right".equals(anchor);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
