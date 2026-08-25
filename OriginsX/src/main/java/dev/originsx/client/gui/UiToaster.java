package dev.originsx.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayDeque;

/**
 * Screen-independent toast messages. Active toasts live in a static queue and
 * are drawn on TOP of everything: from {@code ScreenEvent.Render.Post} while a
 * menu is open, and from the HUD layer in-game. This replaces the old
 * per-screen host approach, where toasts rendered behind menu panels (or not
 * at all while a menu was open).
 */
@OnlyIn(Dist.CLIENT)
public final class UiToaster {

    private static final long TOAST_LIFETIME_MS = 5000L;
    private static final int TOAST_BG = 0xDD0d1117;
    private static final int TOAST_EDGE = 0xFF00e5ff;
    private static final int TOAST_TEXT = 0xFFEEEEEE;
    private static final int TOAST_WIDTH = 320;
    private static final int MAX_TOASTS = 3;

    private static final ArrayDeque<Entry> ACTIVE = new ArrayDeque<>();

    private UiToaster() {
    }

    private record Entry(Component text, long born) {
    }

    public static void show(String key) {
        show(Component.translatable(key));
    }

    public static synchronized void show(Component text) {
        ACTIVE.addLast(new Entry(text, System.currentTimeMillis()));
        while (ACTIVE.size() > MAX_TOASTS) {
            ACTIVE.pollFirst();
        }
    }

    /** Renders active toasts top-center. Called from render events on both paths. */
    public static synchronized void render(GuiGraphicsExtractor g) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(entry -> now - entry.born() > TOAST_LIFETIME_MS);
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) {
            return;
        }
        int x = (g.guiWidth() - TOAST_WIDTH) / 2;
        int y = 8;
        for (Entry entry : ACTIVE) {
            var lines = mc.font.split(entry.text(), TOAST_WIDTH - 16);
            int height = lines.size() * 10 + 12;
            g.fill(x, y, x + TOAST_WIDTH, y + height, TOAST_BG);
            g.fill(x, y, x + 3, y + height, TOAST_EDGE);
            int lineY = y + 6;
            for (var line : lines) {
                g.text(mc.font, line, x + 10, lineY, TOAST_TEXT);
                lineY += 10;
            }
            y += height + 6;
        }
    }
}
