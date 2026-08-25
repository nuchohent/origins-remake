package dev.originsx.client.gui;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.originsx.client.OriginsXClient;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@OnlyIn(Dist.CLIENT)
public final class RaceCreatorScreen {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RaceCreatorScreen.class);

    private RaceCreatorScreen() {
    }

    public static ModularUIScreen create() {
        return buildScreen(null, null, null);
    }

    public static ModularUIScreen createForEdit(String id, JsonObject data, Path packDir) {
        return buildScreen(id, data, packDir);
    }

    private static ModularUIScreen buildScreen(@Nullable String editId,
                                               @Nullable JsonObject editData,
                                               @Nullable Path editPackDir) {
        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN)
                .gapAll(4).paddingAll(6).alignItems(AlignItems.STRETCH));
        root.style(s -> s.background(new ColorRectTexture(UiPalette.SURFACE_BG)));

        RaceCreatorPanel creatorPanel = new RaceCreatorPanel();
        // the editor's "back" button re-opens THIS screen instance so the
        // unsaved draft stays intact
        ModularUIScreen[] screenRef = new ModularUIScreen[1];
        root.addChild(header(creatorPanel, () -> {
            if (screenRef[0] != null) {
                Minecraft.getInstance().setScreenAndShow(screenRef[0]);
            }
        }));
        UIElement creatorContent = creatorPanel.build();

        UIElement[] guideContent = new UIElement[1];
        GuidePanel guidePanel = new GuidePanel(() -> {
            guideContent[0].setDisplay(false);
            creatorContent.setDisplay(true);
        });
        guideContent[0] = guidePanel.build();
        guideContent[0].setDisplay(false);

        creatorPanel.setOnOpenGuide(() -> {
            creatorContent.setDisplay(false);
            guideContent[0].setDisplay(true);
        });

        var body = new UIElement().layout(l -> l.flex(1).widthPercent(100).minHeight(1));
        body.addChildren(creatorContent, guideContent[0]);
        root.addChild(body);


        if (editId != null && editData != null && editPackDir != null) {
            creatorPanel.load(editId, editData, editPackDir);
        }

        var ui = UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP));
        ModularUIScreen screen = new ModularUIScreen(new ModularUI(ui),
                Component.translatable("originsx.screen.creator.title"));
        screenRef[0] = screen;
        return screen;
    }

    private static UIElement header(RaceCreatorPanel creatorPanel, Runnable returnToCreator) {
        var header = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(10)
                .alignItems(AlignItems.CENTER).paddingAll(4));
        header.addChild(new UIElement().layout(l -> l.width(28).height(28))
                .style(s -> s.backgroundTexture(new ItemStackTexture(new ItemStack(Items.CRAFTING_TABLE)))));
        var titleCol = new UIElement().layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN));
        titleCol.addChild(new Label().setText("originsx.screen.creator.title")
                .textStyle(style -> style.fontSize(16).textColor(UiPalette.ACCENT_BRIGHT)));
        header.addChild(titleCol);

        var back = new Button();
        back.setText("originsx.gui.back").layout(l -> l.height(20));
        back.textStyle(s -> s.fontSize(10));
        back.setOnClick(e -> OriginsXClient.openSelectionScreen());
        header.addChild(back);

        var skillTreeButton = new Button();
        skillTreeButton.setText("gui.originsx_skilltree.open_tree").layout(l -> l.height(20));
        skillTreeButton.textStyle(s -> s.fontSize(10));
        skillTreeButton.setOnClick(e -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            if (!net.neoforged.fml.ModList.get().isLoaded("originsx_skilltree")) {
                player.sendSystemMessage(
                        Component.translatable("gui.originsx_skilltree.no_addon"));
                return;
            }
            try {
                // both mods share the game-content classloader, so the caller's
                // own loader resolves the addon class; an explicit FML container
                // loader here would be the boot-layer one and fail with CNFE
                Class<?> client = Class.forName("dev.originsx.skilltree.client.SkillTreeClient");
                try {
                    client.getMethod("openEditor", String.class, String.class, Runnable.class)
                            .invoke(null, creatorPanel.currentRaceId(),
                                    creatorPanel.currentPowersJson(), returnToCreator);
                } catch (NoSuchMethodException legacy) {
                    // older addon without the return callback
                    client.getMethod("openEditor", String.class, String.class)
                            .invoke(null, creatorPanel.currentRaceId(),
                                    creatorPanel.currentPowersJson());
                }
            } catch (Throwable t) {
                Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite
                        ? ite.getCause() : t;
                log.error("Failed to open skill tree editor", cause);
                player.sendSystemMessage(Component.literal(
                        "Skill tree error: " + cause).withStyle(net.minecraft.ChatFormatting.RED));
            }
        });
        header.addChild(skillTreeButton);

        return header;
    }
}