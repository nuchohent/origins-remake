package dev.originsx.client.gui;

import com.google.gson.Gson;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.originsx.client.hud.HudConfig;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * In-game editor for {@link HudConfig} (opened with the HUD settings keybind,
 * default J): position, size, colors, custom texture paths and label toggles
 * for the cooldown and resource indicators. Changes apply on "Save".
 */
@OnlyIn(Dist.CLIENT)
public final class HudSettingsScreen {

    private static final String[] ANCHORS = {
            "top_left", "top_center", "top_right",
            "bottom_left", "bottom_center", "bottom_right"
    };
    private static final String[] ON_OFF = {"true", "false"};
    private static final Gson GSON = new Gson();

    private HudSettingsScreen() {
    }

    public static ModularUIScreen create() {
        // edit a copy so an aborted screen leaves the live config untouched
        HudConfig working = GSON.fromJson(GSON.toJson(HudConfig.get()), HudConfig.class);

        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN)
                .gapAll(4).paddingAll(8).alignItems(AlignItems.STRETCH));
        root.style(s -> s.background(new ColorRectTexture(UiPalette.SURFACE_BG)));

        var title = new Label().setText("originsx.hud.settings.title");
        title.textStyle(s -> s.fontSize(14).textColor(UiPalette.ACCENT_BRIGHT));
        root.addChild(title);

        var scroller = new ScrollerView();
        scroller.layout(l -> l.flex(1).widthPercent(100));
        scroller.viewContainer(view -> {
            view.layout(l -> l.widthPercent(100)
                    .flexDirection(FlexDirection.COLUMN).gapAll(6));
            buildCooldownSection(view, working);
            buildResourceSection(view, working);
            view.addChild(hint());
        });
        root.addChild(scroller);
        root.addChild(actionsRow(working));

        var ui = UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP));
        return new ModularUIScreen(new ModularUI(ui),
                Component.translatable("originsx.hud.settings.title"));
    }

    // ---------------------------------------------------------------- sections

    private static void buildCooldownSection(UIElement view, HudConfig working) {
        view.addChild(sectionHeader("originsx.hud.section.cooldown"));
        HudConfig.Cooldown cfg = working.cooldown;

        view.addChild(fieldRow("originsx.hud.field.enabled",
                boolSelector(cfg.enabled, v -> cfg.enabled = v)));
        view.addChild(fieldRow("originsx.hud.field.anchor", anchorSelector(cfg.anchor, v -> cfg.anchor = v)));
        view.addChild(fieldRow("originsx.hud.field.x", intField(cfg.x, -2000, 8000, v -> cfg.x = v)));
        view.addChild(fieldRow("originsx.hud.field.y", intField(cfg.y, -2000, 8000, v -> cfg.y = v)));
        view.addChild(fieldRow("originsx.hud.field.bar_width", intField(cfg.barWidth, 4, 400, v -> cfg.barWidth = v)));
        view.addChild(fieldRow("originsx.hud.field.bar_height", intField(cfg.barHeight, 1, 100, v -> cfg.barHeight = v)));
        view.addChild(fieldRow("originsx.hud.field.color", colorField(cfg.color, v -> cfg.color = v)));
        view.addChild(fieldRow("originsx.hud.field.texture", pathField(cfg.texture, v -> cfg.texture = v)));
        view.addChild(fieldRow("originsx.hud.field.show_labels",
                boolSelector(cfg.showLabels, v -> cfg.showLabels = v)));
    }

    private static void buildResourceSection(UIElement view, HudConfig working) {
        view.addChild(sectionHeader("originsx.hud.section.resource"));
        HudConfig.Bar cfg = working.resource;

        view.addChild(fieldRow("originsx.hud.field.enabled",
                boolSelector(cfg.enabled, v -> cfg.enabled = v)));
        view.addChild(fieldRow("originsx.hud.field.anchor", anchorSelector(cfg.anchor, v -> cfg.anchor = v)));
        view.addChild(fieldRow("originsx.hud.field.x", intField(cfg.x, -2000, 8000, v -> cfg.x = v)));
        view.addChild(fieldRow("originsx.hud.field.y", intField(cfg.y, -2000, 8000, v -> cfg.y = v)));
        view.addChild(fieldRow("originsx.hud.field.width", intField(cfg.width, 4, 2000, v -> cfg.width = v)));
        view.addChild(fieldRow("originsx.hud.field.height", intField(cfg.height, 1, 200, v -> cfg.height = v)));
        view.addChild(fieldRow("originsx.hud.field.fill_color", colorField(cfg.fillColor, v -> cfg.fillColor = v)));
        view.addChild(fieldRow("originsx.hud.field.texture", pathField(cfg.texture, v -> cfg.texture = v)));
        view.addChild(fieldRow("originsx.hud.field.show_text",
                boolSelector(cfg.showText, v -> cfg.showText = v)));
    }

    private static UIElement hint() {
        var hint = new Label().setText("originsx.hud.settings.hint");
        hint.textStyle(s -> s.fontSize(8).adaptiveHeight(true).textColor(UiPalette.TEXT_HINT));
        return hint;
    }

    // ---------------------------------------------------------------- widgets

    private static UIElement sectionHeader(String key) {
        var header = new Label().setText(key);
        header.textStyle(s -> s.fontSize(11).textColor(UiPalette.ACCENT));
        return header;
    }

    private static UIElement fieldRow(String labelKey, UIElement input) {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                .alignItems(AlignItems.CENTER));
        var label = new Label();
        label.setText(labelKey);
        label.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
        row.addChildren(label, input);
        return row;
    }

    /** Boolean toggle rendered as an on/off dropdown (translatable labels). */
    private static UIElement boolSelector(boolean initial, java.util.function.Consumer<Boolean> onChange) {
        var selector = new Selector<String>();
        selector.layout(l -> l.flex(1).height(18));
        selector.setCandidates(java.util.List.of(ON_OFF));
        selector.setSelected(initial ? "true" : "false", false);
        selector.setCandidateUIProvider(value -> {
            var item = new Label().setText(value == null ? Component.empty()
                    : Component.translatable("originsx.hud.value." + value));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        selector.setOnValueChanged(v -> onChange.accept(!"false".equals(v)));
        return selector;
    }

    private static UIElement anchorSelector(String initial, java.util.function.Consumer<String> onChange) {
        var selector = new Selector<String>();
        selector.layout(l -> l.flex(1).height(18));
        selector.setCandidates(java.util.List.of(ANCHORS));
        selector.setSelected(initial, false);
        selector.setCandidateUIProvider(value -> {
            var item = new Label().setText(value == null ? Component.empty()
                    : Component.translatable("originsx.hud.anchor." + value));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        selector.setOnValueChanged(v -> {
            if (v != null) {
                onChange.accept(v);
            }
        });
        return selector;
    }

    private static TextField intField(int initial, int min, int max, java.util.function.Consumer<Integer> onChange) {
        var field = new TextField();
        field.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        field.layout(l -> l.flex(1).height(18));
        field.setNumbersOnlyInt(min, max);
        field.setText(String.valueOf(initial));
        field.setTextResponder(v -> {
            try {
                onChange.accept(Integer.parseInt(v.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        return field;
    }

    private static TextField colorField(String initial, java.util.function.Consumer<String> onChange) {
        var field = new TextField();
        field.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        field.layout(l -> l.flex(1).height(18));
        field.textFieldStyle(s -> s.placeholder(Component.literal("FF55CCFF")));
        field.setText(initial);
        field.setTextResponder(onChange::accept);
        return field;
    }

    private static TextField pathField(String initial, java.util.function.Consumer<String> onChange) {
        var field = new TextField();
        field.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        field.layout(l -> l.flex(1).height(18));
        field.textFieldStyle(s -> s.placeholder(
                Component.translatable("originsx.hud.field.texture.hint")));
        field.setText(initial);
        field.setTextResponder(onChange::accept);
        return field;
    }

    private static UIElement actionsRow(HudConfig working) {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6));
        Button save = new Button();
        save.layout(l -> l.flex(1).height(20));
        save.setText("originsx.hud.btn.save");
        save.setOnClick(e -> {
            HudConfig.save(working);
            Minecraft.getInstance().setScreenAndShow(null);
        });
        Button reset = new Button();
        reset.layout(l -> l.width(90).height(20));
        reset.setText("originsx.hud.btn.reset");
        reset.setOnClick(e -> {
            HudConfig.save(new HudConfig());
            Minecraft.getInstance().setScreenAndShow(create());
        });
        row.addChildren(save, reset);
        return row;
    }
}
