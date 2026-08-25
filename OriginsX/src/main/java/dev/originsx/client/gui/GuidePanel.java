package dev.originsx.client.gui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An in-game documentation viewer for the race creator, styled like a wiki
 * index: a navigation sidebar on the left lists topics, the right side shows
 * the selected topic. The "power types" section is generated from the creator's
 * {@code PowerTypeSpec} table so it always stays in sync with the editor.
 */
@OnlyIn(Dist.CLIENT)
public final class GuidePanel {

    private final Runnable onBack;
    private ScrollerView contentScroller;
    private final List<Button> navButtons = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();

    public GuidePanel(Runnable onBack) {
        this.onBack = onBack;
    }

    public UIElement build() {
        sections.add(new Section("originsx.guide.section.start", this::buildStart));
        sections.add(new Section("originsx.guide.section.fields", this::buildFields));
        sections.add(new Section("originsx.guide.section.powers", this::buildPowers));
        sections.add(new Section("originsx.guide.section.types", this::buildTypes));
        sections.add(new Section("originsx.guide.section.conditions", this::buildConditions));
        sections.add(new Section("originsx.guide.section.save", this::buildSave));

        var root = new UIElement().layout(l -> l.flex(1).widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6));

        var sidebar = new UIElement().layout(l -> l.width(150).flexDirection(FlexDirection.COLUMN).gapAll(3).paddingAll(4))
                .style(s -> s.background(new ColorRectTexture(UiPalette.PANEL_BG)));

        var title = new Label().setText("originsx.guide.title");
        title.textStyle(s -> s.fontSize(11).adaptiveHeight(true).textColor(UiPalette.ACCENT));
        sidebar.addChild(title);

        var back = new Button();
        back.setText("originsx.guide.back");
        back.layout(l -> l.widthPercent(100).height(18));
        back.textStyle(s -> s.fontSize(8).textAlignHorizontal(Horizontal.LEFT));
        back.setOnClick(e -> onBack.run());
        sidebar.addChild(back);

        for (Section section : sections) {
            var btn = new Button();
            btn.setText(section.titleKey());
            btn.layout(l -> l.widthPercent(100).height(18));
            btn.textStyle(s -> s.fontSize(8).textAlignHorizontal(Horizontal.LEFT));
            btn.setOnClick(e -> showSection(section));
            sidebar.addChild(btn);
            navButtons.add(btn);
        }

        contentScroller = new ScrollerView();
        contentScroller.layout(l -> l.flex(1).widthPercent(100));
        contentScroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.AUTO));
        contentScroller.viewContainer(v -> v.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(4).widthPercent(100)));

        root.addChildren(sidebar, contentScroller);
        showSection(sections.get(0));
        return root;
    }

    // ---------------------------------------------------------------- nav

    private void showSection(Section section) {
        contentScroller.clearAllScrollViewChildren();
        var col = new UIElement().layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(5).widthPercent(100));
        section.builder().accept(col);
        contentScroller.addScrollViewChild(col);
        for (int i = 0; i < navButtons.size(); i++) {
            boolean active = sections.get(i) == section;
            navButtons.get(i).textStyle(s -> s.fontSize(9)
                    .textColor(active ? 0xFF55CCFF : 0xFFDDDDDD));
        }
    }

    private record Section(String titleKey, Consumer<UIElement> builder) {
    }

    // ---------------------------------------------------------------- content helpers

    private static void heading(UIElement col, String key) {
        var label = new Label().setText(key);
        label.textStyle(s -> s.fontSize(12).textWrap(TextWrap.WRAP).adaptiveHeight(true)
                .textColor(UiPalette.ACCENT));
        col.addChild(label);
    }

    private static void paragraph(UIElement col, String key) {
        var label = new Label().setText(key);
        label.layout(l -> l.widthPercent(100));
        label.textStyle(s -> s.fontSize(9).textWrap(TextWrap.WRAP).adaptiveHeight(true)
                .textColor(UiPalette.TEXT));
        col.addChild(label);
    }

    private static void bullet(UIElement col, String key) {
        var label = new Label().setText(Component.literal("• ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable(key)));
        label.layout(l -> l.widthPercent(100));
        label.textStyle(s -> s.fontSize(9).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        col.addChild(label);
    }

    private static void step(UIElement col, int number, String key) {
        var label = new Label().setText(Component.literal(number + ". ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable(key)));
        label.layout(l -> l.widthPercent(100));
        label.textStyle(s -> s.fontSize(9).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        col.addChild(label);
    }

    // ---------------------------------------------------------------- sections

    private void buildStart(UIElement col) {
        heading(col, "originsx.guide.start.title");
        step(col, 1, "originsx.guide.start.step1");
        step(col, 2, "originsx.guide.start.step2");
        step(col, 3, "originsx.guide.start.step3");
        step(col, 4, "originsx.guide.start.step4");
        step(col, 5, "originsx.guide.start.step5");
        paragraph(col, "originsx.guide.start.hint");
    }

    private void buildFields(UIElement col) {
        heading(col, "originsx.guide.fields.title");
        bullet(col, "originsx.guide.fields.name");
        bullet(col, "originsx.guide.fields.datapack");
        bullet(col, "originsx.guide.fields.id");
        bullet(col, "originsx.guide.fields.icon");
        bullet(col, "originsx.guide.fields.difficulty");
        bullet(col, "originsx.guide.fields.scale");
        bullet(col, "originsx.guide.fields.description");
    }

    private void buildPowers(UIElement col) {
        heading(col, "originsx.guide.powers.title");
        paragraph(col, "originsx.guide.powers.intro");
        bullet(col, "originsx.guide.powers.active");
        bullet(col, "originsx.guide.powers.passive");
        bullet(col, "originsx.guide.powers.difficulty");
        bullet(col, "originsx.guide.powers.conditional");
    }

    private void buildTypes(UIElement col) {
        heading(col, "originsx.guide.types.title");
        paragraph(col, "originsx.guide.types.intro");
        for (RaceCreatorPanel.GuidePower power : RaceCreatorPanel.guidePowers()) {
            var card = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(4))
                    .style(s -> s.background(new ColorRectTexture(UiPalette.PANEL_BG)));
            var name = new Label().setText(Component.translatable(power.labelKey()));
            name.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
            card.addChild(name);
            var desc = new Label().setText(Component.translatable(power.descKey()));
            desc.layout(l -> l.widthPercent(100));
            desc.textStyle(s -> s.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true));
            card.addChild(desc);
            if (!power.paramKeys().isEmpty()) {
                List<String> labels = power.paramKeys().stream()
                        .map(key -> Component.translatable(key).getString())
                        .toList();
                var params = new Label().setText(Component.literal("— ")
                        .append(String.join(", ", labels)));
                params.layout(l -> l.widthPercent(100));
                params.textStyle(s -> s.fontSize(7).textWrap(TextWrap.WRAP).adaptiveHeight(true));
                card.addChild(params);
            }
            col.addChild(card);
        }
    }

    private void buildConditions(UIElement col) {
        heading(col, "originsx.guide.conditions.title");
        paragraph(col, "originsx.guide.conditions.intro");
        for (String condition : RaceCreatorPanel.conditionTypes()) {
            bullet(col, "originsx.creator.condition." + condition);
        }
    }

    private void buildSave(UIElement col) {
        heading(col, "originsx.guide.save.title");
        bullet(col, "originsx.guide.save.datapack");
        bullet(col, "originsx.guide.save.edit");
        bullet(col, "originsx.guide.save.share");
        bullet(col, "originsx.guide.save.export");
        bullet(col, "originsx.guide.save.import");
        bullet(col, "originsx.guide.save.balance");
    }
}
