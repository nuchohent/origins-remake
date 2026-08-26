package dev.originsx.looks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Appearance editor for one custom race: attach items to player-model bones,
 * tune pos/rot/scale per entry and see every change live on your own player
 * (F5) without saving. Save writes the {@code cosmetics} array back into the
 * race's JSON file in the world datapacks and reloads.
 */
@OnlyIn(Dist.CLIENT)
public final class LooksScreen extends ModularUIScreen {

    /** Palette matching OriginsX 1.4.0 (dark blue-tinted surfaces). */
    private static final int PANEL_BG = 0xFF22222A;
    private static final int ROW_BG = 0xFF2E2E38;
    private static final int ROW_SELECTED = 0xFF7A5A20;
    private static final int LINE_LOCKED = 0xFF666666;

    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();

    /** Passes the root element from the super() call into the constructor body. */
    private static final ThreadLocal<UIElement> ROOT_HOLDER = new ThreadLocal<>();

    private final String raceId;
    private final List<Cosmetics.Entry> entries = new ArrayList<>();
    private Integer selected;

    private Label raceLabel;
    private Label countLabel;
    private ScrollerView listScroller;
    private UIElement detailGroup;
    private Label emptyDetailHint;
    private RegistryPicker fItem;
    private Selector<String> fPart;
    private TextField fPx;
    private TextField fPy;
    private TextField fPz;
    private TextField fRx;
    private TextField fRy;
    private TextField fRz;
    private TextField fScale;
    private boolean loadingFields;

    // toast: transient on-screen notification (chat is not used on purpose)
    private UIElement toastHost;
    private Label toastLabel;
    private long toastHideAtMs;
    private boolean toastVisible;

    public LooksScreen(String raceId) {
        super(new ModularUI(UI.of(createRoot(),
                com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager.INSTANCE
                        .getStylesheetSafe(com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager.GDP))),
                Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".title"));
        this.raceId = raceId == null ? "" : raceId;
        loadExisting();
        UIElement root = ROOT_HOLDER.get();
        ROOT_HOLDER.remove();
        populateRoot(root);
        rebuildList();
        fillFields();
    }

    private static UIElement createRoot() {
        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN)
                .gapAll(4).paddingAll(6).alignItems(AlignItems.STRETCH));
        ROOT_HOLDER.set(root);
        return root;
    }

    @Override
    public void tick() {
        super.tick();
        if (toastVisible && System.currentTimeMillis() > toastHideAtMs) {
            toastVisible = false;
            toastHost.setDisplay(false);
        }
    }

    private void showToast(String key) {
        if (toastHost == null) {
            return;
        }
        toastLabel.setText(Component.translatable(key));
        toastHost.setDisplay(true);
        toastVisible = true;
        toastHideAtMs = System.currentTimeMillis() + 3000;
    }

    // ------------------------------------------------------------------
    //  Data
    // ------------------------------------------------------------------

    private void loadExisting() {
        Identifier id = Identifier.tryParse(raceId);
        var race = id == null ? null : dev.raceapi.race.RaceRegistry.getOrNull(id);
        if (race != null) {
            entries.addAll(LooksClient.ofRace(race));
        }
    }

    private Cosmetics.Entry selectedEntry() {
        return selected == null || selected < 0 || selected >= entries.size()
                ? null : entries.get(selected);
    }

    private void publishPreview() {
        LooksClient.setPreview(raceId, entries);
    }

    // ------------------------------------------------------------------
    //  UI construction
    // ------------------------------------------------------------------

    private void populateRoot(UIElement root) {
        root.addChild(header());
        root.addChild(separator());

        // cosmetics list
        var listPanel = new UIElement().layout(l -> l.flex(1).widthPercent(100));
        listPanel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        listScroller = new ScrollerView();
        listScroller.layout(l -> l.flex(1).widthPercent(100));
        listScroller.viewContainer(view -> view.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)));
        listPanel.addChild(listScroller);
        root.addChild(listPanel);

        root.addChild(buildEditor());

        // transient toast overlay (top strip), always on top of screen content
        toastHost = new UIElement().layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).height(60)
                .alignItems(AlignItems.CENTER).paddingAll(8));
        toastHost.setDisplay(false);
        toastLabel = new Label();
        toastLabel.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.done"));
        toastLabel.textStyle(style -> style.fontSize(10).textColor(0xFFDDDDDD).adaptiveWidth(true));
        toastLabel.layout(l -> l.paddingAll(8));
        toastLabel.style(s -> s.background(new ColorRectTexture(0xF022222A)));
        toastHost.addChild(toastLabel);
        root.addChild(toastHost);
    }

    private UIElement header() {
        var headerRow = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .gapAll(6).alignItems(AlignItems.CENTER));
        Label title = new Label();
        title.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".title"));
        title.textStyle(style -> style.fontSize(14));
        title.layout(l -> l.widthAuto());
        headerRow.addChild(title);

        raceLabel = new Label();
        raceLabel.textStyle(style -> style.fontSize(11)
                .textColor(0xFF55CCFF).adaptiveWidth(true));
        raceLabel.setText(LooksClient.raceName(raceId));
        headerRow.addChild(raceLabel);

        countLabel = new Label();
        countLabel.textStyle(style -> style.fontSize(9).textColor(0xFF9A9AA0));
        countLabel.layout(l -> l.flex(1));
        headerRow.addChild(countLabel);

        Button closeBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".close",
                this::closeWithoutSaving);
        closeBtn.layout(l -> l.width(52).height(15));
        headerRow.addChild(closeBtn);
        updateCountLabel();
        return headerRow;
    }

    private UIElement buildEditor() {
        var panel = new UIElement().layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(3).paddingAll(4)
                .alignItems(AlignItems.STRETCH));
        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));

        emptyDetailHint = adaptiveLabel(9);
        emptyDetailHint.setText(Component.translatable(
                "gui." + dev.originsx.looks.LooksMod.MOD_ID + ".pick_hint"));
        panel.addChild(emptyDetailHint);

        detailGroup = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));

        // item + body part
        var r1 = row();
        var itemCol = new UIElement().layout(l -> l.flex(1)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)
                .alignItems(AlignItems.STRETCH));
        itemCol.addChild(fieldLabel("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.item"));
        fItem = new RegistryPicker(RegistryPicker.Kind.ITEM);
        fItem.layout(l -> l.widthPercent(100).height(18));
        fItem.setOnValueChanged(v -> updateSelected(stackFromId(v), null, null, null, Float.NaN));
        itemCol.addChild(fItem);
        r1.addChild(itemCol);

        var partCol = new UIElement().layout(l -> l.flex(1)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)
                .alignItems(AlignItems.STRETCH));
        partCol.addChild(fieldLabel("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.part"));
        fPart = new Selector<>();
        fPart.layout(l -> l.widthPercent(100).height(18));
        List<String> parts = new ArrayList<>();
        for (Cosmetics.Part part : Cosmetics.Part.values()) {
            parts.add(part.jsonName);
        }
        fPart.setCandidates(parts);
        fPart.setCandidateUIProvider(value -> {
            var item = new Label().setText(value == null ? Component.empty()
                    : Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".part." + value));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        fPart.setOnValueChanged(v -> {
            if (!loadingFields && v != null) {
                Cosmetics.Part part = Cosmetics.Part.byName(v);
                if (part != null) {
                    updateSelected(null, part, null, null, Float.NaN);
                }
            }
        });
        partCol.addChild(fPart);
        r1.addChild(partCol);
        detailGroup.addChild(r1);

        // position + rotation + scale
        fPx = numberField("pos.x", 0);
        fPy = numberField("pos.y", 0);
        fPz = numberField("pos.z", 0);
        fRx = numberField("rot.x", 0);
        fRy = numberField("rot.y", 0);
        fRz = numberField("rot.z", 0);
        fScale = numberField("scale", 1);

        var rPos = row();
        rPos.addChild(fieldLabelFixed("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.pos"));
        rPos.addChild(labeledFieldFlex(fPx));
        rPos.addChild(labeledFieldFlex(fPy));
        rPos.addChild(labeledFieldFlex(fPz));
        detailGroup.addChild(rPos);

        var rRot = row();
        rRot.addChild(fieldLabelFixed("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.rot"));
        rRot.addChild(labeledFieldFlex(fRx));
        rRot.addChild(labeledFieldFlex(fRy));
        rRot.addChild(labeledFieldFlex(fRz));
        detailGroup.addChild(rRot);

        var rScale = row();
        rScale.addChild(fieldLabelFixed("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.scale"));
        rScale.addChild(labeledFieldFlex(fScale));
        detailGroup.addChild(rScale);

        var actions = row();
        Button addBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".add",
                this::addEntry);
        addBtn.layout(l -> l.width(56).height(15));
        actions.addChild(addBtn);
        Button deleteBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".delete",
                this::deleteSelected);
        deleteBtn.layout(l -> l.width(56).height(15));
        actions.addChild(deleteBtn);
        Button saveBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save",
                this::saveCosmetics);
        saveBtn.layout(l -> l.width(62).height(15));
        actions.addChild(saveBtn);

        Label hint = new Label();
        hint.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".edit_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        hint.textStyle(style -> style.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));

        // actions live OUTSIDE detailGroup: with an empty list nothing is
        // selected, and an add button hidden behind "has selection" is a
        // deadlock — the row must stay visible at all times
        panel.addChild(detailGroup);
        panel.addChild(actions);
        panel.addChild(hint);
        return panel;
    }

    private TextField numberField(String key, float initial) {
        var tf = new TextField();
        tf.layout(l -> l.height(14));
        tf.textFieldStyle(s -> s.placeholder(Component.literal(
                String.valueOf(initial))));
        tf.setTextResponder(v -> {
            if (!loadingFields) {
                applyNumberField(key, v);
            }
        });
        return tf;
    }

    private static ItemStack stackFromId(String id) {
        Identifier parsed = Identifier.tryParse(id == null ? "" : id);
        if (parsed == null) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.getValue(parsed);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    // ------------------------------------------------------------------
    //  Widgets helpers (SkillTreeScreen-style)
    // ------------------------------------------------------------------

    private static UIElement row() {
        var rowEl = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4)
                        .alignItems(AlignItems.CENTER));
        return rowEl;
    }

    private static UIElement separator() {
        var sep = new UIElement();
        sep.layout(l -> l.widthPercent(100).height(1));
        sep.style(s -> s.background(new ColorRectTexture(LINE_LOCKED)));
        return sep;
    }

    private static Label fieldLabel(String key) {
        var lb = new Label();
        lb.setText(Component.translatable(key));
        lb.textStyle(s -> s.fontSize(7).textColor(0xFFAAAAAA));
        return lb;
    }

    private static Label fieldLabelFixed(String key) {
        var lb = fieldLabel(key);
        lb.layout(l -> l.width(30));
        return lb;
    }

    private static UIElement labeledFieldFlex(TextField tf) {
        var col = new UIElement().layout(l -> l.flex(1).minWidth(1)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)
                .alignItems(AlignItems.STRETCH));
        col.addChild(tf);
        return col;
    }

    private static Label adaptiveLabel(int size) {
        Label label = new Label();
        label.textStyle(style -> style.fontSize(size).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        return label;
    }

    private static Button smallButton(String key, Runnable action) {
        Button button = new Button();
        button.setText(key);
        button.textStyle(s -> s.fontSize(9));
        button.setOnClick(e -> action.run());
        return button;
    }

    // ------------------------------------------------------------------
    //  List rendering
    // ------------------------------------------------------------------

    private void updateCountLabel() {
        countLabel.setText(Component.translatable(
                "gui." + dev.originsx.looks.LooksMod.MOD_ID + ".count", entries.size()));
    }

    private void rebuildList() {
        listScroller.clearAllScrollViewChildren();
        updateCountLabel();
        listScroller.viewContainer(view -> view.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)));
        if (entries.isEmpty()) {
            Label empty = new Label();
            empty.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".empty"));
            empty.textStyle(style -> style.fontSize(10).textColor(0xFF777777));
            empty.layout(l -> l.widthPercent(100).paddingAll(8));
            listScroller.addScrollViewChild(empty);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            listScroller.addScrollViewChild(entryRow(i));
        }
    }

    private UIElement entryRow(int index) {
        Cosmetics.Entry entry = entries.get(index);
        boolean isSelected = selected != null && selected == index;
        var rowEl = new UIElement()
                .layout(l -> l.widthPercent(100).height(24).flexDirection(FlexDirection.ROW)
                        .gapAll(6).paddingAll(3).alignItems(AlignItems.CENTER))
                .style(s -> s.background(new ColorRectTexture(
                        isSelected ? ROW_SELECTED : ROW_BG)));

        UIElement icon = new UIElement().layout(l -> l.width(18).height(18));
        icon.style(s -> s.backgroundTexture(new ItemStackTexture(entry.stack())));
        rowEl.addChild(icon);

        var textCol = new UIElement().layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN));
        Label name = new Label();
        name.setText(Component.translatable(entry.part().translationKey()));
        name.textStyle(style -> style.fontSize(9)
                .textColor(isSelected ? 0xFFFFFFFF : 0xFFDDDDDD));
        textCol.addChild(name);
        Label summary = new Label();
        summary.setText(Component.literal(summaryOf(entry)));
        summary.textStyle(style -> style.fontSize(7).textColor(0xFF9A9AA0));
        textCol.addChild(summary);
        rowEl.addChild(textCol);

        Button remove = new Button();
        remove.setText("x");
        remove.textStyle(s -> s.fontSize(8).textColor(0xFFFF5555));
        remove.layout(l -> l.width(14).height(14));
        remove.setOnClick(e -> {
            entries.remove(index);
            if (selected != null) {
                if (selected >= entries.size()) {
                    selected = entries.size() - 1;
                }
                if (selected < 0) {
                    selected = null;
                }
            }
            publishPreview();
            rebuildList();
            fillFields();
        });
        remove.style(s -> s.tooltips(Component.translatable(
                "gui." + dev.originsx.looks.LooksMod.MOD_ID + ".delete")));
        rowEl.addChild(remove);

        rowEl.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                selected = index;
                rebuildList();
                fillFields();
            }
        });
        return rowEl;
    }

    private static String summaryOf(Cosmetics.Entry entry) {
        String itemId = BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString();
        return String.format("%s  [%.2f %.2f %.2f] x%.2f", itemId,
                entry.pos()[0], entry.pos()[1], entry.pos()[2], entry.scale());
    }

    // ------------------------------------------------------------------
    //  Editing
    // ------------------------------------------------------------------

    private void addEntry() {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "end_rod")));
        entries.add(new Cosmetics.Entry(Cosmetics.Part.HEAD, stack,
                new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 0f}, 1.0f));
        selected = entries.size() - 1;
        publishPreview();
        rebuildList();
        fillFields();
    }

    private void deleteSelected() {
        if (selected == null) {
            return;
        }
        entries.remove((int) selected);
        selected = entries.isEmpty() ? null
                : Math.min(selected, entries.size() - 1);
        publishPreview();
        rebuildList();
        fillFields();
    }

    /**
     * Partially updates the selected entry; null/NaN arguments keep the old
     * value. Repaints the list row summaries and refreshes the live preview.
     */
    private void updateSelected(ItemStack stack, Cosmetics.Part part,
                                float[] pos, float[] rot, Float scale) {
        Cosmetics.Entry old = selectedEntry();
        if (old == null) {
            return;
        }
        entries.set(selected, new Cosmetics.Entry(
                part != null ? part : old.part(),
                stack != null ? stack : old.stack(),
                pos != null ? pos : old.pos(),
                rot != null ? rot : old.rot(),
                Float.isNaN(scale) ? old.scale() : scale));
        publishPreview();
    }

    private void applyNumberField(String key, String value) {
        Cosmetics.Entry old = selectedEntry();
        if (old == null) {
            return;
        }
        switch (key) {
            case "pos.x" -> updateSelected(null, null,
                    vec(old.pos(), 0, value), null, Float.NaN);
            case "pos.y" -> updateSelected(null, null,
                    vec(old.pos(), 1, value), null, Float.NaN);
            case "pos.z" -> updateSelected(null, null,
                    vec(old.pos(), 2, value), null, Float.NaN);
            case "rot.x" -> updateSelected(null, null, null,
                    vec(old.rot(), 0, value), Float.NaN);
            case "rot.y" -> updateSelected(null, null, null,
                    vec(old.rot(), 1, value), Float.NaN);
            case "rot.z" -> updateSelected(null, null, null,
                    vec(old.rot(), 2, value), Float.NaN);
            case "scale" -> {
                try {
                    updateSelected(null, null, null, null,
                            Float.parseFloat(value.trim()));
                } catch (NumberFormatException ignored) {
                    // keep last valid scale until the text parses again
                }
            }
            default -> {
            }
        }
    }

    private static float[] vec(float[] source, int index, String value) {
        float[] out = new float[]{source[0], source[1], source[2]};
        try {
            out[index] = Float.parseFloat(value.trim());
        } catch (NumberFormatException ignored) {
            // keep last valid component
        }
        return out;
    }

    private void fillFields() {
        loadingFields = true;
        try {
            Cosmetics.Entry entry = selectedEntry();
            boolean has = entry != null;
            detailGroup.setDisplay(has);
            emptyDetailHint.setDisplay(!has);
            if (!has) {
                return;
            }
            fItem.setValue(BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString(), false);
            fPart.setSelected(entry.part().jsonName, false);
            fPx.setText(fmt(entry.pos()[0]));
            fPy.setText(fmt(entry.pos()[1]));
            fPz.setText(fmt(entry.pos()[2]));
            fRx.setText(fmt(entry.rot()[0]));
            fRy.setText(fmt(entry.rot()[1]));
            fRz.setText(fmt(entry.rot()[2]));
            fScale.setText(fmt(entry.scale()));
        } finally {
            loadingFields = false;
        }
    }

    private static String fmt(float value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value) : String.valueOf(value);
    }

    private void closeWithoutSaving() {
        LooksClient.clearPreviewFor(raceId);
        Minecraft.getInstance().setScreenAndShow(null);
    }

    // ------------------------------------------------------------------
    //  Save into the world datapack
    // ------------------------------------------------------------------

    private void saveCosmetics() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.singleplayer_only");
            return;
        }
        Identifier id = Identifier.tryParse(raceId);
        if (id == null) {
            showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.failed");
            return;
        }
        Path raceFile = findRaceFile(server, id);
        if (raceFile == null) {
            showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.not_found");
            return;
        }
        try {
            JsonObject root;
            try (var reader = Files.newBufferedReader(raceFile, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (entries.isEmpty()) {
                root.remove("cosmetics");
            } else {
                root.add("cosmetics", Cosmetics.toJson(entries));
            }
            Files.writeString(raceFile, PRETTY.toJson(root), StandardCharsets.UTF_8);
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), "reload"));
            // saved data takes over again once the sync lands
            LooksClient.clearPreviewFor(raceId);
            showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.done");
        } catch (Exception e) {
            dev.originsx.looks.LooksMod.LOGGER.error("Failed to save cosmetics for {}", raceId, e);
            showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.failed");
        }
    }

    /**
     * Locates the race definition inside ANY world datapack
     * ({@code datapacks/&lt;pack&gt;/data/&lt;ns&gt;/raceapi/races/&lt;path&gt;.json}) — custom races
     * may live in imported_races or any other pack.
     */
    private static Path findRaceFile(MinecraftServer server, Identifier raceId) {
        Path root = server.getWorldPath(LevelResource.DATAPACK_DIR);
        Path relative = Path.of("data", raceId.getNamespace(), "raceapi",
                "races", raceId.getPath() + ".json");
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.endsWith(relative))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
