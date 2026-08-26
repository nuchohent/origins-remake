package dev.originsx.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.originsx.client.OriginsXClient;
import dev.originsx.network.SelectRacePayload;
import dev.raceapi.client.SelectedRaceClient;
import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
import dev.raceapi.race.RaceRegistry;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
public final class RaceSelectionScreen {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RaceSelectionScreen.class);

    private static final Random RANDOM = new Random();

    private RaceSelectionScreen() {
    }

    public static ModularUIScreen create() {
        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN)
                .gapAll(4).paddingAll(6).alignItems(AlignItems.STRETCH));

        root.addChild(header());

        var body = new UIElement().layout(l -> l.flex(1).widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6));
        RaceListHolder holder = raceList();
        var detail = detailPanel(holder);
        body.addChildren(holder.wrapper, detail);
        root.addChild(body);


        var ui = UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP));
        return new ModularUIScreen(new ModularUI(ui), Component.translatable("originsx.screen.title"));
    }

    // ── Header ──────────────────────────────────────────────────────

    private static UIElement header() {
        var header = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(10)
                .alignItems(AlignItems.CENTER).paddingAll(4));

        header.addChild(new UIElement().layout(l -> l.width(28).height(28))
                .style(s -> s.backgroundTexture(new ItemStackTexture(new ItemStack(Items.ENDER_EYE)))));

        var titleCol = new UIElement().layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN));
        titleCol.addChild(new Label().setText("originsx.screen.title")
                .textStyle(style -> style.fontSize(16)));
        header.addChild(titleCol);

        if (OriginsXClient.isCheatsEnabled()) {
            var createBtn = new Button();
            createBtn.setText("originsx.gui.create_race").layout(l -> l.height(20));
            createBtn.textStyle(s -> s.fontSize(10));
            createBtn.setOnClick(e -> OriginsXClient.openCreatorScreen());
            header.addChild(createBtn);

            var importBtn = new Button();
            importBtn.setText("originsx.gui.import_race").layout(l -> l.height(20));
            importBtn.textStyle(s -> s.fontSize(10));
            importBtn.setOnClick(e -> Minecraft.getInstance().setScreenAndShow(importScreen()));
            header.addChild(importBtn);
        }

        // HUD editor (mana bar + cooldown indicators) — client-side config,
        // available to every player
        var hudBtn = new Button();
        hudBtn.setText("key.originsx.hud_settings").layout(l -> l.height(20));
        hudBtn.textStyle(s -> s.fontSize(10));
        hudBtn.setOnClick(e -> Minecraft.getInstance()
                .setScreenAndShow(HudSettingsScreen.create()));
        header.addChild(hudBtn);

        return header;
    }

    // ── Left panel: race list ───────────────────────────────────────

    private static RaceListHolder raceList() {
        RaceListHolder holder = new RaceListHolder();
        holder.wrapper = new UIElement().layout(l -> l.widthPercent(32).flexDirection(FlexDirection.COLUMN)
                .gapAll(4).heightPercent(100));

        var searchField = new TextField();
        searchField.layout(l -> l.widthPercent(100).height(20));
        searchField.setText("");
        searchField.setTextResponder(v -> {
            holder.searchFilter = v.toLowerCase();
            rebuildRaceList(holder);
        });
        searchField.textFieldStyle(s -> s.placeholder(Component.translatable("originsx.gui.search")));
        holder.wrapper.addChild(searchField);

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1));
        holder.scroller = scroller;
        scroller.viewContainer(view -> {
            view.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(3).widthPercent(100));
            view.addChild(randomRaceCard(holder));
            for (Race race : RaceRegistry.playable()) {
                view.addChild(raceCard(race, holder));
            }
        });
        holder.wrapper.addChild(scroller);
        return holder;
    }

    private static void rebuildRaceList(RaceListHolder holder) {
        holder.scroller.clearAllScrollViewChildren();
        holder.scroller.viewContainer(view -> {
            view.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(3).widthPercent(100));
            view.addChild(randomRaceCard(holder));
            String filter = holder.searchFilter == null ? "" : holder.searchFilter;
            for (Race race : RaceRegistry.playable()) {
                if (filter.isEmpty() || race.getDisplayName().getString().toLowerCase().contains(filter)
                        || race.getId().toString().toLowerCase().contains(filter)) {
                    view.addChild(raceCard(race, holder));
                }
            }
        });
    }

    private static UIElement randomRaceCard(RaceListHolder holder) {
        boolean highlighted = holder.randomSelected;
        var card = new UIElement()
                .layout(l -> l.widthPercent(100).height(46).flexDirection(FlexDirection.ROW).gapAll(8).paddingAll(6)
                        .alignItems(AlignItems.CENTER))
                .style(s -> s.background(new ColorRectTexture(
                        highlighted ? 0xFF3D5C8A : 0xFF2B2B33)));
        card.addChildren(
                new UIElement().layout(l -> l.width(32).height(32))
                        .style(s -> s.backgroundTexture(new ItemStackTexture(new ItemStack(Items.NETHER_STAR)))),
                randomCardText());
        card.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                List<Race> playable = new ArrayList<>(RaceRegistry.playable());
                if (playable.isEmpty()) return;
                Race random = playable.get(RANDOM.nextInt(playable.size()));
                holder.randomSelected = true;
                holder.select(random);
            }
        });
        return card;
    }

    private static UIElement randomCardText() {
        var col = new UIElement().layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN).gapAll(2));
        col.addChild(new Label().setText(Component.translatable("originsx.race.random"))
                .textStyle(style -> style.fontSize(11).textColor(0xFFFFD700)));
        col.addChild(new Label().setText(Component.translatable("originsx.race.random.desc"))
                .textStyle(style -> style.fontSize(8).textColor(UiPalette.TEXT_DIM)));
        return col;
    }

    private static UIElement raceCard(Race race, RaceListHolder holder) {
        boolean isCurrent = race.getId().equals(SelectedRaceClient.getOrNull());
        boolean highlighted = holder.current != null && holder.current.getId().equals(race.getId());

        int bg = highlighted ? 0xFF3D5C8A : isCurrent ? 0xFF2E4460 : 0xFF2B2B33;
        var card = new UIElement()
                .layout(l -> l.widthPercent(100).height(46).flexDirection(FlexDirection.ROW).gapAll(8).paddingAll(6)
                        .alignItems(AlignItems.CENTER))
                .style(s -> s.background(new ColorRectTexture(bg)));
        card.addChildren(
                new UIElement().layout(l -> l.width(32).height(32))
                        .style(s -> s.backgroundTexture(new ItemStackTexture(race.getIcon()))),
                raceCardText(race));
        card.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                holder.randomSelected = false;
                holder.select(race);
            }
        });
        return card;
    }

    private static UIElement raceCardText(Race race) {
        var col = new UIElement().layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN).gapAll(2));
        col.addChild(new Label().setText(race.getDisplayName())
                .textStyle(style -> style.fontSize(11)
                        .textWrap(TextWrap.WRAP).adaptiveHeight(true)));
        col.addChild(new DifficultyBar(race.getDifficulty()));
        return col;
    }

    // ── Right panel: detail ─────────────────────────────────────────

    private static UIElement detailPanel(RaceListHolder holder) {
        var panel = new UIElement().layout(l -> l.flex(1).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));

        ScrollerView raceScroller = new ScrollerView();
        raceScroller.layout(l -> l.flex(1).widthPercent(100));
        raceScroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.AUTO));
        raceScroller.viewContainer(view -> view.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(4).widthPercent(100)));

        panel.addChild(raceScroller);

        holder.onSelect = race -> rebuildDetail(raceScroller, race);
        return panel;
    }

    private static void rebuildDetail(ScrollerView scroller, Race race) {
        scroller.clearAllScrollViewChildren();
        scroller.addScrollViewChild(buildDetail(race));
    }

    // ── Detail card ─────────────────────────────────────────────────

    private static UIElement buildDetail(Race race) {
        var panel = new UIElement()
                .layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(8).paddingAll(10))
                .style(s -> s.background(new ColorRectTexture(0xFF22222A)));

        // Header: icon + name
        var header = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(10)
                .alignItems(AlignItems.CENTER));
        var detailText = new UIElement().layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN).gapAll(2));
        detailText.addChild(new Label().setText(race.getDisplayName())
                .textStyle(style -> style.fontSize(16).adaptiveWidth(true)));
        header.addChildren(
                new UIElement().layout(l -> l.width(48).height(48))
                        .style(s -> s.backgroundTexture(new ItemStackTexture(race.getIcon()))),
                detailText);

        // Difficulty
        var difficultyRow = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        difficultyRow.addChildren(
                new Label().setText("originsx.gui.difficulty")
                        .textStyle(style -> style.fontSize(8).textColor(0xFF888888)),
                new DifficultyBar(race.getDifficulty())
        );

        // Description
        var description = new Label().setText(race.getDescription())
                .textStyle(style -> style.fontSize(10).textWrap(TextWrap.WRAP).adaptiveHeight(true)
                        .textColor(0xFFDDDDDD));

        // Separator
        var separator = new UIElement().layout(l -> l.widthPercent(100).height(1))
                .style(s -> s.background(new ColorRectTexture(UiPalette.SEPARATOR)));

        // Powers
        var powersCol = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));

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
            powersCol.addChild(new Label().setText("originsx.gui.powers.active")
                    .textStyle(style -> style.fontSize(9).textColor(0xFF55CCFF)));
            for (Power power : active) {
                powersCol.addChild(activePowerRow(race, power));
            }
        }
        if (!passive.isEmpty()) {
            powersCol.addChild(new Label().setText("originsx.gui.powers.passive")
                    .textStyle(style -> style.fontSize(9).textColor(0xFF55CCFF)));
            for (Power power : passive) {
                powersCol.addChild(passivePowerRow(power));
            }
        }

        // Actions: column of rows — six buttons in one row overflow the panel
        var actions = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
        var actionsRowA = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6));
        var selectButton = new Button();
        boolean isCurrentRace = race.getId().equals(SelectedRaceClient.getOrNull());
        selectButton.setText(isCurrentRace ? "originsx.gui.selected" : "originsx.gui.select")
                .layout(l -> l.flex(1).height(22));
        selectButton.textStyle(s -> s.fontSize(10));
        selectButton.setOnClick(e -> {
            ClientPacketDistributor.sendToServer(new SelectRacePayload(race.getId().toString()));
            Minecraft.getInstance().setScreenAndShow(null);
        });

        var resetButton = new Button();
        resetButton.setText("originsx.gui.reset").layout(l -> l.flex(1).height(22));
        resetButton.textStyle(s -> s.fontSize(10));
        resetButton.setOnClick(e -> {
            ClientPacketDistributor.sendToServer(new SelectRacePayload(""));
            Minecraft.getInstance().setScreenAndShow(null);
        });
        actions.addChildren(selectButton, resetButton);

        actionsRowA.addChildren(selectButton, resetButton);
        actions.addChild(actionsRowA);

        Path customFile = findCustomRaceFile(race);
        if (customFile != null) {
            var editButton = new Button();
            editButton.setText("originsx.gui.edit").layout(l -> l.flex(1).height(22));
            editButton.textStyle(s -> s.fontSize(9));
            editButton.setOnClick(e -> editRace(race, customFile));

            var exportButton = new Button();
            exportButton.setText("originsx.gui.export").layout(l -> l.flex(1).height(22));
            exportButton.textStyle(s -> s.fontSize(9));
            exportButton.setOnClick(e -> exportRace(race, customFile));

            var deleteButton = new Button();
            deleteButton.setText("originsx.gui.delete").layout(l -> l.flex(1).height(22));
            deleteButton.textStyle(s -> s.fontSize(9));
            deleteButton.setOnClick(e -> deleteRace(race, customFile));
            var actionsRowB = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6));
            actionsRowB.addChildren(editButton, exportButton, deleteButton);
            actions.addChild(actionsRowB);

            // looks editor lives in an optional addon; a dead button would be
            // worse than none
            if (net.neoforged.fml.ModList.get().isLoaded("originsx_looks")) {
                var looksButton = new Button();
                looksButton.setText("originsx.gui.looks").layout(l -> l.flex(1).height(22));
                looksButton.textStyle(s -> s.fontSize(9));
                looksButton.setOnClick(e -> openLooksEditor(race.getId().toString()));
                actions.addChild(looksButton);
            }
        }

        panel.addChildren(header, difficultyRow, description, separator, powersCol, actions);
        return panel;
    }

    // ── Custom race management (singleplayer world datapacks) ───────

    private static final Map<String, Path> CUSTOM_RACE_FILES = new HashMap<>();

    private static Path findCustomRaceFile(Race race) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return null;
        String id = race.getId().toString();
        Path root = server.getWorldPath(LevelResource.DATAPACK_DIR);
        Path cached = CUSTOM_RACE_FILES.get(id);
        if (cached != null && (!Files.exists(cached) || !cached.startsWith(root))) {
            // deleted on disk, or a stale entry pointing into another world
            CUSTOM_RACE_FILES.remove(id);
        }
        return CUSTOM_RACE_FILES.computeIfAbsent(id, key -> {
            Path relative = Path.of("data", race.getId().getNamespace(), "raceapi",
                    "races", race.getId().getPath() + ".json");
            try (Stream<Path> stream = Files.walk(root)) {
                return stream.filter(Files::isRegularFile)
                        .filter(path -> path.endsWith(relative))
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static Path packDirOf(Path file) {
        Path dir = file.getParent();
        while (dir != null && !Files.exists(dir.resolve("pack.mcmeta"))) {
            dir = dir.getParent();
        }
        return dir;
    }

    /**
     * Opens the Looks appearance editor for a custom race via reflection —
     * the addon is optional, so OriginsX must not hard-link its classes.
     */
    private static void openLooksEditor(String raceId) {
        try {
            // both mods share the game-content classloader (same pattern as
            // the skill-tree button in RaceCreatorScreen)
            Class<?> client = Class.forName("dev.originsx.looks.client.LooksClient");
            client.getMethod("openEditor", String.class).invoke(null, raceId);
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite
                    ? ite.getCause() : t;
            log.error("Failed to open looks editor", cause);
            message(Component.literal("Looks error: " + cause)
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void editRace(Race race, Path file) {
        Path packDir = packDirOf(file);
        if (packDir == null) {
            message(Component.translatable("originsx.gui.edit.failed").withStyle(ChatFormatting.RED));
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            OriginsXClient.openCreatorScreen(race.getId().toString(), root, packDir);
        } catch (IOException | com.google.gson.JsonParseException e) {
            message(Component.translatable("originsx.gui.edit.failed").withStyle(ChatFormatting.RED));
        }
    }

    private static void deleteRace(Race race, Path file) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        try {
            Files.deleteIfExists(file);
            CUSTOM_RACE_FILES.remove(race.getId().toString());
            if (SelectedRaceClient.getOrNull() != null && SelectedRaceClient.getOrNull().equals(race.getId())) {
                ClientPacketDistributor.sendToServer(new SelectRacePayload(""));
            }
            if (server != null) {
                server.execute(() -> server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), "reload"));
            }
            Minecraft.getInstance().setScreenAndShow(null);
            message(Component.translatable("originsx.gui.delete.done").withStyle(ChatFormatting.GREEN));
        } catch (IOException e) {
            message(Component.translatable("originsx.gui.delete.failed").withStyle(ChatFormatting.RED));
        }
    }

    private static void exportRace(Race race, Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String share = dev.originsx.share.RaceShare.encode(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(share);
            message(Component.translatable("originsx.gui.export.done", race.getId().toString())
                    .withStyle(ChatFormatting.GREEN));
        } catch (IOException | com.google.gson.JsonParseException e) {
            message(Component.translatable("originsx.gui.export.failed").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Paste-a-share-string dialog. A valid payload is saved into the world's
     * {@code imported_races} datapack with a fresh generated id (never
     * overwriting anything), then the server reloads.
     */
    private static ModularUIScreen importScreen() {
        var overlay = new UIElement().layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).heightPercent(100)
                .flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.CENTER));

        var panel = new UIElement().layout(l -> l.width(360).top(60)
                .flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(8));
        panel.style(s -> s.background(new ColorRectTexture(UiPalette.PANEL_BG)));
        overlay.addChild(panel);

        panel.addChild(new Label().setText("originsx.share.title")
                .textStyle(s -> s.fontSize(14)));
        var hint = new Label().setText("originsx.share.hint");
        hint.textStyle(s -> s.fontSize(9).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        panel.addChild(hint);

        // fixed compact height: a flex-sized field swallowed the whole panel
        var field = new TextField();
        field.layout(l -> l.widthPercent(100).height(44));
        field.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        panel.addChild(field);

        // LDLib text fields have no Ctrl+V handling of their own — give the
        // clipboard an explicit button
        var pasteRow = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .gapAll(6).alignItems(AlignItems.CENTER));
        var pasteBtn = new Button();
        pasteBtn.setText("originsx.share.paste").layout(l -> l.width(90).height(16));
        pasteBtn.textStyle(s -> s.fontSize(9));
        pasteBtn.setOnClick(e -> {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isBlank()) {
                field.setText(clip.trim());
            }
        });
        pasteRow.addChild(pasteBtn);
        panel.addChild(pasteRow);

        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6));
        var importBtn = new Button();
        importBtn.setText("originsx.share.import").layout(l -> l.flex(1).height(20));
        importBtn.textStyle(s -> s.fontSize(10));
        var cancelBtn = new Button();
        cancelBtn.setText("originsx.share.cancel").layout(l -> l.flex(1).height(20));
        cancelBtn.textStyle(s -> s.fontSize(10));
        row.addChildren(importBtn, cancelBtn);
        panel.addChild(row);

        importBtn.setOnClick(e -> {
            try {
                JsonObject raceJson = dev.originsx.share.RaceShare.decode(field.getText());
                MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
                if (server != null) {
                    // singleplayer: write straight into the world datapack
                    Identifier id = saveImportedRace(raceJson);
                    message(Component.translatable("originsx.share.waiting").withStyle(ChatFormatting.YELLOW));
                    waitForReloadAndOpen(id, 600);
                } else {
                    // dedicated server: ops import via serverbound packet —
                    // the server validates, writes and reloads for everyone
                    if (!OriginsXClient.isCheatsEnabled()) {
                        message(Component.translatable("originsx.share.no_permission")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                            new dev.originsx.network.ImportRacePayload(
                                    dev.originsx.share.RaceShare.encode(raceJson)));
                    message(Component.translatable("originsx.share.waiting").withStyle(ChatFormatting.YELLOW));
                    // the server reload broadcasts the race sync; reopen when it lands
                    Minecraft.getInstance().setScreenAndShow(null);
                }
            } catch (IllegalArgumentException bad) {
                message(Component.translatable(bad.getMessage()).withStyle(ChatFormatting.RED));
            } catch (IOException io) {
                if (Minecraft.getInstance().getSingleplayerServer() == null) {
                    message(Component.translatable("originsx.share.need_singleplayer").withStyle(ChatFormatting.RED));
                } else {
                    message(Component.translatable("originsx.share.failed_io").withStyle(ChatFormatting.RED));
                }
            }
        });
        cancelBtn.setOnClick(e -> Minecraft.getInstance().setScreenAndShow(create()));

        var ui = UI.of(overlay, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP));
        return new ModularUIScreen(new ModularUI(ui), Component.translatable("originsx.share.title"));
    }

    /**
     * Polls the client registry (once per frame) until the imported race shows
     * up after the async /reload, then rebuilds the list. Gives up with a
     * hint after {@code framesLeft} frames.
     */
    private static void waitForReloadAndOpen(Identifier id, int framesLeft) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (RaceRegistry.getOrNull(id) != null) {
            // switch first: the fresh screen registers its own toast host,
            // so the "done" toast is visible on top of the new list
            mc.setScreenAndShow(create());
            message(Component.translatable("originsx.share.done", id.toString())
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        if (framesLeft > 0) {
            mc.execute(() -> waitForReloadAndOpen(id, framesLeft - 1));
        } else {
            message(Component.translatable("originsx.share.reload_timeout").withStyle(ChatFormatting.RED));
        }
    }

    private static Identifier saveImportedRace(JsonObject raceJson) throws IOException {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            throw new IOException("singleplayer only");
        }
        Path packDir = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("imported_races");
        Path racesDir = packDir.resolve("data").resolve("imported")
                .resolve("raceapi").resolve("races");
        // dedupe: importing the same share string twice must not create a copy
        String canonical = raceJson.toString();
        if (Files.exists(racesDir)) {
            try (Stream<Path> stream = Files.walk(racesDir)) {
                var existing = stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .findFirst()
                        .filter(path -> {
                            try {
                                return Files.readString(path, StandardCharsets.UTF_8).equals(canonical);
                            } catch (IOException e) {
                                return false;
                            }
                        });
                if (existing.isPresent()) {
                    String fileName = existing.get().getFileName().toString();
                    return Identifier.fromNamespaceAndPath("imported",
                            fileName.substring(0, fileName.length() - ".json".length()));
                }
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("imported",
                "imported_race_" + Long.toHexString(System.currentTimeMillis())
                        + "_" + Integer.toHexString(RANDOM.nextInt(0x10000)));
        Path raceFile = racesDir.resolve(id.getPath() + ".json");
        Files.createDirectories(raceFile.getParent());
        Files.writeString(raceFile, canonical, StandardCharsets.UTF_8);
        Path meta = packDir.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, "{\n  \"pack\": {\n    \"pack_format\": 90,\n"
                    + "    \"min_format\": 82,\n    \"max_format\": 999,\n"
                    + "    \"description\": \"Imported races\"\n  }\n}\n", StandardCharsets.UTF_8);
        }
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "reload"));
        return id;
    }

    private static void message(Component text) {
        UiToaster.show(text);
    }

    // ── Power rows with description ─────────────────────────────────

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
                .textStyle(style -> style.fontSize(9).textColor(0xFFDDDDDD)
                        .textWrap(TextWrap.WRAP).adaptiveHeight(true)));
        if (power.getDescription() != null) {
            row.addChild(new Label().setText(power.getDescription())
                    .textStyle(style -> style.fontSize(8).textColor(0xFF999999)
                            .textWrap(TextWrap.WRAP).adaptiveHeight(true)));
        }
        return row;
    }

    private static UIElement passivePowerRow(Power power) {
        // same character heuristic as the creator picker / balance meter:
        // type weight plus manual difficulty; negative = buff
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

    private static class RaceListHolder {
        private UIElement wrapper;
        private ScrollerView scroller;
        private Race current;
        private boolean randomSelected;
        private String searchFilter;
        private java.util.function.Consumer<Race> onSelect;

        private void select(Race race) {
            Race fresh = RaceRegistry.getOrNull(race.getId());
            this.current = fresh != null ? fresh : race;
            if (onSelect != null) {
                onSelect.accept(this.current);
            }
        }
    }
}