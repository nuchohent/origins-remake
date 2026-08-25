package dev.originsx.client.gui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.originsx.client.OriginsXClient;
import dev.raceapi.client.SelectedRaceClient;
import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class RaceInfoHUD {

    private static boolean hudOpen = false;

    private RaceInfoHUD() {
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (hudOpen) {
            mc.setScreenAndShow(null);
            return;
        }
        Race race = SelectedRaceClient.getRace();
        if (race == null) return;
        try {
            mc.setScreenAndShow(create(race));
            hudOpen = true;
        } catch (Exception e) {
            hudOpen = false;
            throw e;
        }
    }

    private static final class InfoScreen extends ModularUIScreen {
        private InfoScreen(ModularUI ui, Component title) {
            super(ui, title);
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
            if (OriginsXClient.OPEN_RACE_HUD.matches(event)) {
                this.onClose();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public void removed() {
            hudOpen = false;
            super.removed();
        }
    }

    private static ModularUIScreen create(Race race) {
        var root = new UIElement();
        root.layout(l -> l.width(260).heightPercent(100).flexDirection(FlexDirection.COLUMN)
                .gapAll(4).paddingAll(10).alignItems(AlignItems.STRETCH))
                .style(s -> s.background(new ColorRectTexture(0xF014141A)));

        var scroller = new com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView();
        scroller.layout(l -> l.flex(1).widthPercent(100));
        scroller.viewContainer(view -> {
            view.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(4).widthPercent(100));

            var header = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(8)
                    .alignItems(AlignItems.CENTER));
            header.addChild(new UIElement().layout(l -> l.width(36).height(36))
                    .style(s -> s.backgroundTexture(new ItemStackTexture(race.getIcon()))));
            var nameCol = new UIElement().layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN).gapAll(2));
            nameCol.addChild(new Label().setText(race.getDisplayName())
                    .textStyle(style -> style.fontSize(16).textColor(UiPalette.ACCENT_BRIGHT)));
            header.addChild(nameCol);
            view.addChild(header);

            view.addChild(new UIElement().layout(l -> l.widthPercent(100).height(1))
                    .style(s -> s.background(new ColorRectTexture(UiPalette.ACCENT_BRIGHT))));

            view.addChild(new DifficultyBar(race.getDifficulty()));

            view.addChild(new Label().setText(race.getDescription())
                    .textStyle(style -> style.fontSize(9)
                            .textWrap(TextWrap.WRAP).adaptiveHeight(true)
                            .textColor(0xFFDDDDDD)));

            view.addChild(new UIElement().layout(l -> l.widthPercent(100).height(1))
                    .style(s -> s.background(new ColorRectTexture(UiPalette.SEPARATOR))));

            List<Power> active = new ArrayList<>();
            List<Power> passive = new ArrayList<>();
            for (Power power : race.getPowers()) {
                if (power.isHidden()) continue;
                if (power.hasBinding()) {
                    active.add(power);
                } else {
                    passive.add(power);
                }
            }

            if (!active.isEmpty()) {
                view.addChild(new Label().setText("originsx.gui.powers.active")
                        .textStyle(style -> style.fontSize(9).textColor(UiPalette.ACCENT_BRIGHT)));
                for (Power power : active) {
                    view.addChild(activePowerRow(race, power));
                }
            }
            if (!passive.isEmpty()) {
                view.addChild(new Label().setText("originsx.gui.powers.passive")
                        .textStyle(style -> style.fontSize(9).textColor(UiPalette.ACCENT_BRIGHT)));
                for (Power power : passive) {
                    view.addChild(passivePowerRow(power));
                }
            }
        });

        root.addChild(scroller);

        var footer = new Label().setText("originsx.hud.close");
        footer.textStyle(style -> style.fontSize(8).textColor(UiPalette.TEXT_HINT));
        root.addChild(footer);

        var ui = UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP));
        return new InfoScreen(new ModularUI(ui), Component.translatable("originsx.hud.title"));
    }

    private static UIElement activePowerRow(Race race, Power power) {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(1)
                .paddingLeft(6));
        int slot = OriginsXClient.boundSlot(race, power);
        Component keyLabel = slot >= 0
                ? Component.literal("[").withStyle(ChatFormatting.AQUA)
                        .append(OriginsXClient.slotKeyComponent(slot))
                        .append("]").withStyle(ChatFormatting.AQUA)
                : Component.empty();
        Component nameText = keyLabel.copy().append(" ").append(power.getDisplayName());
        row.addChild(new Label().setText(nameText)
                .textStyle(style -> style.fontSize(9).textColor(UiPalette.ACCENT_BRIGHT)
                        .textWrap(TextWrap.WRAP).adaptiveHeight(true)));
        if (power.getDescription() != null) {
            row.addChild(new Label().setText(power.getDescription())
                    .textStyle(style -> style.fontSize(8).textColor(0xFF999999)
                            .textWrap(TextWrap.WRAP).adaptiveHeight(true)));
        }
        return row;
    }

    private static UIElement passivePowerRow(Power power) {
        // same character heuristic as the creator's power picker / balance
        // meter: type weight plus manual difficulty; negative = buff
        int score = RaceCreatorPanel.powerWeight(power) + power.getDifficulty();
        Component prefix;
        int nameColor;
        if (score < 0) {
            prefix = Component.literal("+ ").withStyle(ChatFormatting.GREEN);
            nameColor = 0xFF55FF55;
        } else if (score > 0) {
            prefix = Component.literal("- ").withStyle(ChatFormatting.RED);
            nameColor = 0xFFFF5555;
        } else {
            prefix = Component.empty();
            nameColor = UiPalette.TEXT;
        }
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(1)
                .paddingLeft(6));
        row.addChild(new Label().setText(prefix.copy().append(power.getDisplayName()))
                .textStyle(style -> style.fontSize(9)
                        .textColor(nameColor)
                        .textWrap(TextWrap.WRAP)
                        .adaptiveHeight(true)));
        if (power.getDescription() != null) {
            row.addChild(new Label().setText(power.getDescription())
                    .textStyle(style -> style.fontSize(8).textColor(0xFF999999)
                            .textWrap(TextWrap.WRAP).adaptiveHeight(true)));
        }
        return row;
    }
}
