package dev.originsx.client.hud;

import dev.originsx.client.OriginsXClient;
import dev.raceapi.client.CooldownClient;
import dev.raceapi.client.ResourceClient;
import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws the configurable HUD indicators: the active-power cooldown bars and
 * the race resource (mana/stamina) bar. Layout, colors and optional custom
 * textures come from {@link HudConfig}.
 */
public final class HudRenderer {

    private HudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft mc) {
        HudConfig config = HudConfig.get();
        Race race = dev.raceapi.client.SelectedRaceClient.getRace();
        if (race == null) {
            return;
        }
        if (config.resource.enabled && ResourceClient.hasResource()) {
            drawResourceBar(graphics, mc, config.resource);
        }
        if (config.cooldown.enabled) {
            drawCooldowns(graphics, mc, race, config.cooldown);
        }
        if (config.toggles.enabled) {
            drawToggles(graphics, mc, race, config.toggles);
        }
    }

    // ------------------------------------------------------------------ toggles

    /**
     * ON indicators of toggle powers (Overdrive etc.): a small "Name: ON"
     * line per switched-on power, fed by the server's power-state sync.
     */
    private static void drawToggles(GuiGraphicsExtractor g, Minecraft mc,
                                    Race race, HudConfig.Toggles cfg) {
        Map<String, Power> byId = new HashMap<>();
        for (Power power : race.getPowers()) {
            byId.put(power.getId().toString(), power);
        }
        List<String> lines = new ArrayList<>();
        for (var entry : dev.raceapi.client.PowerStateClient.all().entrySet()) {
            if (!entry.getValue()) {
                continue;
            }
            Power power = byId.get(entry.getKey());
            if (power != null) {
                lines.add(power.getDisplayName().getString());
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        int color = parseColor(cfg.color, 0xFF55FF55);
        int index = 0;
        for (String name : lines) {
            Component text = Component.translatable("originsx.hud.toggle_on", name);
            int width = mc.font.width(text);
            int[] pos = position(cfg.anchor, cfg.x, cfg.y - index * 11, width, 10, mc);
            g.text(mc.font, text.getVisualOrderText(), pos[0], pos[1], color);
            index++;
        }
    }

    // ------------------------------------------------------------------ resource

    private static void drawResourceBar(GuiGraphicsExtractor g, Minecraft mc, HudConfig.Bar cfg) {
        int[] pos = position(cfg.anchor, cfg.x, cfg.y, cfg.width, cfg.height, mc);
        int x = pos[0];
        int y = pos[1];

        if (cfg.showText) {
            String label = (int) Math.floor(ResourceClient.current()) + "/"
                    + (int) ResourceClient.max();
            g.text(mc.font, label,
                    x + cfg.width / 2 - mc.font.width(label) / 2, y - 10, 0xFFDDDDDD);
        }

        Identifier texture = textureId(cfg.texture);
        if (texture != null) {
            var entry = HudTextures.get(HudTextures.resolve(cfg.texture));
            if (entry != null) {
                g.fill(x - 1, y - 1, x + cfg.width + 1, y + cfg.height + 1, 0xFF000000);
                float fraction = fraction(ResourceClient.current(), ResourceClient.max());
                int filled = Math.max(0, Math.min(cfg.width, (int) (cfg.width * fraction)));
                if (filled > 0) {
                    g.enableScissor(x, y, x + filled, y + cfg.height);
                    blitStretch(g, texture, x, y, cfg.width, cfg.height, entry);
                    g.disableScissor();
                }
                return;
            }
        }
        int color = parseColor(cfg.fillColor, 0xFF55CCFF);
        g.fill(x - 1, y - 1, x + cfg.width + 1, y + cfg.height + 1, 0xFF000000);
        float fraction = fraction(ResourceClient.current(), ResourceClient.max());
        g.fill(x, y, x + cfg.width, y + cfg.height, 0xFF22222A);
        int filled = Math.max(0, Math.min(cfg.width, (int) (cfg.width * fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + cfg.height, color);
        }
    }

    // ------------------------------------------------------------------ cooldowns

    private static void drawCooldowns(GuiGraphicsExtractor g, Minecraft mc,
                                      Race race, HudConfig.Cooldown cfg) {
        Map<String, Power> byId = new HashMap<>();
        for (Power power : race.getPowers()) {
            byId.put(power.getId().toString(), power);
        }
        int index = 0;
        for (String powerId : List.copyOf(CooldownClient.active())) {
            float fraction = CooldownClient.remaining(powerId);
            if (fraction <= 0.0f) {
                continue;
            }
            Power power = byId.get(powerId);
            if (power == null) {
                continue;
            }
            int slot = OriginsXClient.boundSlot(race, power);
            int[] pos = position(cfg.anchor, cfg.x, cfg.y - index * (cfg.barHeight + 7),
                    cfg.barWidth, cfg.barHeight, mc);
            int x = pos[0];
            int y = pos[1];

            if (cfg.showLabels) {
                Component key = slot >= 0
                        ? Component.translatable("originsx.hud.slot",
                        OriginsXClient.slotKeyComponent(slot))
                        : Component.empty();
                g.text(mc.font, key.copy().append(" ").append(power.getDisplayName()).getVisualOrderText(),
                        x + cfg.barWidth + 4, y - 1, 0xFFFFFFFF);
            }

            Identifier texture = textureId(cfg.texture);
            if (texture != null) {
                var entry = HudTextures.get(HudTextures.resolve(cfg.texture));
                if (entry != null) {
                    g.fill(x - 1, y - 1, x + cfg.barWidth + 1, y + cfg.barHeight + 1, 0xFF000000);
                    int filled = Math.max(1, (int) (cfg.barWidth * fraction));
                    g.enableScissor(x, y, x + filled, y + cfg.barHeight);
                    blitStretch(g, texture, x, y, cfg.barWidth, cfg.barHeight, entry);
                    g.disableScissor();
                }
            } else {
                int color = parseColor(cfg.color, 0xFFFF6A00);
                g.fill(x - 1, y - 1, x + cfg.barWidth + 1, y + cfg.barHeight + 1, 0xFF000000);
                g.fill(x, y, x + Math.max(1, (int) (cfg.barWidth * fraction)), y + cfg.barHeight, color);
            }
            index++;
            if (index >= OriginsXClient.MAX_ACTIVE_SLOTS) {
                break;
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Screen position for an element of the given size. Positive offsets move
     * inward from the chosen anchor edges.
     */
    private static int[] position(String anchor, int xOff, int yOff, int width, int height, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean right = anchor.endsWith("right");
        boolean center = anchor.contains("center");
        boolean top = anchor.startsWith("top");
        int px = center ? (sw - width) / 2 + xOff : right ? sw - xOff - width : xOff;
        int py = top ? yOff : sh - yOff - height;
        return new int[]{px, py};
    }

    private static float fraction(double current, double max) {
        return max <= 0 ? 0 : (float) Math.max(0.0, Math.min(1.0, current / max));
    }

    @Nullable
    private static Identifier textureId(String configured) {
        return HudTextures.resolve(configured) != null ? Identifier.fromNamespaceAndPath("originsx", "hud_custom") : null;
    }

    /** Draws the whole source texture stretched to the destination rect. */
    private static void blitStretch(GuiGraphicsExtractor g, Identifier id, int x, int y,
                                    int width, int height, HudTextures.Entry entry) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0f, 0.0f, width, height,
                entry.width(), entry.height());
    }

    /** Parses an ARGB hex string ("FF55CCFF", with or without 0x); falls back on bad input. */
    public static int parseColor(String hex, int fallback) {
        try {
            String clean = hex == null ? "" : hex.trim().toLowerCase(Locale.ROOT);
            if (clean.startsWith("0x")) {
                clean = clean.substring(2);
            }
            if (clean.length() == 6) {
                clean = "ff" + clean;
            }
            if (clean.length() != 8) {
                return fallback;
            }
            return (int) Long.parseLong(clean, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
