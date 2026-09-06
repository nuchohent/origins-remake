package dev.originsx.client.gui;

/** Shared UI palette matching the OriginsX 1.4.0 look. */
public final class UiPalette {

    /** Inputs, text fields, list rows - near black with a blue tint. */
    public static final int DEEP_BG = 0xFF14141A;
    /** Panels, cards, detail areas. */
    public static final int PANEL_BG = 0xFF22222A;
    /** Large content surfaces (guide root, screen roots). */
    public static final int SURFACE_BG = 0xFF1A1A22;
    /** Separator lines and dividers. */
    public static final int SEPARATOR = 0xFF444444;
    /** Section titles / headings. */
    public static final int ACCENT = 0xFF88CCFF;
    /** Bright accent (race names, key values). */
    public static final int ACCENT_BRIGHT = 0xFF55CCFF;
    /** Primary readable text. */
    public static final int TEXT = 0xFFDDDDDD;
    /** Secondary text. */
    public static final int TEXT_DIM = 0xFF999999;
    /** Hints and captions. */
    public static final int TEXT_HINT = 0xFF9A9AA0;
    /** Faintest text. */
    public static final int TEXT_FAINT = 0xFF777777;

    private UiPalette() {
    }
}
