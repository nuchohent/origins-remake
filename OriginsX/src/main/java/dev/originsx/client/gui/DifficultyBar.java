package dev.originsx.client.gui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A small horizontal difficulty bar. Fills from left to right based on
 * difficulty (-5 to +5). Cyberpunk neon colour scheme.
 */
@OnlyIn(Dist.CLIENT)
public class DifficultyBar extends UIElement {

    private static final int BAR_WIDTH = 56;
    private static final int BAR_HEIGHT = 5;

    private final int difficulty;

    public DifficultyBar(int difficulty) {
        this.difficulty = difficulty;
        layout(l -> l.width(BAR_WIDTH).height(BAR_HEIGHT));
    }

    @Override
    public void drawBackgroundAdditional(com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext ctx) {
        super.drawBackgroundAdditional(ctx);
        com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext g = (com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext) ctx;
        int x0 = (int) getContentX();
        int y0 = (int) getContentY();

        // Background track
        g.graphics.fill(x0, y0, x0 + BAR_WIDTH, y0 + BAR_HEIGHT, 0xFF0f1722);

        // Center notch: fill grows LEFT (green = strong) or RIGHT (red = weak)
        // from the middle, mirroring the creator's balance meter
        int half = BAR_WIDTH / 2;
        g.graphics.fill(x0 + half, y0, x0 + half + 1, y0 + BAR_HEIGHT, 0xFF3a4552);
        int fillW = Math.round(half * (Math.abs(difficulty) / 5f));
        if (fillW > 0) {
            int color = difficulty < 0 ? 0xFF10b981 : 0xFFef4444;
            int fx = difficulty < 0 ? x0 + half - fillW : x0 + half + 1;
            g.graphics.fill(fx, y0, fx + fillW, y0 + BAR_HEIGHT, color);
        }

        // Border (subtle neon outline)
        g.graphics.fill(x0 - 1, y0 - 1, x0 + BAR_WIDTH + 1, y0, 0xFF1e293b);
        g.graphics.fill(x0 - 1, y0 + BAR_HEIGHT, x0 + BAR_WIDTH + 1, y0 + BAR_HEIGHT + 1, 0xFF1e293b);
        g.graphics.fill(x0 - 1, y0, x0, y0 + BAR_HEIGHT, 0xFF1e293b);
        g.graphics.fill(x0 + BAR_WIDTH, y0, x0 + BAR_WIDTH + 1, y0 + BAR_HEIGHT, 0xFF1e293b);
    }

    private static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (from >> 16) & 0xFF, ag = (from >> 8) & 0xFF, ab = from & 0xFF;
        int br = (to >> 16) & 0xFF, bg = (to >> 8) & 0xFF, bb = to & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int b = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
