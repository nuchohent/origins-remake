package dev.originsx.looks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleSlot;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.LengthPercentageAuto;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modular layout editing for the appearance editor: Ctrl+L toggles an edit
 * mode where ANY widget can be grabbed and dragged (position) or resized
 * (bottom-right grip). The arrangement persists in
 * {@code config/originsx_looks/editor_layout.json} and is re-applied on the
 * next editor open.
 * <p>
 * How overrides work: every pin is applied as an {@link StyleOrigin#IMPORTANT}
 * style candidate on the element's style bag for
 * position/left/top/width/height. IMPORTANT outranks the inline layout set at
 * construction time, so a pinned widget breaks out of its flex flow and floats
 * at the given coordinates. "Reset" simply drops those IMPORTANT candidates,
 * which returns the widget to its natural auto layout.
 */
@OnlyIn(Dist.CLIENT)
public final class LayoutEditor {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int RESIZE_GRIP = 8;
    private static final float MIN_SIZE = 6f;

    /** One persisted widget transform (negative/zero w or h = keep auto size). */
    public static final class WidgetRecord {
        @SerializedName("id")
        public String id;
        @SerializedName("x")
        public float x;
        @SerializedName("y")
        public float y;
        @SerializedName("w")
        public float w;
        @SerializedName("h")
        public float h;
        @SerializedName("rot")
        public float rot;
    }

    public static final class LayoutConfig {
        @SerializedName("version")
        public int version = 1;
        @SerializedName("widgets")
        public List<WidgetRecord> widgets = new ArrayList<>();
    }

    private final UIElement root;
    private final List<UIElement> pinned = new ArrayList<>();
    private final Set<UIElement> pinnedSet = new LinkedHashSet<>();

    private boolean active;
    private UIElement overlay;
    private Label nameTag;
    private Label selectedRow;
    private LayoutOverlayTexture overlayTexture;

    @Nullable
    private UIElement selected;
    @Nullable
    private String selectedId;
    @Nullable
    private UIElement hovered;

    private boolean dragging;
    private boolean resizing;
    private float dragStartX;
    private float dragStartY;
    private UIElement moveParent;
    private float startScreenX;
    private float startScreenY;
    private float startW;
    private float startH;

    // kept as fields so add/removeEventListener compare equal by identity
    private final UIEventListener onDown = this::onMouseDown;
    private final UIEventListener onMove = this::onMouseMove;
    private final UIEventListener onUp = this::onMouseUp;

    private LayoutEditor(UIElement root) {
        this.root = root;
    }

    /** Creates the editor for a freshly built editor screen and re-applies the saved layout. */
    public static LayoutEditor attach(UIElement root) {
        LayoutEditor editor = new LayoutEditor(root);
        editor.applySaved();
        return editor;
    }

    public boolean isActive() {
        return active;
    }

    // ------------------------------------------------------------------
    //  Toggle
    // ------------------------------------------------------------------

    public void toggle() {
        if (active) {
            deactivate();
        } else {
            activate();
        }
    }

    private void activate() {
        if (active) {
            return;
        }
        active = true;
        overlayTexture = new LayoutOverlayTexture();
        overlay = new UIElement().layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).heightPercent(100));
        overlay.style(s -> s.background(overlayTexture));
        buildToolbar(overlay);
        nameTag = new Label();
        nameTag.textStyle(s -> s.fontSize(6).textColor(0xFFFFFF00));
        nameTag.setDisplay(false);
        overlay.addChild(nameTag);
        root.addChild(overlay);

        root.addEventListener(UIEvents.MOUSE_DOWN, onDown, true);
        root.addEventListener(UIEvents.MOUSE_MOVE, onMove, true);
        root.addEventListener(UIEvents.MOUSE_UP, onUp, true);
    }

    private void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        saveToDisk();
        root.removeEventListener(UIEvents.MOUSE_DOWN, onDown, true);
        root.removeEventListener(UIEvents.MOUSE_MOVE, onMove, true);
        root.removeEventListener(UIEvents.MOUSE_UP, onUp, true);
        if (overlay != null) {
            root.removeChild(overlay);
            overlay = null;
        }
        selected = null;
        selectedId = null;
        hovered = null;
        nameTag = null;
        selectedRow = null;
        overlayTexture = null;
    }

    // ------------------------------------------------------------------
    //  Toolbar
    // ------------------------------------------------------------------

    private void buildToolbar(UIElement host) {
        var toolbar = new UIElement().layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(4).top(4).width(270).height(52)
                .flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(3));
        toolbar.style(s -> s.background(new ColorRectTexture(0xCC22222A)));

        Label title = new Label();
        title.setText("gui.originsx_looks.layout.title");
        title.textStyle(s -> s.fontSize(7).textColor(0xFF9A9AA0));
        toolbar.addChild(title);

        selectedRow = new Label();
        selectedRow.textStyle(s -> s.fontSize(6).textColor(0xFF55CCFF).adaptiveWidth(true));
        selectedRow.setText("<none>");
        toolbar.addChild(selectedRow);

        var buttons = new UIElement().layout(l -> l.widthPercent(100).height(16)
                .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(4));
        Button reset = new Button();
        reset.setText("gui.originsx_looks.layout.reset");
        reset.textStyle(s -> s.fontSize(6));
        reset.layout(l -> l.width(64).height(14));
        reset.setOnClick(e -> resetAll());
        buttons.addChild(reset);
        Button exit = new Button();
        exit.setText("gui.originsx_looks.layout.done");
        exit.textStyle(s -> s.fontSize(6));
        exit.layout(l -> l.width(64).height(14));
        exit.setOnClick(e -> toggle());
        buttons.addChild(exit);
        toolbar.addChild(buttons);

        host.addChild(toolbar);
    }

    // ------------------------------------------------------------------
    //  Hit testing
    // ------------------------------------------------------------------

    private boolean insideRect(UIElement el, float px, float py) {
        // getPositionX/Y are the screen-absolute boxes the renderer actually
        // draws at (getLocalToWorldPose only carries Transform2D, not layout).
        return px >= el.getPositionX() && px <= el.getPositionX() + el.getSizeWidth()
                && py >= el.getPositionY() && py <= el.getPositionY() + el.getSizeHeight();
    }

    private boolean insideToolbar(float px, float py) {
        if (overlay == null || overlay.getChildren().isEmpty()) {
            return false;
        }
        UIElement toolbar = overlay.getChildren().get(0);
        return insideRect(toolbar, px, py);
    }

    @Nullable
    private UIElement hitTest(float px, float py) {
        return hitTestRec(root, px, py);
    }

    /** True when the element sits inside the editor overlay (toolbar/nameTag). */
    private boolean insideOverlay(@Nullable UIElement el) {
        while (el != null) {
            if (el == overlay) {
                return true;
            }
            el = el.getParent();
        }
        return false;
    }

    @Nullable
    private UIElement hitTestRec(UIElement el, float px, float py) {
        if (el == overlay || insideOverlay(el)) {
            return null;
        }
        if (el == root) {
            // never select the root itself, but DO walk its children
            UIElement best = null;
            for (UIElement child : el.getChildren()) {
                UIElement hit = hitTestRec(child, px, py);
                if (hit != null) {
                    best = hit;
                }
            }
            return best;
        }
        if (!el.isDisplayed()) {
            return null;
        }
        if (el.getSizeWidth() <= 0 || el.getSizeHeight() <= 0) {
            return null;
        }
        if (!insideRect(el, px, py)) {
            return null;
        }
        UIElement best = el;
        for (UIElement child : el.getChildren()) {
            UIElement hit = hitTestRec(child, px, py);
            if (hit != null) {
                best = hit;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    //  Mouse handling
    // ------------------------------------------------------------------

    private void onMouseDown(UIEvent event) {
        if (event.button != 0) {
            return;
        }
        float px = event.x;
        float py = event.y;
        if (insideToolbar(px, py)) {
            return;
        }
        event.stopImmediatePropagation();

        UIElement target = hitTest(px, py);
        if (target == null || target == overlay) {
            select(null);
            return;
        }
        while (target instanceof Label label && target.getParent() instanceof Button) {
            target = label.getParent();
        }
        select(target);
        if (selected == null) {
            return;
        }
        if (isOnGrip(px, py)) {
            resizing = true;
            startW = selected.getSizeWidth();
            startH = selected.getSizeHeight();
        } else {
            resizing = false;
            moveParent = selected.getParent();
            startScreenX = selected.getPositionX();
            startScreenY = selected.getPositionY();
        }
        dragging = true;
        dragStartX = px;
        dragStartY = py;
    }

    private boolean isOnGrip(float px, float py) {
        if (selected == null) {
            return false;
        }
        float px0 = selected.getPositionX();
        float py0 = selected.getPositionY();
        float w = selected.getSizeWidth();
        float h = selected.getSizeHeight();
        return px >= px0 + w - RESIZE_GRIP && px <= px0 + w
                && py >= py0 + h - RESIZE_GRIP && py <= py0 + h;
    }

    private void onMouseMove(UIEvent event) {
        if (dragging && selected != null) {
            float dx = event.x - dragStartX;
            float dy = event.y - dragStartY;
            if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) {
                return;
            }
            if (pinnedSet.add(selected)) {
                pinned.add(selected);
            }
            if (resizing) {
                float w = Math.max(MIN_SIZE, startW + dx);
                float h = Math.max(MIN_SIZE, startH + dy);
                setPinnedSize(selected, w, h);
            } else {
                // work fully in screen space, then project back into the parent's
                // local space so widgets inside scrolled/translated containers pin correctly
                UIElement parent = moveParent != null ? moveParent : selected.getParent();
                float parentX = parent == null ? 0 : parent.getPositionX();
                float parentY = parent == null ? 0 : parent.getPositionY();
                setPinnedPosition(selected, startScreenX + dx - parentX,
                        startScreenY + dy - parentY);
            }
            root.clearLayoutCache();
        } else {
            hovered = hitTest(event.x, event.y);
        }
    }

    private void onMouseUp(UIEvent event) {
        if (event.button != 0) {
            return;
        }
        if (dragging) {
            dragging = false;
            saveToDisk();
        }
    }

    // ------------------------------------------------------------------
    //  Pinning / resetting
    // ------------------------------------------------------------------

    private static void setPinnedPosition(UIElement el, float left, float top) {
        var bag = el.getStyleBag();
        bag.replaceOrPutCandidate(LayoutProperties.POSITION,
                StyleSlot.of(LayoutProperties.POSITION, StyleOrigin.IMPORTANT, 999, 0, TaffyPosition.ABSOLUTE));
        bag.replaceOrPutCandidate(LayoutProperties.LEFT,
                StyleSlot.of(LayoutProperties.LEFT, StyleOrigin.IMPORTANT, 999, 0, LengthPercentageAuto.length(left)));
        bag.replaceOrPutCandidate(LayoutProperties.TOP,
                StyleSlot.of(LayoutProperties.TOP, StyleOrigin.IMPORTANT, 999, 0, LengthPercentageAuto.length(top)));
        el.markTaffyStyleDirty();
    }

    private static void setPinnedSize(UIElement el, float width, float height) {
        var bag = el.getStyleBag();
        bag.replaceOrPutCandidate(LayoutProperties.WIDTH,
                StyleSlot.of(LayoutProperties.WIDTH, StyleOrigin.IMPORTANT, 999, 0, TaffyDimension.length(width)));
        bag.replaceOrPutCandidate(LayoutProperties.HEIGHT,
                StyleSlot.of(LayoutProperties.HEIGHT, StyleOrigin.IMPORTANT, 999, 0, TaffyDimension.length(height)));
        el.markTaffyStyleDirty();
    }

    private void select(@Nullable UIElement el) {
        selected = el;
        selectedId = el == null ? null : idOf(el);
        updateSelectedRow();
    }

    private void updateSelectedRow() {
        if (selectedRow == null) {
            return;
        }
if (selected == null) {
            selectedRow.setText(Component.literal("<none>"));
        } else {
            String pos = String.format("x %.0f y %.0f  w %.0f h %.0f",
                    selected.getLayoutX(), selected.getLayoutY(),
                    selected.getSizeWidth(), selected.getSizeHeight());
            selectedRow.setText(Component.literal(selectedId + "   " + pos));
        }
    }

    private void resetAll() {
        // drop every IMPORTANT layout override back to natural auto layout
        resetRec(root);
        cleanSaved();
        pinned.clear();
        pinnedSet.clear();
        selected = null;
        selectedId = null;
        hovered = null;
        updateSelectedRow();
        root.clearLayoutCache();
    }

    private static void resetRec(UIElement el) {
        var bag = el.getStyleBag();
        for (var p : List.of(LayoutProperties.POSITION, LayoutProperties.LEFT,
                LayoutProperties.TOP, LayoutProperties.WIDTH, LayoutProperties.HEIGHT)) {
            bag.removeCandidates(p, slot -> slot.origin() == StyleOrigin.IMPORTANT);
        }
        for (UIElement child : el.getChildren()) {
            resetRec(child);
        }
        el.markTaffyStyleDirty();
    }

    // ------------------------------------------------------------------
    //  Persistence
    // ------------------------------------------------------------------

    private static Path configDirectory() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config").resolve("originsx_looks");
    }

    private static Path configFile() {
        return configDirectory().resolve("editor_layout.json");
    }

    private void applySaved() {
        try {
            Path file = configFile();
            if (!Files.isRegularFile(file)) {
                return;
            }
            LayoutConfig config = GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), LayoutConfig.class);
            if (config == null || config.widgets == null) {
                return;
            }
            for (WidgetRecord record : config.widgets) {
                if (record == null || record.id == null) {
                    continue;
                }
                UIElement el = findByPath(record.id);
                if (el == null) {
                    continue;
                }
                setPinnedPosition(el, record.x, record.y);
                if (record.w > 0 && record.h > 0) {
                    setPinnedSize(el, record.w, record.h);
                }
                if (pinnedSet.add(el)) {
                    pinned.add(el);
                }
            }
            root.clearLayoutCache();
        } catch (Exception ignored) {
            dev.originsx.looks.LooksMod.LOGGER.warn("[Looks] failed to load editor layout", ignored);
        }
    }

    private void saveToDisk() {
        try {
            LayoutConfig config = new LayoutConfig();
            for (UIElement el : pinned) {
                WidgetRecord record = new WidgetRecord();
                record.id = idOf(el);
                record.x = el.getLayoutX();
                record.y = el.getLayoutY();
                record.w = el.getSizeWidth();
                record.h = el.getSizeHeight();
                config.widgets.add(record);
            }
            Path dir = configDirectory();
            Files.createDirectories(dir);
            Files.writeString(configFile(), GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            dev.originsx.looks.LooksMod.LOGGER.warn("[Looks] failed to save editor layout", e);
        }
    }

    private void cleanSaved() {
        try {
            Files.deleteIfExists(configFile());
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Tree / path helpers
    // ------------------------------------------------------------------

    /** Path of an element from the root: dot-joined child indexes. */
    @Nullable
    private static String idOf(UIElement el) {
        StringBuilder sb = new StringBuilder();
        UIElement cur = el;
        int depth = 0;
        while (cur != null) {
            UIElement parent = cur.getParent();
            if (parent == null) {
                break;
            }
            int index = indexOf(parent, cur);
            if (index < 0) {
                return null;
            }
            if (sb.length() > 0) {
                sb.insert(0, '.');
            }
            sb.insert(0, index);
            cur = parent;
            if (++depth > 64) {
                return null;
            }
        }
        return sb.toString();
    }

    private static int indexOf(UIElement parent, UIElement child) {
        List<UIElement> children = parent.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == child) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private UIElement findByPath(String path) {
        UIElement cur = root;
        for (String part : path.split("\\.")) {
            if (cur == null) {
                return null;
            }
            int index;
            try {
                index = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            List<UIElement> children = cur.getChildren();
            if (index < 0 || index >= children.size()) {
                return null;
            }
            cur = children.get(index);
        }
        return cur == root ? null : cur;
    }

    /** Called every screen tick while the editor screen is open. */
    public void tick() {
        if (!active || nameTag == null || overlay == null) {
            return;
        }
        if (selected == null) {
            nameTag.setDisplay(false);
            return;
        }
        // overlay fills the whole screen at (0,0), so the element's screen-absolute
        // position IS its overlay-local position here
        float w = selected.getSizeWidth();
        float h = selected.getSizeHeight();
        float tx = selected.getPositionX();
        float ty = selected.getPositionY() - 14;
        if (ty < 2) {
            ty = selected.getPositionY() + h + 2;
        }
        final float fx = tx;
        final float fy = ty;
        String text = selectedId == null ? "" : selectedId;
        Component shown = nameTag.getValue();
        String cur = shown == null ? null : shown.getString();
        boolean textChanged = cur == null || !cur.equals(text);
        boolean moved = nameLayoutDirty(fx, fy);
        if (textChanged || moved) {
            if (textChanged) {
                nameTag.setText(text);
            }
            if (moved) {
                nameTag.getStyleBag().replaceOrPutCandidate(LayoutProperties.LEFT,
                        StyleSlot.of(LayoutProperties.LEFT, StyleOrigin.IMPORTANT, 999, 0,
                                LengthPercentageAuto.length(fx)));
                nameTag.getStyleBag().replaceOrPutCandidate(LayoutProperties.TOP,
                        StyleSlot.of(LayoutProperties.TOP, StyleOrigin.IMPORTANT, 999, 0,
                                LengthPercentageAuto.length(fy)));
                nameTag.markTaffyStyleDirty();
            }
        }
        nameTag.setDisplay(true);
        updateSelectedRow();
    }

    private float nameTagLeft = Float.NaN;
    private float nameTagTop = Float.NaN;

    private boolean nameLayoutDirty(float fx, float fy) {
        boolean dirty = nameTagLeft != fx || nameTagTop != fy;
        nameTagLeft = fx;
        nameTagTop = fy;
        return dirty;
    }

    // ------------------------------------------------------------------
    //  Overlay texture (boxes + hover + grip)
    // ------------------------------------------------------------------

    private final class LayoutOverlayTexture implements GuiTexture {
        @Override
        public void draw(GUIContext context, float x, float y, float width, float height) {
            if (overlay == null) {
                return;
            }
            var graphics = context.graphics;
            if (hovered != null && hovered != selected) {
                drawBox(graphics, hovered, 0x55FFFFFF);
            }
            for (UIElement el : pinned) {
                drawBox(graphics, el, 0x8855CCFF);
            }
            if (selected != null) {
                drawBox(graphics, selected, 0xFF55CCFF);
                float gx = selected.getPositionX() + selected.getSizeWidth() - RESIZE_GRIP;
                float gy = selected.getPositionY() + selected.getSizeHeight() - RESIZE_GRIP;
                graphics.fill(Math.round(gx), Math.round(gy),
                        Math.round(gx + RESIZE_GRIP), Math.round(gy + RESIZE_GRIP), 0xFF00DDFF);
            }
        }

        private void drawBox(GuiGraphicsExtractor graphics, UIElement el, int color) {
            float l = el.getPositionX();
            float t = el.getPositionY();
            float r = l + el.getSizeWidth();
            float b = t + el.getSizeHeight();
            int li = Math.round(l), ti = Math.round(t), ri = Math.round(r), bi = Math.round(b);
            graphics.fill(li, ti, ri, ti + 1, color);
            graphics.fill(li, bi - 1, ri, bi, color);
            graphics.fill(li, ti, li + 1, bi, color);
            graphics.fill(ri - 1, ti, ri, bi, color);
        }
    }
}