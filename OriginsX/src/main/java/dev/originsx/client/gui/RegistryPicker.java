package dev.originsx.client.gui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Searchable registry dropdown (creative-search style): a button that opens a
 * panel with a text filter and ALL entries of a registry (items, entities,
 * effects), each row showing an icon (items and spawn eggs) plus the
 * translated display name and the raw id. Replaces the fixed hardcoded
 * option lists that only offered a dozen choices.
 */
@OnlyIn(Dist.CLIENT)
public final class RegistryPicker extends UIElement {

    private static final float PANEL_WIDTH = 260;
    private static final float PANEL_MAX_HEIGHT = 260;
    private static final float ROW_HEIGHT = 18;
    private static final float TAB_SIZE = 16;
    /** Cap rendered rows so huge registries don't build thousands of widgets. */
    private static final int MAX_ROWS = 300;

    public enum Kind {
        ITEM, ENTITY, EFFECT
    }

    /** One selectable registry entry. */
    public record Entry(Identifier id, Component name, @Nullable IGuiTexture texture) {
    }

    /** A creative-style category: name, icon and the items belonging to it. */
    private record Category(Component name, @Nullable ItemStack icon, List<Entry> entries) {
    }

    /**
     * Only one dropdown stays open at a time across the whole screen. Kept as
     * a weak reference: if the owning screen closed while a dropdown hung
     * open, close() never runs and a strong static would pin the whole
     * detached UI tree in memory until the next picker use.
     */
    @Nullable
    private static java.lang.ref.WeakReference<RegistryPicker> openDropdown;

    private final Kind kind;
    private final Button button = new Button();
    @Nullable
    private String value;
    @Nullable
    private Consumer<String> onChange;
    @Nullable
    private UIElement overlay;

    /** Creative categories (index 0 = "all"), built lazily for ITEM kind. */
    private final List<Category> categories = new ArrayList<>();
    private boolean categoriesBuilt;
    private int selectedCategory;

    public RegistryPicker(Kind kind) {
        this.kind = kind;
        button.layout(l -> l.widthPercent(100).heightPercent(100));
        button.textStyle(s -> s.fontSize(9));
        button.setOnClick(this::toggle);
        addChild(button);
    }

    public String getValue() {
        return value == null ? "" : value;
    }

    public void setValue(@Nullable String id, boolean fire) {
        this.value = id;
        button.setText(displayName(id));
        if (fire && onChange != null) {
            onChange.accept(id == null ? "" : id);
        }
    }

    public void setOnValueChanged(@Nullable Consumer<String> onChange) {
        this.onChange = onChange;
    }

    private Component displayName(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return Component.translatable("originsx.creator.picker.empty");
        }
        Entry entry = find(id);
        return entry != null ? entry.name() : Component.literal(id);
    }

    @Nullable
    private Entry find(String id) {
        Identifier loc = Identifier.tryParse(id);
        if (loc == null) {
            return null;
        }
        return switch (kind) {
            case ITEM -> BuiltInRegistries.ITEM.get(loc).isPresent()
                    ? entry(BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.get(loc).get().value()),
                    new ItemStack(BuiltInRegistries.ITEM.get(loc).get().value()))
                    : null;
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.get(loc).isPresent()
                    ? entry(BuiltInRegistries.ENTITY_TYPE.getKey(BuiltInRegistries.ENTITY_TYPE.get(loc).get().value()),
                    BuiltInRegistries.ENTITY_TYPE.get(loc).get().value())
                    : null;
            case EFFECT -> BuiltInRegistries.MOB_EFFECT.get(loc).isPresent()
                    ? entry(BuiltInRegistries.MOB_EFFECT.getKey(BuiltInRegistries.MOB_EFFECT.get(loc).get().value()),
                    BuiltInRegistries.MOB_EFFECT.get(loc).get().value())
                    : null;
        };
    }

    private static Entry entry(Identifier id, ItemStack icon) {
        return new Entry(id, icon.getHoverName(), new ItemStackTexture(icon));
    }

    private static Entry entry(Identifier id, MobEffect effect) {
        // same icon the player sees on the HUD effect icons
        Identifier texture = Identifier.fromNamespaceAndPath(id.getNamespace(),
                "textures/mob_effect/" + id.getPath() + ".png");
        return new Entry(id, effect.getDisplayName(), SpriteTexture.of(texture));
    }

    private static Entry entry(Identifier id, EntityType<?> type) {
        // spawn egg icon when the mod provides one (<entity>_spawn_egg)
        ItemStack icon = null;
        Identifier egg = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
        var eggItem = BuiltInRegistries.ITEM.get(egg);
        if (eggItem.isPresent()) {
            icon = new ItemStack(eggItem.get().value());
        }
        return new Entry(id, type.getDescription(),
                icon != null ? new ItemStackTexture(icon) : null);
    }

    private List<Entry> allEntries() {
        if (kind == Kind.ITEM && selectedCategory > 0 && selectedCategory < categories.size()) {
            return categories.get(selectedCategory).entries();
        }
        List<Entry> list = new ArrayList<>();
        switch (kind) {
            case ITEM -> BuiltInRegistries.ITEM.forEach(item ->
                    list.add(entry(BuiltInRegistries.ITEM.getKey(item), new ItemStack(item))));
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.forEach(type -> {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                // the player type is not summonable
                if (!id.getPath().equals("player")) {
                    list.add(entry(id, type));
                }
            });
            case EFFECT -> BuiltInRegistries.MOB_EFFECT.forEach(effect ->
                    list.add(entry(BuiltInRegistries.MOB_EFFECT.getKey(effect), effect)));
        }
        list.sort((a, b) -> a.name().getString().compareToIgnoreCase(b.name().getString()));
        return list;
    }

    /**
     * Builds creative-style categories for the ITEM kind (index 0 = "all").
     * Uses the same tab contents the creative menu shows, so modded items
     * land in their mod's tab and nothing is hidden behind the search cap.
     */
    private void buildCategories() {
        categories.clear();
        selectedCategory = 0;
        categories.add(new Category(
                Component.translatable("originsx.creator.picker.cat_all"), null, allEntries()));
        if (kind != Kind.ITEM) {
            categoriesBuilt = true;
            return;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            categoriesBuilt = true;
            return;
        }
        try {
            // NOTE: the return value only says whether contents were REBUILT
            // this call; false also means they were already built and valid,
            // so the tabs must be read regardless
            net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
                    mc.level.enabledFeatures(), false, mc.level.registryAccess());
            for (net.minecraft.world.item.CreativeModeTab tab : net.minecraft.world.item.CreativeModeTabs.tabs()) {
                if (tab.getType() != net.minecraft.world.item.CreativeModeTab.Type.CATEGORY) {
                    continue;
                }
                java.util.LinkedHashMap<Identifier, Entry> items = new java.util.LinkedHashMap<>();
                for (ItemStack stack : tab.getDisplayItems()) {
                    Item item = stack.getItem();
                    Identifier id = BuiltInRegistries.ITEM.getKey(item);
                    items.putIfAbsent(id, entry(id, new ItemStack(item)));
                }
                if (!items.isEmpty()) {
                    categories.add(new Category(tab.getDisplayName(),
                            tab.getIconItem(), new ArrayList<>(items.values())));
                }
            }
        } catch (Exception ignored) {
            // fall back to the flat "all" list
        }
        categoriesBuilt = true;
    }

    private void toggle(UIEvent event) {
        if (overlay != null) {
            close();
            return;
        }
        open(event);
    }

    @Nullable
    private static RegistryPicker currentOpen() {
        return openDropdown == null ? null : openDropdown.get();
    }

    private void open(UIEvent event) {
        RegistryPicker current = currentOpen();
        if (current != null && current != this) {
            current.close();
        }
        UIElement host = this;
        while (host.getParent() != null) {
            host = host.getParent();
        }
        Vector2f local = host.getLocalMouse(event.x, event.y);

        if (!categoriesBuilt) {
            buildCategories();
        }

        var overlayEl = new UIElement();
        overlayEl.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).heightPercent(100));

        var panel = new UIElement();
        panel.style(s -> s.background(new ColorRectTexture(UiPalette.PANEL_BG)));

        var search = new TextField();
        search.layout(l -> l.widthPercent(100).height(14));
        search.textFieldStyle(s -> s.placeholder(
                Component.translatable("originsx.creator.picker.search")));
        search.setTextResponder(v -> rebuildRows(viewRef[0], v));

        var scroller = new ScrollerView();
        scroller.viewContainer(view -> {
            view.layout(l -> l.widthPercent(100)
                    .flexDirection(FlexDirection.COLUMN).gapAll(1));
            viewRef[0] = view;
        });
        scroller.layout(l -> l.flex(1).widthPercent(100));

        float height = Mth.clamp(PANEL_MAX_HEIGHT, ROW_HEIGHT * 5, PANEL_MAX_HEIGHT);
        float px = Mth.clamp(local.x - 60, 2, Math.max(2, host.getSizeWidth() - PANEL_WIDTH - 6));
        float py = Mth.clamp(local.y + 6, 2, Math.max(2, host.getSizeHeight() - height - 2));

        panel.addChild(search);
        if (kind == Kind.ITEM && categories.size() > 1) {
            panel.addChild(categoryStrip());
            // give the rows the remaining space: strip rows + padding
            height += stripHeight();
        }
        float panelHeight = height;
        panel.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .left(px).top(py).width(PANEL_WIDTH).height(panelHeight)
                .flexDirection(FlexDirection.COLUMN).paddingAll(3).gapAll(2));

        panel.addChild(scroller);
        rebuildRows(viewRef[0], "");

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

    private final UIElement[] viewRef = new UIElement[1];
    private final UIElement[] stripRef = new UIElement[1];

    /** Height of the category strip: rows of TAB_SIZE icon buttons. */
    private float stripHeight() {
        int perRow = Math.max(1, (int) ((PANEL_WIDTH - 6) / (TAB_SIZE + 2)));
        int rows = (int) Math.ceil(categories.size() / (float) perRow);
        return rows * (TAB_SIZE + 2) + 2;
    }

    /** Wrap row of creative-category icon buttons; index 0 is "all". */
    private UIElement categoryStrip() {
        var strip = new UIElement().layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP)
                .gapAll(2).paddingAll(1));
        stripRef[0] = strip;
        rebuildStrip();
        return strip;
    }

    private void rebuildStrip() {
        UIElement strip = stripRef[0];
        if (strip == null) {
            return;
        }
        strip.clearAllChildren();
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            final int index = i;
            Button tab = new Button();
            tab.layout(l -> l.width(TAB_SIZE).height(TAB_SIZE));
            tab.noText();
            boolean selected = i == selectedCategory;
            tab.style(s -> s.background(selected
                    ? new ColorRectTexture(UiPalette.ACCENT)
                    : new ColorRectTexture(UiPalette.PANEL_BG)));
            tab.style(s -> s.tooltips(category.name()));
            if (category.icon() != null) {
                // icon as a CHILD element: a backgroundTexture set on the
                // button itself would be hidden under the background color
                UIElement icon = new UIElement();
                icon.layout(l -> l.widthPercent(100).heightPercent(100));
                icon.style(s -> s.backgroundTexture(
                        new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(category.icon())));
                tab.addChild(icon);
            }
            tab.setOnClick(e -> {
                selectedCategory = index;
                rebuildStrip();
                rebuildRows(viewRef[0], "");
            });
            strip.addChild(tab);
        }
    }

    private void rebuildRows(UIElement view, String query) {
        if (view == null) {
            return;
        }
        view.clearAllChildren();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int shown = 0;
        for (Entry entry : allEntries()) {
            if (!q.isEmpty()
                    && !entry.name().getString().toLowerCase(Locale.ROOT).contains(q)
                    && !entry.id().toString().contains(q)) {
                continue;
            }
            if (shown >= MAX_ROWS) {
                Label more = new Label();
                more.setText(Component.translatable("originsx.creator.picker.more", MAX_ROWS));
                more.textStyle(style -> style.fontSize(8).textColor(UiPalette.TEXT_HINT));
                more.layout(l -> l.widthPercent(100).height(ROW_HEIGHT));
                view.addChild(more);
                break;
            }
            view.addChild(rowFor(entry));
            shown++;
        }
        if (shown == 0) {
            Label empty = new Label();
            empty.setText("originsx.creator.picker.empty");
            empty.textStyle(style -> style.fontSize(8).textColor(UiPalette.TEXT_HINT));
            empty.layout(l -> l.widthPercent(100).height(ROW_HEIGHT));
            view.addChild(empty);
        }
    }

    private Button rowFor(Entry entry) {
        Button row = new Button();
        row.noText();
        row.layout(l -> l.widthPercent(100).height(ROW_HEIGHT)
                .flexDirection(FlexDirection.ROW).gapAll(4).alignItems(AlignItems.CENTER));
        boolean selected = entry.id().toString().equals(value);
        row.textStyle(s -> s.fontSize(9).textColor(selected
                ? UiPalette.ACCENT_BRIGHT : 0xFFDDDDDD));
        if (entry.texture() != null) {
            UIElement icon = new UIElement();
            icon.layout(l -> l.width(14).height(14));
            icon.style(s -> s.backgroundTexture(entry.texture()));
            row.addChild(icon);
        }
        Label label = new Label();
        label.setText(entry.name());
        label.textStyle(style -> style.fontSize(9).textColor(selected
                ? UiPalette.ACCENT_BRIGHT : 0xFFDDDDDD));
        label.layout(l -> l.flex(1));
        row.addChild(label);
        row.style(s -> s.tooltips(Component.literal(entry.id().toString())));
        row.setOnClick(e -> {
            setValue(entry.id().toString(), true);
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
