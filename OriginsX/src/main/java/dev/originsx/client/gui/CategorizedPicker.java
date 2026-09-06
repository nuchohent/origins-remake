package dev.originsx.client.gui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compact categorized dropdown (pattern from the Skill Tree 1.4.0 toolkit):
 * a display button that opens a small scrollable panel near the click point,
 * grouped by category headers, closed by picking an entry or clicking outside.
 * Replaces the LDLib2 {@code Selector} dialog that opened fullscreen.
 */
@OnlyIn(Dist.CLIENT)
public final class CategorizedPicker<T> extends UIElement {

    private static final float PANEL_WIDTH = 250;
    private static final float PANEL_MAX_HEIGHT = 260;
    private static final float ITEM_HEIGHT = 16;
    private static final float HEADER_HEIGHT = 13;

    /** Display/grouping rules for candidate items. */
    public interface Format<T> {
        Component name(T item);

        /** Shown as a tooltip; {@code null} hides it. */
        @Nullable
        Component description(T item);

        String categoryKey(T item);

        int categoryOrder(T item);

        boolean highlighted(T item);
    }

    /**
     * Only one dropdown stays open at a time across the whole screen. Weak on
     * purpose: a closed screen with a hanging dropdown must not pin its UI
     * tree in memory (see RegistryPicker).
     */
    @Nullable
    private static java.lang.ref.WeakReference<CategorizedPicker<?>> openDropdown;

    private final Format<T> format;
    private final Button button = new Button();
    private List<T> candidates = List.of();
    @Nullable
    private T value;
    @Nullable
    private Consumer<T> onChange;
    @Nullable
    private UIElement overlay;

    public CategorizedPicker(Format<T> format) {
        this.format = format;
        button.layout(l -> l.widthPercent(100).heightPercent(100));
        button.textStyle(s -> s.fontSize(9));
        button.setOnClick(this::toggle);
        addChild(button);
    }

    public void setCandidates(List<T> candidates) {
        // the list changed - a currently open dropdown would show stale entries
        close();
        this.candidates = List.copyOf(candidates);
    }

    @Nullable
    public T getValue() {
        return value;
    }

    public void setValue(@Nullable T value, boolean fire) {
        this.value = value;
        button.setText(value == null ? Component.empty() : format.name(value));
        if (fire && onChange != null) {
            onChange.accept(value);
        }
    }

    public void setOnValueChanged(@Nullable Consumer<T> onChange) {
        this.onChange = onChange;
    }

    private void toggle(UIEvent event) {
        if (overlay != null) {
            close();
            return;
        }
        open(event);
    }

    @Nullable
    private static CategorizedPicker<?> currentOpen() {
        return openDropdown == null ? null : openDropdown.get();
    }

    private void open(UIEvent event) {
        CategorizedPicker<?> current = currentOpen();
        if (current != null && current != this) {
            current.close();
        }
        UIElement host = this;
        while (host.getParent() != null) {
            host = host.getParent();
        }
        Vector2f local = host.getLocalMouse(event.x, event.y);

        var overlayEl = new UIElement();
        overlayEl.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).heightPercent(100));

        var panel = new UIElement();
        panel.style(s -> s.background(new ColorRectTexture(UiPalette.PANEL_BG)));

        var scroller = new ScrollerView();
        UIElement[] viewRef = new UIElement[1];
        scroller.viewContainer(view -> {
            view.layout(l -> l.widthPercent(100)
                    .flexDirection(FlexDirection.COLUMN).gapAll(1));
            viewRef[0] = view;
        });
        UIElement view = viewRef[0];

        int rows = 0;
        int headers = 0;
        String lastCategory = null;
        List<T> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(format::categoryOrder));
        for (T item : sorted) {
            String categoryKey = format.categoryKey(item);
            if (!categoryKey.equals(lastCategory)) {
                lastCategory = categoryKey;
                headers++;
                Label header = new Label();
                header.setText(categoryKey);
                header.textStyle(style -> style.fontSize(8).textColor(UiPalette.ACCENT));
                header.layout(l -> l.widthPercent(100).height(HEADER_HEIGHT));
                view.addChild(header);
            }
            rows++;
            view.addChild(rowFor(item));
        }
        if (sorted.isEmpty()) {
            Label empty = new Label();
            empty.setText("originsx.creator.picker.empty");
            empty.textStyle(style -> style.fontSize(8).textColor(UiPalette.TEXT_HINT));
            empty.layout(l -> l.widthPercent(100).height(ITEM_HEIGHT));
            view.addChild(empty);
            rows = 1;
        }

        float contentHeight = 10 + headers * (HEADER_HEIGHT + 1) + rows * (ITEM_HEIGHT + 1);
        float height = Mth.clamp(contentHeight, ITEM_HEIGHT * 3, PANEL_MAX_HEIGHT);
        float px = Mth.clamp(local.x - 60, 2, Math.max(2, host.getSizeWidth() - PANEL_WIDTH - 6));
        float py = Mth.clamp(local.y + 6, 2, Math.max(2, host.getSizeHeight() - height - 2));
        panel.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .left(px).top(py).width(PANEL_WIDTH).height(height)
                .flexDirection(FlexDirection.COLUMN).paddingAll(3));
        scroller.layout(l -> l.flex(1).widthPercent(100));
        panel.addChild(scroller);
        overlayEl.addChild(panel);
        overlayEl.addEventListener(UIEvents.MOUSE_DOWN, ev -> {
            for (UIElement ancestor = ev.target; ancestor != null;
                 ancestor = ancestor.getParent()) {
                if (ancestor == panel) {
                    return;
                }
            }
            close();
        }, true);

        host.addChild(overlayEl);
        overlay = overlayEl;
        openDropdown = new java.lang.ref.WeakReference<>(this);
    }

    private Button rowFor(T item) {
        Button row = new Button();
        row.setText(format.name(item));
        int color = item == value ? UiPalette.ACCENT_BRIGHT
                : format.highlighted(item) ? 0xFFFFFFFF : 0xFFDDDDDD;
        row.textStyle(s -> s.fontSize(9).textColor(color));
        row.layout(l -> l.widthPercent(100).height(ITEM_HEIGHT));
        Component desc = format.description(item);
        if (desc != null) {
            row.style(s -> s.tooltips(desc));
        }
        row.setOnClick(e -> {
            setValue(item, true);
            close();
        });
        return row;
    }

    private void close() {
        if (overlay != null) {
            UIElement host = overlay.getParent();
            if (host != null) {
                host.removeChild(overlay);
            }
            overlay = null;
        }
        if (currentOpen() == this) {
            openDropdown = null;
        }
    }
}
