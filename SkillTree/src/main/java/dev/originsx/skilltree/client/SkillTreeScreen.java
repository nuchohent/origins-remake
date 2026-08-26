package dev.originsx.skilltree.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.originsx.skilltree.net.UnlockNodePayload;
import dev.originsx.skilltree.tree.Shards;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.joml.Vector2f;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The skill tree screen with the default LDLib2 look: item icons on nodes,
 * human-readable upgrade effects, and an edit mode for building trees for
 * custom races (singleplayer export as a datapack, like the race creator).
 */
@OnlyIn(Dist.CLIENT)
public final class SkillTreeScreen extends ModularUIScreen {

    private static final int LINE_LOCKED = 0xFF666666;
    private static final int LINE_ACTIVE = 0xFFd09020;
    /** Palette matching OriginsX 1.4.0 (dark blue-tinted surfaces). */
    private static final int PANEL_BG = 0xFF22222A;
    private static final int NODE_BG = 0xFF2E2E38;
    private static final int NODE_SELECTED = 0xFF7A5A20;

    private static final int CELL = 34;
    private static final int NODE = 26;
    /** Free-form canvas size (FTB Quests style); scrollbars pan around it. */
    private static final float WORLD_W = 1600;
    private static final float WORLD_H = 1200;

    /** Power factors per tier; mirrors TierScaler so previews match gameplay. */
    private static final double[] TIER_FACTOR = {0.0, 0.34, 0.67, 1.0};

    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();

    private static final String DEFAULT_POWER_JSON =
            "{\"type\":\"attribute\",\"operation\":\"add_value\","
                    + "\"attribute\":\"minecraft:generic.max_health\",\"amount\":2}";

    private static volatile SkillTreeScreen openScreen;

    /** Passes the root element from the super() call into the constructor body. */
    private static final ThreadLocal<UIElement> ROOT_HOLDER = new ThreadLocal<>();

    private boolean editMode;
    private JsonObject editJson;
    private List<SkillTreeClientState.ClientNode> editNodes = List.of();
    private String selectedNodeId;
    private UIElement canvasHost;
    private UIElement world;
    private UIElement lineLayer;
    private final Map<String, NodeView> views = new HashMap<>();
    /** Node id -> top-left pixel position inside the world (lines are drawn from these). */
    private final Map<String, float[]> pixelPos = new HashMap<>();

    // node dragging state
    private String draggingId;
    private final Vector2f dragStartMouse = new Vector2f();
    private float dragStartNodeX;
    private float dragStartNodeY;

    // context menu ("toolkit") opened by right click
    private UIElement contextMenu;
    /** Screen-space mouse position of the latest mouse event (for menu placement). */
    private float lastMouseX;
    private float lastMouseY;

    // detail widgets (assigned during construction)
    private Label detailTitle;
    private Label detailDesc;
    private Label infoTier;
    private Label infoEffect;
    private Label infoCost;
    private Button unlockButton;
    private Label unlockCost;
    private UIElement viewGroup;
    private UIElement editGroup;
    private TextField fId;
    private TextField fIndex;
    private TextField fX;
    private TextField fY;
    private TextField fCosts;
    private RegistryPicker fIcon;
    private TextField fName;
    private TextField fDesc;
    private TextField fRequires;
    private TextField fPower;
    private boolean loadingFields;

    public record NodeView(UIElement button, UIElement icon) {
        /** Icon fills the whole slot (centered by the item renderer); no fallback word. */
        public void paint(SkillTreeClientState.ClientNode node, int tier, boolean selected) {
            if (!node.icon().isEmpty()) {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(node.icon()));
                if (item != null) {
                    icon.style(s -> s.backgroundTexture(new ItemStackTexture(new ItemStack(item))));
                }
            }
            final int bg;
            if (tier >= 3) {
                bg = selected ? 0xFF4CAF50 : 0xFF2E7D32;   // maxed: green
            } else if (tier >= 1) {
                bg = selected ? 0xFF55CCFF : 0xFF2E4460;   // partial: blue
            } else {
                bg = selected ? NODE_SELECTED : NODE_BG;   // locked: dark / gold when picked
            }
            button.style(s -> s.background(new ColorRectTexture(bg)));
        }
    }

    /** Draft power options for the index picker: {@code [[index, name], ...]}. */
    private List<int[]> pickerIndexes = new java.util.ArrayList<>();
    private final Map<Integer, String> pickerNames = new HashMap<>();
    /** Draft power engine JSON by index, offered when picking a node power. */
    private final Map<Integer, String> pickerPowers = new HashMap<>();
    private UIElement detailRoot;
    /** Run by the "back to creator" button (set when opened from the race creator). */
    @javax.annotation.Nullable
    private Runnable editorReturn;

    public SkillTreeScreen() {
        this(null, "[]");
    }

    /** @param editorRaceId non-null → open directly in edit mode with a blank tree for that race */
    public SkillTreeScreen(String editorRaceId) {
        this(editorRaceId, "[]");
    }

    public SkillTreeScreen(String editorRaceId, String powersJson) {
        this(editorRaceId, powersJson, null);
    }

    /** @param editorReturn run by the editor's "back to creator" button */
    public SkillTreeScreen(String editorRaceId, String powersJson,
                           Runnable editorReturn) {
        super(new ModularUI(UI.of(createRoot(),
                StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP))),
                Component.translatable("gui.originsx_skilltree.title"));
        this.editorReturn = editorReturn;
        // must run before populateRoot(): buildDetailPanel attaches the index
        // picker only when the power list is already parsed
        if (editorRaceId != null && powersJson != null) {
            try {
                for (var element : JsonParser.parseString(powersJson).getAsJsonArray()) {
                    if (element.isJsonArray()) {
                        var entry = element.getAsJsonArray();
                        if (entry.size() >= 2) {
                            int idx = entry.get(0).getAsInt();
                            pickerIndexes.add(new int[]{idx});
                            pickerNames.put(idx, entry.get(1).getAsString());
                            if (entry.size() >= 3 && entry.get(2).isJsonObject()) {
                                pickerPowers.put(idx, entry.get(2).toString());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            // draft has no powers (creator opened fresh) but the race itself
            // is selected: offer the actual race powers so the picker is not
            // empty (JSON is unknown, only index + name)
            if (pickerIndexes.isEmpty()) {
                try {
                    var race = dev.raceapi.client.SelectedRaceClient.getRace();
                    if (race != null && race.getId().toString().equals(editorRaceId)) {
                        java.util.List<dev.raceapi.race.Power> racePowers = race.getPowers();
                        for (int i = 0; i < racePowers.size(); i++) {
                            pickerIndexes.add(new int[]{i});
                            pickerNames.put(i, racePowers.get(i).getDisplayName().getString());
                        }
                    }
                } catch (Throwable ignored) {
                    // RaceAPI not present or no race selected
                }
            }
        }
        UIElement root = ROOT_HOLDER.get();
        ROOT_HOLDER.remove();
        populateRoot(root);
        openScreen = this;
        if (editorRaceId != null) {
            editMode = true;
            editJson = new JsonObject();
            editJson = loadExistingTree(editorRaceId);
            if (editJson == null) {
                editJson = new JsonObject();
                editJson.addProperty("race", editorRaceId);
                editJson.addProperty("title", "");
                editJson.add("nodes", new JsonArray());
            }
            reparseEditNodes();
            viewGroup.setDisplay(false);
            editGroup.setDisplay(true);
        } else {
            List<SkillTreeClientState.ClientNode> nodes = SkillTreeClientState.nodes();
            if (!nodes.isEmpty()) {
                selectedNodeId = nodes.get(0).id();
            }
        }
        rebuildCanvas();
        updateDetail();
    }

    // toast: transient on-screen notification (chat is not used on purpose)
    private UIElement toastHost;
    private Label toastLabel;
    private long toastHideAtMs;
    private boolean toastVisible;

    private void showToast(String key) {
        if (toastHost == null) {
            return;
        }
        toastLabel.setText(Component.translatable(key));
        toastHost.setDisplay(true);
        toastVisible = true;
        toastHideAtMs = System.currentTimeMillis() + 3000;
    }

    @Override
    public void tick() {
        super.tick();
        if (toastVisible && System.currentTimeMillis() > toastHideAtMs) {
            toastVisible = false;
            toastHost.setDisplay(false);
        }
    }

    private static UIElement createRoot() {
        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN)
                .gapAll(4).paddingAll(6).alignItems(AlignItems.STRETCH));
        ROOT_HOLDER.set(root);
        return root;
    }

    /**
     * Loads the previously saved tree for this race (same location saveTree
     * writes to, plus any other datapack that ships one), so re-opening the
     * editor continues the existing tree instead of starting from scratch.
     */
    private JsonObject loadExistingTree(String raceId) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || raceId == null || raceId.isEmpty()) {
            return null;
        }
        Identifier race = Identifier.tryParse(raceId);
        if (race == null) {
            return null;
        }
        Path datapacks = server.getWorldPath(LevelResource.DATAPACK_DIR);
        Path preferred = datapacks.resolve("originsx_skilltree_custom")
                .resolve("data").resolve(race.getNamespace())
                .resolve("skilltree").resolve(race.getPath() + ".json");
        List<Path> candidates = new ArrayList<>();
        candidates.add(preferred);
        try (var walk = Files.walk(datapacks, 6)) {
            walk.filter(p -> p.getFileName() != null
                    && p.getFileName().toString().equals(race.getPath() + ".json")
                    && p.getParent() != null && p.getParent().getFileName() != null
                    && p.getParent().getFileName().toString().equals("skilltree")
                    && p.getParent().getParent() != null && p.getParent().getParent().getFileName() != null
                    && p.getParent().getParent().getFileName().toString().equals(race.getNamespace())
                    && !p.equals(preferred))
                    .forEach(candidates::add);
        } catch (Exception ignored) {
        }
        for (Path candidate : candidates) {
            try {
                JsonObject tree = JsonParser.parseString(Files.readString(candidate)).getAsJsonObject();
                if (tree.has("nodes") && tree.get("nodes").isJsonArray()) {
                    return tree;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public void removed() {
        if (openScreen == this) {
            openScreen = null;
        }
        super.removed();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (SkillTreeClient.OPEN_SKILL_TREE.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Called on the client thread whenever a fresh state snapshot arrives. */
    static void notifyStateApplied() {
        SkillTreeScreen screen = openScreen;
        if (screen != null) {
            Minecraft.getInstance().execute(screen::refresh);
        }
    }

    // ------------------------------------------------------------------
    //  UI construction (runs from the super() call; only touches locals)
    // ------------------------------------------------------------------

    private void populateRoot(UIElement root) {
        var headerRow = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .gapAll(6).alignItems(AlignItems.CENTER));
        Label title = new Label();
        title.setText(Component.translatable("gui.originsx_skilltree.title"));
        title.textStyle(style -> style.fontSize(14));
        title.layout(l -> l.flex(1));
        headerRow.addChild(title);
        root.addChild(headerRow);

        root.addChild(separator());

        canvasHost = new UIElement();
        canvasHost.layout(l -> l.flex(1).widthPercent(100));
        canvasHost.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        root.addChild(canvasHost);

        buildDetailPanel(root);

        // transient toast overlay (top strip), always on top of screen content
        toastHost = new UIElement().layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).height(60)
                .alignItems(AlignItems.CENTER).paddingAll(8));
        toastHost.setDisplay(false);
        toastLabel = new Label();
        toastLabel.setText(Component.translatable("gui.originsx_skilltree.save.done"));
        toastLabel.textStyle(style -> style.fontSize(10).textColor(0xFFDDDDDD).adaptiveWidth(true));
        toastLabel.layout(l -> l.paddingAll(8));
        toastLabel.style(s -> s.background(new ColorRectTexture(0xF022222A)));
        toastHost.addChild(toastLabel);
        root.addChild(toastHost);
    }

    private static UIElement separator() {
        var sep = new UIElement();
        sep.layout(l -> l.widthPercent(100).height(1));
        sep.style(s -> s.background(new ColorRectTexture(LINE_LOCKED)));
        return sep;
    }

    private void buildDetailPanel(UIElement root) {
        var panel = new UIElement().layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(3).paddingAll(4)
                .alignItems(AlignItems.STRETCH));
        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        detailRoot = panel;

        viewGroup = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3)
                        .alignItems(AlignItems.STRETCH));
        detailTitle = adaptiveLabel(12);
        viewGroup.addChild(detailTitle);
        detailDesc = adaptiveLabel(9);
        viewGroup.addChild(detailDesc);
        infoTier = adaptiveLabel(9);
        infoTier.textStyle(s -> s.fontSize(9).textColor(0xFFAAAAAA));
        viewGroup.addChild(infoTier);
        infoEffect = adaptiveLabel(9);
        infoEffect.textStyle(s -> s.fontSize(9).textColor(0xFFE0E0E0));
        viewGroup.addChild(infoEffect);
        infoCost = adaptiveLabel(9);
        viewGroup.addChild(infoCost);

        var actionRow = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                        .alignItems(AlignItems.CENTER));
        unlockButton = new Button();
        unlockButton.setText(Component.empty());
        unlockButton.layout(l -> l.width(20).height(20));
        UIElement unlockIcon = new UIElement();
        unlockIcon.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(2));
        Item shardItem = BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("originsx_skilltree", "skill_shard"));
        if (shardItem != null) {
            unlockIcon.style(s -> s.backgroundTexture(
                    new ItemStackTexture(new ItemStack(shardItem))));
        }
        unlockButton.addChild(unlockIcon);
        unlockButton.setOnClick(e -> {
            if (selectedNodeId != null) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor
                        .sendToServer(new UnlockNodePayload(selectedNodeId));
            }
        });
        actionRow.addChild(unlockButton);
        unlockCost = new Label();
        unlockCost.textStyle(style -> style.fontSize(10));
        actionRow.addChild(unlockCost);
        viewGroup.addChild(actionRow);
        panel.addChild(viewGroup);

        editGroup = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
        fId = editorField("gui.originsx_skilltree.hint.id", v -> setString("id", v));
        fIndex = editorField("gui.originsx_skilltree.hint.index",
                v -> setInt("index", v));
        fX = editorField("gui.originsx_skilltree.hint.px", v -> setInt("x", v));
        fY = editorField("gui.originsx_skilltree.hint.py", v -> setInt("y", v));
        fCosts = editorField("gui.originsx_skilltree.hint.costs", v -> setCosts(v));
        fIcon = new RegistryPicker(RegistryPicker.Kind.ITEM);
        fIcon.setValue("", false);
        fIcon.setOnValueChanged(v -> setString("icon", v));
        fName = editorField("gui.originsx_skilltree.field.name", v -> setPowerString("display_name", v));
        fDesc = editorField("gui.originsx_skilltree.field.desc", v -> setPowerString("description", v));
        fRequires = editorField("gui.originsx_skilltree.field.requires", v -> setRequires(v));
        fPower = editorField("gui.originsx_skilltree.field.power", v -> setPowerJson(v));
        // click on the index or power field offers the powers of the draft
        // race (or of the selected race) instead of typing JSON by hand
        fIndex.addEventListener(UIEvents.MOUSE_DOWN,
                e -> openPowerPicker(), true);
        fPower.addEventListener(UIEvents.MOUSE_DOWN,
                e -> openPowerPicker(), true);

        var r1 = row();
        r1.addChild(labeledFieldFlex("gui.originsx_skilltree.field.id", fId));
        r1.addChild(labeledField("gui.originsx_skilltree.field.index", fIndex, 66));
        r1.addChild(labeledField("gui.originsx_skilltree.field.x", fX, 36));
        r1.addChild(labeledField("gui.originsx_skilltree.field.y", fY, 36));
        r1.addChild(labeledField("gui.originsx_skilltree.field.costs", fCosts, 76));
        editGroup.addChild(r1);
        var r2 = row();
        r2.addChild(labeledFieldFlex("gui.originsx_skilltree.field.name", fName));
        var iconCol = new UIElement().layout(l -> l.flex(1)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)
                .alignItems(AlignItems.STRETCH));
        iconCol.addChild(fieldLabel("gui.originsx_skilltree.field.icon"));
        fIcon.layout(l -> l.widthPercent(100).height(18));
        iconCol.addChild(fIcon);
        r2.addChild(iconCol);
        editGroup.addChild(r2);
        var r3 = row();
        r3.addChild(labeledFieldFlex("gui.originsx_skilltree.field.desc", fDesc));
        r3.addChild(labeledFieldFlex("gui.originsx_skilltree.field.requires", fRequires));
        editGroup.addChild(r3);
        var r4 = row();
        r4.addChild(labeledFieldFlex("gui.originsx_skilltree.field.power", fPower));
        editGroup.addChild(r4);
        var r5 = row();
        Button deleteBtn = smallButton("gui.originsx_skilltree.delete", () -> deleteSelected());
        deleteBtn.layout(l -> l.width(56).height(15));
        r5.addChild(deleteBtn);
        if (editorReturn != null) {
            Button backBtn = smallButton("gui.originsx_skilltree.back", () -> {
                var run = editorReturn;
                editorReturn = null;
                if (run != null) {
                    run.run();
                }
            });
            // wide enough for "Назад к создателю рас" so the text never
            // spills into the neighbouring button
            backBtn.layout(l -> l.width(132).height(15));
            r5.addChild(backBtn);
        }
        Button saveBtn = smallButton("gui.originsx_skilltree.save", this::saveTree);
        saveBtn.layout(l -> l.width(62).height(15));
        r5.addChild(saveBtn);
        editGroup.addChild(r5);
        // hint on its own full-width row so the buttons can never overlap it
        Label hint = new Label();
        hint.setText(Component.translatable("gui.originsx_skilltree.edit_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        hint.textStyle(style -> style.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        editGroup.addChild(hint);
        editGroup.setDisplay(false);
        panel.addChild(editGroup);

        root.addChild(panel);
    }

    private static UIElement row() {
        var row = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4)
                        .alignItems(AlignItems.CENTER));
        return row;
    }

    /** Small caption above an editor input so the player knows what goes where. */
    private static Label fieldLabel(String key) {
        var lb = new Label();
        lb.setText(Component.translatable(key));
        lb.textStyle(s -> s.fontSize(7).textColor(0xFFAAAAAA));
        return lb;
    }

    private static UIElement labeledField(String labelKey, TextField tf, float width) {
        var col = new UIElement().layout(l -> l.width(width)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)
                .alignItems(AlignItems.STRETCH));
        col.addChild(fieldLabel(labelKey));
        col.addChild(tf);
        return col;
    }

    private static UIElement labeledFieldFlex(String labelKey, TextField tf) {
        var col = new UIElement().layout(l -> l.flex(1)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)
                .alignItems(AlignItems.STRETCH));
        col.addChild(fieldLabel(labelKey));
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

    private TextField editorField(String placeholderKey, java.util.function.Consumer<String> onChange) {
        var tf = new TextField();
        tf.layout(l -> l.height(14));
        tf.textFieldStyle(s -> s.placeholder(Component.translatable(placeholderKey)));
        tf.setTextResponder(v -> {
            if (!loadingFields) {
                onChange.accept(v);
            }
        });
        return tf;
    }

    // ------------------------------------------------------------------
    //  Node source (server state or local edit copy)
    // ------------------------------------------------------------------

    private List<SkillTreeClientState.ClientNode> currentNodes() {
        return editMode ? editNodes : SkillTreeClientState.nodes();
    }

    private static SkillTreeClientState.ClientNode find(List<SkillTreeClientState.ClientNode> nodes,
                                                        String id) {
        for (var node : nodes) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }

    private SkillTreeClientState.ClientNode selected() {
        if (selectedNodeId == null) {
            return null;
        }
        for (var node : currentNodes()) {
            if (node.id().equals(selectedNodeId)) {
                return node;
            }
        }
        return null;
    }

    void refresh() {
        if (openScreen != this) {
            return;
        }
        rebuildCanvas();
        updateDetail();
    }

    // ------------------------------------------------------------------
    //  Canvas (FTB Quests style: free placement, drag, right-click menu)
    // ------------------------------------------------------------------

    private void rebuildCanvas() {
        views.clear();
        pixelPos.clear();
        closeContextMenu();
        for (UIElement child : new ArrayList<>(canvasHost.getChildren())) {
            canvasHost.removeChild(child);
        }

        world = new UIElement();
        world.layout(l -> l.width(WORLD_W).height(WORLD_H));
        world.style(s -> s.background(new ColorRectTexture(0xFF14141A)));
        lineLayer = new UIElement();
        lineLayer.layout(l -> l.width(0).height(0));
        world.addChild(lineLayer);

        List<SkillTreeClientState.ClientNode> nodes = currentNodes();
        if (nodes.isEmpty() && !editMode) {
            Label empty = new Label();
            empty.setText(Component.translatable("gui.originsx_skilltree.no_tree"));
            empty.textStyle(style -> style.fontSize(11));
            empty.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(12).top(12));
            world.addChild(empty);
        }

        // old grid trees stored small cell indices; new trees use raw pixels
        boolean legacyGrid = true;
        for (var node : nodes) {
            if (node.x() > 30 || node.y() > 30) {
                legacyGrid = false;
                break;
            }
        }
        final boolean legacy = legacyGrid;
        for (var node : nodes) {
            float px = node.x() * (legacy ? CELL : 1) + 4;
            float py = node.y() * (legacy ? CELL : 1) + 4;
            pixelPos.put(node.id(), new float[]{px, py});
            world.addChild(createNodeButton(node, px, py));
        }
        paintLines();

        ScrollerView scroller = new ScrollerView();
        scroller.layout(l -> l.flex(1).widthPercent(100));
        scroller.viewContainer(view -> view.addChild(world));
        if (editMode) {
            // capture phase: fires even when the click lands on scroller internals;
            // right click on empty space -> "add node" toolkit; drag moves nodes
            canvasHost.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                lastMouseX = e.x;
                lastMouseY = e.y;
                if (e.button == 1) {
                    Vector2f local = world.getLocalMouse(e.x, e.y);
                    for (float[] pos : pixelPos.values()) {
                        if (local.x >= pos[0] && local.x < pos[0] + NODE
                                && local.y >= pos[1] && local.y < pos[1] + NODE) {
                            return; // the node opens its own menu
                        }
                    }
                    openAddMenu(local);
                } else if (e.button == 0 && contextMenu != null) {
                    for (UIElement parent = e.target; parent != null;
                         parent = parent.getParent()) {
                        if (parent == contextMenu) {
                            return;
                        }
                    }
                    closeContextMenu();
                }
            }, true);
            canvasHost.addEventListener(UIEvents.MOUSE_MOVE,
                    this::handleWorldMouseMove, true);
            canvasHost.addEventListener(UIEvents.MOUSE_UP,
                    e -> draggingId = null, true);
        }
        canvasHost.addChild(scroller);
    }

    private UIElement createNodeButton(SkillTreeClientState.ClientNode node,
                                       float px, float py) {
        UIElement button = new UIElement();
        button.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(px).top(py).width(NODE).height(NODE));
        button.style(s -> s.background(new ColorRectTexture(NODE_BG)));
        UIElement icon = new UIElement();
        icon.layout(l -> l.widthPercent(100).heightPercent(100));
        button.addChild(icon);
        NodeView view = new NodeView(button, icon);
        views.put(node.id(), view);
        view.paint(node, SkillTreeClientState.tierOf(node.id()),
                node.id().equals(selectedNodeId));

        button.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            lastMouseX = e.x;
            lastMouseY = e.y;
            if (e.button == 0) {
                selectedNodeId = node.id();
                paintAllNodes();
                updateDetail();
                if (editMode) {
                    draggingId = node.id();
                    Vector2f local = world.getLocalMouse(e.x, e.y);
                    float[] pos = pixelPos.get(node.id());
                    dragStartMouse.set(local.x, local.y);
                    dragStartNodeX = pos[0];
                    dragStartNodeY = pos[1];
                }
            } else if (e.button == 1 && editMode) {
                openNodeMenu(node.id());
            }
            e.propagationStopped = editMode;
        });
        return button;
    }

    /** Called from the world-level mouseMove listener while a node is being dragged. */
    private void handleWorldMouseMove(UIEvent event) {
        if (draggingId == null || world == null) {
            return;
        }
        var nodeJson = selectedEditNodeById(draggingId);
        if (nodeJson == null) {
            draggingId = null;
            return;
        }
        Vector2f local = world.getLocalMouse(event.x, event.y);
        float nx = Math.max(0, Math.min(WORLD_W - NODE,
                dragStartNodeX + local.x - dragStartMouse.x));
        float ny = Math.max(0, Math.min(WORLD_H - NODE,
                dragStartNodeY + local.y - dragStartMouse.y));
        nodeJson.addProperty("x", Math.round(nx));
        nodeJson.addProperty("y", Math.round(ny));
        reparseEditNodes();
        var moved = find(editNodes, draggingId);
        if (moved != null) {
            float[] pos = pixelPos.get(draggingId);
            if (pos != null) {
                pos[0] = nx;
                pos[1] = ny;
            }
            NodeView view = views.get(draggingId);
            if (view != null) {
                view.button().layout(l -> l.left(nx).top(ny));
            }
            paintLines();
            fillEditorFields();
        }
    }

    private JsonObject selectedEditNodeById(String id) {
        if (editJson == null) {
            return null;
        }
        for (JsonElement element : editJson.getAsJsonArray("nodes")) {
            if (element.isJsonObject()
                    && nodeIdMatches(element.getAsJsonObject(), id)) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    /** Elbow connectors between parent and child node centers. */
    private void paintLines() {
        if (lineLayer == null) {
            return;
        }
        for (UIElement child : new ArrayList<>(lineLayer.getChildren())) {
            lineLayer.removeChild(child);
        }
        List<SkillTreeClientState.ClientNode> nodes = currentNodes();
        for (var node : nodes) {
            for (String requiredId : node.requires()) {
                float[] from = pixelPos.get(requiredId);
                float[] to = pixelPos.get(node.id());
                if (from == null || to == null) {
                    continue;
                }
                float x1 = from[0] + NODE / 2f;
                float y1 = from[1] + NODE / 2f;
                float x2 = to[0] + NODE / 2f;
                float y2 = to[1] + NODE / 2f;
                final int color = SkillTreeClientState.tierOf(node.id()) > 0
                        ? LINE_ACTIVE : LINE_LOCKED;
                float midX = (x1 + x2) / 2f;
                hSegment(x1, midX, y1, color);
                vSegment(midX, y1, y2, color);
                hSegment(midX, x2, y2, color);
            }
        }
    }

    private void hSegment(float ax, float bx, float y, int color) {
        float left = Math.min(ax, bx);
        float width = Math.abs(bx - ax);
        if (width < 0.5f) {
            return;
        }
        UIElement seg = new UIElement();
        seg.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(left).top(y - 1).width(width).height(2));
        seg.style(s -> s.background(new ColorRectTexture(color)));
        lineLayer.addChild(seg);
    }

    private void vSegment(float x, float ay, float by, int color) {
        float top = Math.min(ay, by);
        float height = Math.abs(by - ay);
        if (height < 0.5f) {
            return;
        }
        UIElement seg = new UIElement();
        seg.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(x - 1).top(top).width(2).height(height));
        seg.style(s -> s.background(new ColorRectTexture(color)));
        lineLayer.addChild(seg);
    }

    // ------------------------------------------------------------------
    //  Right-click toolkit (FTB Quests-like context menu)
    // ------------------------------------------------------------------

    private void openAddMenu(Vector2f worldLocal) {
        closeContextMenu();
        Vector2f hostLocal = canvasHost.getLocalMouse(lastMouseX, lastMouseY);
        float mx = Math.max(2, hostLocal.x);
        float my = Math.max(2, hostLocal.y);
        final float placeX = Math.max(0, worldLocal.x - NODE / 2f);
        final float placeY = Math.max(0, worldLocal.y - NODE / 2f);
        contextMenu = buildMenuPanel(mx, my,
                panel -> panel.addChild(menuItemTranslated(
                        "gui.originsx_skilltree.menu.add",
                        () -> {
                            closeContextMenu();
                            addNodeAtPixel(placeX, placeY);
                        })));
        canvasHost.addChild(contextMenu);
    }

    private void openNodeMenu(String nodeId) {
        closeContextMenu();
        String source = selectedNodeId;
        selectedNodeId = nodeId;
        paintAllNodes();
        updateDetail();
        Vector2f hostLocal = canvasHost.getLocalMouse(lastMouseX, lastMouseY);
        float mx = Math.max(2, hostLocal.x);
        float my = Math.max(2, hostLocal.y);
        List<Button> items = new ArrayList<>();
        items.add(menuItemTranslated("gui.originsx_skilltree.menu.edit", () -> {
            closeContextMenu();
            updateDetail();
        }));
        if (source != null && !source.equals(nodeId)) {
            items.add(menuItemTranslated("gui.originsx_skilltree.menu.link", () -> {
                closeContextMenu();
                linkRequires(source, nodeId);
            }));
        }
        items.add(menuItemTranslated("gui.originsx_skilltree.delete", () -> {
            closeContextMenu();
            deleteSelected();
        }));
        contextMenu = buildMenuPanel(mx, my, panel -> {
            for (Button item : items) {
                panel.addChild(item);
            }
        });
        canvasHost.addChild(contextMenu);
    }

    /** Makes {@code child} require {@code parent}: parent must be maxed first. */
    private void linkRequires(String parent, String child) {
        var nodeJson = selectedEditNodeById(child);
        if (nodeJson == null) {
            return;
        }
        JsonArray requires = nodeJson.has("requires")
                && nodeJson.get("requires").isJsonArray()
                ? nodeJson.getAsJsonArray("requires") : new JsonArray();
        for (var element : requires) {
            if (element.getAsString().equals(parent)) {
                return;
            }
        }
        requires.add(parent);
        nodeJson.add("requires", requires);
        reparseEditNodes();
        rebuildCanvas();
        updateDetail();
    }

    /**
     * Dropdown over the power fields listing every power added to the draft
     * race; picking one points the selected node at that power.
     */
    private void openPowerPicker() {
        if (contextMenu != null) {
            closeContextMenu();
        }
        List<Button> items = new ArrayList<>();
        for (int[] option : pickerIndexes) {
            int idx = option[0];
            String name = pickerNames.getOrDefault(idx, "?");
            items.add(menuItemLiteral(idx + " · " + name, () -> {
                closeContextMenu();
                applyDraftPower(idx);
            }));
        }
        if (items.isEmpty()) {
            // explain WHY the list is empty instead of showing nothing
            items.add(menuItemTranslated("gui.originsx_skilltree.no_powers", () -> {
                closeContextMenu();
            }));
        }
        contextMenu = buildMenuOn(detailRoot, 6, 6, panel -> {
            for (Button item : items) {
                panel.addChild(item);
            }
        });
        detailRoot.addChild(contextMenu);
    }

    /** Points the selected node at draft power {@code idx}: index + power copy. */
    private void applyDraftPower(int idx) {
        var node = selectedEditNode();
        if (node == null) {
            return;
        }
        node.addProperty("index", idx);
        String powerJson = pickerPowers.get(idx);
        if (powerJson != null) {
            try {
                var parsed = JsonParser.parseString(powerJson);
                if (parsed.isJsonObject()) {
                    // materialize: fill in every engine default so the author
                    // sees (and can tweak) all parameters, not just the ones
                    // the race author happened to write
                    node.add("power", dev.raceapi.power.PowerSchemas.materialize(parsed.getAsJsonObject()));
                } else {
                    node.add("power", parsed);
                }
            } catch (Exception ignored) {
            }
        }
        reparseEditNodes();
        rebuildCanvas();
        fillEditorFields();
        updateDetail();
    }

    private static Button menuItemLiteral(String text, Runnable action) {
        Button item = new Button();
        item.setText(Component.literal(text));
        item.textStyle(s -> s.fontSize(9).textColor(0xFFFFFFFF));
        item.layout(l -> l.widthPercent(100).height(16));
        item.setOnClick(e -> action.run());
        return item;
    }

    /** Same overlay as {@link #buildMenuPanel} but attachable to any parent. */
    private UIElement buildMenuOn(UIElement parent, float x, float y,
                                  java.util.function.Consumer<UIElement> fill) {
        var overlay = new UIElement();
        overlay.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).heightPercent(100));
        var panel = new UIElement();
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(x).top(y).width(150)
                .flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(3));
        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        overlay.addChild(panel);
        fill.accept(panel);
        overlay.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            for (UIElement ancestor = e.target; ancestor != null; ancestor = ancestor.getParent()) {
                if (ancestor == panel) {
                    return;
                }
            }
            closeContextMenu();
        }, true);
        return overlay;
    }

    /**
     * Full-canvasHost transparent overlay that closes on outside click;
     * {@code fill} populates the small menu panel.
     */
    private UIElement buildMenuPanel(float x, float y,
                                     java.util.function.Consumer<UIElement> fill) {
        var overlay = new UIElement();
        overlay.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).heightPercent(100));
        var panel = new UIElement();
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(x).top(y).width(130)
                .flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(3));
        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        overlay.addChild(panel);
        fill.accept(panel);
        overlay.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            for (UIElement p = e.target; p != null; p = p.getParent()) {
                if (p == panel) {
                    return;
                }
            }
            closeContextMenu();
        });
        return overlay;
    }

    private static Button menuItemTranslated(String key, Runnable action) {
        Button item = new Button();
        item.setText(key);
        item.textStyle(s -> s.fontSize(9).textColor(0xFFFFFFFF));
        item.layout(l -> l.widthPercent(100).height(16));
        item.setOnClick(e -> action.run());
        return item;
    }

    private void closeContextMenu() {
        if (contextMenu != null) {
            UIElement host = contextMenu.getParent();
            if (host != null) {
                host.removeChild(contextMenu);
            }
            contextMenu = null;
        }
    }

    private void paintAllNodes() {
        for (var entry : views.entrySet()) {
            var node = find(currentNodes(), entry.getKey());
            if (node != null) {
                entry.getValue().paint(node, SkillTreeClientState.tierOf(node.id()),
                        node.id().equals(selectedNodeId));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Detail panel
    // ------------------------------------------------------------------

    private void updateDetail() {
        if (editMode) {
            fillEditorFields();
            return;
        }
        var node = selected();
        if (node == null) {
            detailTitle.setText(Component.translatable("gui.originsx_skilltree.pick_hint"));
            detailDesc.setText(Component.empty());
            infoTier.setText(Component.empty());
            infoEffect.setText(Component.empty());
            infoCost.setText(Component.empty());
            unlockButton.setDisplay(false);
            unlockCost.setText("");
            return;
        }
        unlockButton.setDisplay(true);
        detailTitle.setText(resolveName(node));
        detailDesc.setText(resolveDescription(node));

        int tier = SkillTreeClientState.tierOf(node.id());
        double nowFactor = TIER_FACTOR[Math.min(tier, 3)];
        infoTier.setText(Component.literal(translated("gui.originsx_skilltree.tier_status",
                roman(tier), roman(3))
                + (requirementsMet(node) ? "" : "   " + translated("gui.originsx_skilltree.req_missing"))));
        if (!requirementsMet(node)) {
            infoEffect.setText(Component.empty());
            infoCost.setText(Component.empty());
            unlockCost.setText("");
        } else if (tier < 3) {
            int cost = nextCost(node);
            infoEffect.setText(Component.literal(translated("gui.originsx_skilltree.now_next",
                    describePower(node.power(), nowFactor),
                    describePower(node.power(), TIER_FACTOR[tier + 1]))));
            int have = shardCount();
            boolean affordable = cost >= 0 && have >= cost;
            infoCost.setText(Component.literal(
                    affordable ? translated("gui.originsx_skilltree.cost_ok", cost, have)
                            : translated("gui.originsx_skilltree.cost_missing", cost, have))
                    .withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED));
            unlockCost.setText(cost >= 0 ? "x" + cost : "");
        } else {
            infoEffect.setText(Component.literal(describePower(node.power(), 1.0)));
            infoCost.setText(Component.translatable("gui.originsx_skilltree.max_tier")
                    .withStyle(ChatFormatting.GREEN));
            unlockButton.setDisplay(false);
            unlockCost.setText("");
        }
    }

    private static net.minecraft.network.chat.MutableComponent resolveName(SkillTreeClientState.ClientNode node) {
        String name = node.displayName();
        if (name.isEmpty()) {
            return Component.translatable("gui.originsx_skilltree.unknown_power");
        }
        net.minecraft.network.chat.MutableComponent component = isTranslatableKey(name)
                ? Component.translatable(name) : Component.literal(name);
        if (isTranslatableKey(name) && component.getString().equals(name)) {
            return Component.literal(prettify(node.id()));
        }
        return component;
    }

    private static net.minecraft.network.chat.MutableComponent resolveDescription(SkillTreeClientState.ClientNode node) {
        String description = node.description();
        if (description.isEmpty()) {
            return Component.empty();
        }
        net.minecraft.network.chat.MutableComponent component = isTranslatableKey(description)
                ? Component.translatable(description) : Component.literal(description);
        if (isTranslatableKey(description) && component.getString().equals(description)) {
            return Component.empty();
        }
        return component.withStyle(ChatFormatting.GRAY);
    }

    /**
     * Human-readable effect of a power scaled by {@code factor}:
     * "+4 Max Health", "+30% Movement Speed" or a prettified type fallback.
     */
    private static String describePower(JsonObject power, double factor) {
        if (power == null) {
            return "";
        }
        String type = str(power, "type");
        String typePath = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        if ("attribute".equals(typePath)) {
            String attr = str(power, "attribute");
            double amount = power.has("amount") && power.get("amount").isJsonPrimitive()
                    ? power.get("amount").getAsDouble() : 0;
            String op = str(power, "operation");
            if (op.isEmpty()) {
                op = "add_value";
            }
            String prettyAttr = prettify(attr);
            double scaled = amount * factor;
            switch (op) {
                case "add_multiplied_base", "multiply_total" -> {
                    return "+" + Math.round(scaled * 100) + "% " + prettyAttr;
                }
                default -> {
                    return (scaled >= 0 ? "+" : "") + trim(scaled) + " " + prettyAttr;
                }
            }
        }
        return prettify(typePath);
    }

    private static String trim(double value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    private static String str(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : "";
    }

    private static boolean isTranslatableKey(String text) {
        return text.indexOf('.') >= 0 && text.indexOf(' ') < 0;
    }

    private static String translated(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static int nextCost(SkillTreeClientState.ClientNode node) {
        int tier = SkillTreeClientState.tierOf(node.id());
        if (tier >= 3 || node.cost().length <= tier) {
            return -1;
        }
        return node.cost()[tier];
    }

    private static boolean requirementsMet(SkillTreeClientState.ClientNode node) {
        for (String required : node.requires()) {
            if (SkillTreeClientState.tierOf(required) < 3) {
                return false;
            }
        }
        return true;
    }

    private static int shardCount() {
        var player = Minecraft.getInstance().player;
        return player == null ? 0 : Shards.count(player);
    }

    /** "minecraft:generic.max_health" -> "Max Health"; ids/keys fall back nicely. */
    private static String prettify(String id) {
        String tail = id;
        int colon = tail.lastIndexOf(':');
        if (colon >= 0) {
            tail = tail.substring(colon + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (String word : tail.split("[_\\s]+")) {
            if (word.isEmpty()) {
                continue;
            }
            for (String part : word.split("\\.")) {
                if (part.isEmpty() || part.equals("generic") || part.equals("player")) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "0";
        };
    }

    // ------------------------------------------------------------------
    //  Edit mode
    // ------------------------------------------------------------------

    private void reparseEditNodes() {
        SkillTreeClientState.ParsedTree parsed =
                SkillTreeClientState.parseTree(editJson.toString());
        editNodes = parsed.nodes();
    }

    private void addNodeAtPixel(float px, float py) {
        if (editJson == null) {
            return;
        }
        JsonObject node = new JsonObject();
        node.addProperty("id", freshNodeId());
        node.addProperty("index", freshIndex());
        node.addProperty("x", Math.round(px));
        node.addProperty("y", Math.round(py));
        node.addProperty("icon", "");
        node.add("requires", new JsonArray());
        JsonArray cost = new JsonArray();
        cost.add(2);
        cost.add(3);
        cost.add(5);
        node.add("cost", cost);
        node.add("power", JsonParser.parseString(DEFAULT_POWER_JSON));
        editJson.getAsJsonArray("nodes").add(node);
        reparseEditNodes();
        selectedNodeId = node.get("id").getAsString();
        rebuildCanvas();
        updateDetail();
    }

    private String freshNodeId() {
        int n = 1;
        while (find(editNodes, "node_" + n) != null) {
            n++;
        }
        return "node_" + n;
    }

    private int freshIndex() {
        int max = -1;
        for (var node : editNodes) {
            max = Math.max(max, node.index());
        }
        return max + 1;
    }

    private void deleteSelected() {
        if (!editMode || selectedNodeId == null || editJson == null) {
            return;
        }
        var array = editJson.getAsJsonArray("nodes");
        for (int i = 0; i < array.size(); i++) {
            var element = array.get(i);
            if (element.isJsonObject()
                    && nodeIdMatches(element.getAsJsonObject(), selectedNodeId)) {
                array.remove(i);
                break;
            }
        }
        reparseEditNodes();
        selectedNodeId = null;
        rebuildCanvas();
        updateDetail();
    }

    /** Null-safe id comparison: nodes may temporarily lack an "id" while being edited. */
    private static boolean nodeIdMatches(JsonObject node, String id) {
        return node.has("id") && node.get("id").isJsonPrimitive()
                && node.get("id").getAsString().equals(id);
    }

    private JsonObject selectedEditNode() {
        if (!editMode || selectedNodeId == null || editJson == null) {
            return null;
        }
        for (JsonElement element : editJson.getAsJsonArray("nodes")) {
            if (element.isJsonObject()
                    && nodeIdMatches(element.getAsJsonObject(), selectedNodeId)) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private void fillEditorFields() {
        var node = selectedEditNode();
        loadingFields = true;
        try {
            if (node == null) {
                for (TextField field : List.of(fId, fIndex, fX, fY, fCosts,
                        fName, fDesc, fRequires, fPower)) {
                    field.setText("");
                }
                fIcon.setValue("", false);
                return;
            }
            fId.setText(node.get("id").getAsString());
            fIndex.setText(strOrEmpty(node, "index"));
            fX.setText(strOrEmpty(node, "x"));
            fY.setText(strOrEmpty(node, "y"));
            fCosts.setText(costsCsv(node));
            fIcon.setValue(str(node, "icon"), false);
            fPower.setText(node.has("power")
                    ? node.getAsJsonObject("power").toString() : "{}");
            var power = node.has("power") ? node.getAsJsonObject("power") : new JsonObject();
            fName.setText(power.has("display_name") ? str(power, "display_name") : "");
            fDesc.setText(power.has("description") ? str(power, "description") : "");
            fRequires.setText(requiresCsv(node));
        } finally {
            loadingFields = false;
        }
    }

    private static String strOrEmpty(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : "";
    }

    private static String costsCsv(JsonObject node) {
        StringBuilder sb = new StringBuilder();
        if (node.has("cost") && node.get("cost").isJsonArray()) {
            for (var element : node.getAsJsonArray("cost")) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(element.getAsInt());
            }
        }
        return sb.toString();
    }

    private static String requiresCsv(JsonObject node) {
        StringBuilder sb = new StringBuilder();
        if (node.has("requires") && node.get("requires").isJsonArray()) {
            for (var element : node.getAsJsonArray("requires")) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(element.getAsString());
            }
        }
        return sb.toString();
    }

    private void setString(String key, String value) {
        var node = selectedEditNode();
        if (node != null) {
            if (value.isEmpty() && !"id".equals(key)) {
                // "id" is mandatory: an empty field keeps the old value
                node.remove(key);
            } else {
                node.addProperty(key, value);
            }
            reparseAndRepaint();
        }
    }

    private void setInt(String key, String value) {
        var node = selectedEditNode();
        if (node != null) {
            try {
                node.addProperty(key, Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                return;
            }
            reparseAndRepaint();
        }
    }

    private void setCosts(String value) {
        var node = selectedEditNode();
        if (node == null) {
            return;
        }
        JsonArray cost = new JsonArray();
        for (String part : value.split(",")) {
            try {
                cost.add(Math.max(0, Integer.parseInt(part.trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        node.add("cost", cost);
        reparseAndRepaint();
    }

    private void setRequires(String value) {
        var node = selectedEditNode();
        if (node == null) {
            return;
        }
        JsonArray requires = new JsonArray();
        for (String part : value.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                requires.add(id);
            }
        }
        node.add("requires", requires);
        reparseAndRepaint();
    }

    private void setPowerString(String key, String value) {
        var node = selectedEditNode();
        if (node == null) {
            return;
        }
        var power = node.has("power") && node.get("power").isJsonObject()
                ? node.getAsJsonObject("power") : new JsonObject();
        if (value.isEmpty()) {
            power.remove(key);
        } else {
            power.addProperty(key, value);
        }
        node.add("power", power);
        reparseAndRepaint();
    }

    private void setPowerJson(String value) {
        var node = selectedEditNode();
        if (node == null) {
            return;
        }
        try {
            node.add("power", JsonParser.parseString(value));
            reparseAndRepaint();
        } catch (Exception ignored) {
            // keep last valid power until the text parses again
        }
    }

    private void reparseAndRepaint() {
        reparseEditNodes();
        paintAllNodes();
        rebuildCanvas();
    }

    private void saveTree() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            showToast("gui.originsx_skilltree.save.singleplayer_only");
            return;
        }
        if (editJson == null) {
            return;
        }
        // save into the EDITED race's file, not whatever race the player
        // currently has selected — otherwise trees for different races
        // overwrite each other (or all land in custom.json)
        String raceStr = editJson.has("race") && editJson.get("race").isJsonPrimitive()
                ? editJson.get("race").getAsString() : null;
        Identifier race = raceStr == null ? null : Identifier.tryParse(raceStr);
        String namespace = race == null ? "skilltree" : race.getNamespace();
        String path = race == null ? "custom" : race.getPath();
        try {
            Path packDir = server.getWorldPath(LevelResource.DATAPACK_DIR)
                    .resolve("originsx_skilltree_custom");
            Path treeFile = packDir.resolve("data").resolve(namespace)
                    .resolve("skilltree").resolve(path + ".json");
            Files.createDirectories(treeFile.getParent());
            Files.writeString(treeFile, PRETTY.toJson(editJson), StandardCharsets.UTF_8);
            Path meta = packDir.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.writeString(meta, """
                        {
                          "pack": {
                            "description": "Player-made skill trees",
                            "pack_format": 48
                          }
                        }
                        """, StandardCharsets.UTF_8);
            }
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), "reload"));
            showToast("gui.originsx_skilltree.save.done");
        } catch (Exception e) {
            showToast("gui.originsx_skilltree.save.failed");
        }
    }
}
