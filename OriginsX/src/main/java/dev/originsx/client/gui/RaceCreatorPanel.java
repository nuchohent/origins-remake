package dev.originsx.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.originsx.client.OriginsXClient;
import dev.raceapi.race.Power;
import dev.raceapi.race.PowerRegistry;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The "Create" tab: a no-code editor that turns a form into a single JSON race
 * file (with inline powers). The result can be copied to the clipboard or
 * written straight into the current world's datapacks folder.
 */
@OnlyIn(Dist.CLIENT)
public final class RaceCreatorPanel {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int PACK_FORMAT_1_21_1 = 48;

    private static final String[] CONDITIONAL_CONDITIONS = {
            "in_water", "not_in_water", "on_ground", "in_air", "is_day", "is_night",
            "is_sprinting", "is_crouching", "is_on_fire",
            "health_below", "health_above", "light_level_below", "light_level_above",
            "has_effect", "in_biome", "in_dimension",
            "is_swimming", "is_raining", "below_y", "above_y", "is_falling",
            "has_armor", "is_full_health",
            // conditions added in Race API 0.7.5
            "eye_in_water", "climbing", "thundering",
            "hunger_below", "hunger_above", "air_below", "air_above",
            "xp_level_above", "xp_level_below",
            "armor_count_above", "armor_count_below",
            "held_item", "offhand_item", "wearing_item",
            "standing_on", "biome_id", "moon_phase", "time_between",
            "entities_nearby_above", "entities_nearby_below",
            "riding", "gamemode", "scoreboard_above", "scoreboard_below",
            // composites: combine other conditions (all_of/any_of take a
            // "conditions" array, "not" negates its single sub-condition)
            "all_of", "any_of", "not"
    };

    private static boolean isCompositeCondition(String type) {
        return "all_of".equals(type) || "any_of".equals(type) || "not".equals(type);
    }

    private static final String[] CONDITIONAL_KEYS = {
            "condition_type", "condition_label", "threshold", "effect", "tag", "dimension"
    };

    private static final String[] CONDITION_EFFECTS = {
            "minecraft:poison", "minecraft:wither", "minecraft:slowness", "minecraft:speed",
            "minecraft:strength", "minecraft:resistance", "minecraft:regeneration",
            "minecraft:weakness", "minecraft:mining_fatigue", "minecraft:blindness",
            "minecraft:nausea", "minecraft:jump_boost", "minecraft:levitation",
            "minecraft:unluck", "minecraft:bad_omen"
    };

    private final RaceDraft draft = new RaceDraft();
    private final List<PowerDraft> powers = new ArrayList<>();

    private CategorizedPicker<PowerTypeSpec> typePicker;
    private TextField newPowerSearch;
    private TextField newPowerName;
    private TextField newPowerDescription;
    private final Map<String, String> newPowerValues = new HashMap<>();
    private final Map<Button, Integer> difficultyButtonValues = new HashMap<>();
    private TextField difficultyField;
    private UIElement newPowerParams;
    private UIElement powersList;
    private UIElement balanceBar;
    private UIElement balanceFill;
    private Label balanceVerdict;
    private UIElement raceDifficultyFill;
    private TextArea preview;
    private String generatedDefaultPath;
    private TextField nameField;
    private TextField idField;
    private TextField datapackNameField;
    private TextField iconField;
    private TextField scaleField;
    private TextArea descriptionArea;
    private Path editPackDir;
    private Identifier editingOriginalId;
    @Nullable
    private JsonObject editingOriginalJson;
    private boolean editing;
    private Button addButton;
    private int editingPowerIndex = -1;
    private CategorizedPicker<PowerTypeSpec> nestedTypePicker;
    private UIElement nestedPowerParams;
    private UIElement conditionalExtras;
    private Selector<String> onInteractTriggerModeSelector;
    private Selector<String> onInteractActionSelector;
    private final PowerDraft nestedPower = new PowerDraft("attribute");
    private Runnable onOpenGuide;

    /** Registers the handler that switches the UI to the guide (called from the "?" button). */
    public void setOnOpenGuide(Runnable onOpenGuide) {
        this.onOpenGuide = onOpenGuide;
    }

    // ---------------------------------------------------------------- guide data

    /**
     * Power-type reference table for the in-game guide. Built straight from the
     * {@link PowerTypeSpec} table so it always stays in sync with the editor.
     */
    public static List<GuidePower> guidePowers() {
        List<GuidePower> result = new ArrayList<>();
        for (PowerTypeSpec spec : PowerTypeSpec.values()) {
            List<String> paramKeys = new ArrayList<>();
            for (ParamSpec param : spec.params) {
                paramKeys.add(param.labelKey);
            }
            result.add(new GuidePower(spec.type, spec.labelKey, spec.descKey, spec.bound, paramKeys));
        }
        return result;
    }

    /** Condition choices offered by the conditional power type (translatable via {@code originsx.creator.condition.*}). */
    public static List<String> conditionTypes() {
        return List.of(CONDITIONAL_CONDITIONS);
    }

    /** Immutable description of a power type, consumed by the guide. */
    public record GuidePower(String type, String labelKey, String descKey, boolean bound, List<String> paramKeys) {
    }

    public UIElement build() {
        initPowerFields();
        var scroller = new ScrollerView();
        scroller.layout(l -> l.flex(1).widthPercent(100));
        scroller.viewContainer(view -> {
            view.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(5).widthPercent(100).paddingAll(2));
            nameField = textField(draft.name, v -> draft.name = v).setTextValidator(s -> s.length() <= 40);
            idField = textField(draft.id, v -> draft.id = v).setResourceLocationOnly()
                    .textFieldStyle(s -> s.placeholder(Component.translatable("originsx.creator.id.hint")));
            datapackNameField = textField(draft.datapackName, v -> draft.datapackName = v)
                    .setTextValidator(s -> s.matches("[a-zA-Z0-9_.-]*"))
                    .textFieldStyle(s -> s.placeholder(Component.translatable("originsx.creator.datapack.hint")));
            iconField = textField(draft.icon, v -> draft.icon = v).setResourceLocationOnly()
                    .textFieldStyle(s -> s.placeholder(Component.translatable("originsx.creator.icon.hint")));
            scaleField = textField(draft.scale, v -> draft.scale = v).setNumbersOnlyDouble(0.25, 4.0)
                    .textFieldStyle(s -> s.placeholder(Component.translatable("originsx.creator.scale.hint")));

            view.addChildren(
                    sectionTitle("originsx.creator.title"),
                    fieldRow("originsx.creator.name", nameField),
                    datapackRow(),
                    iconRow(),
                    difficultyRow(),
                    fieldRow("originsx.creator.scale", scaleField),
                    descriptionArea(),
                    sectionTitle("originsx.creator.powers"),
                    activeHint(),
                    powerSearchRow(),
                    addPowerRow()
            );

            newPowerParams = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
            view.addChild(newPowerParams);

            addButton = new Button();
            addButton.layout(l -> l.width(120).height(20).alignSelf(AlignItems.CENTER));
            addButton.setText("originsx.creator.add");
            addButton.setOnClick(e -> addPower());
            view.addChild(addButton);

            powersList = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
            view.addChild(buildBalanceMeter());
            view.addChild(powersList);

            view.addChild(sectionTitle("originsx.creator.json"));
            preview = new TextArea();
            preview.layout(l -> l.widthPercent(100).height(160));
            preview.textAreaStyle(s -> s.fontSize(8));
            view.addChild(preview);

            var actions = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6));
            Button guideButton = new Button();
            guideButton.layout(l -> l.width(20).height(20));
            guideButton.setText("?");
            guideButton.textStyle(s -> s.fontSize(9));
            guideButton.style(s -> s.tooltips("originsx.guide.tooltip"));
            guideButton.setOnClick(e -> {
                if (onOpenGuide != null) {
                    onOpenGuide.run();
                }
            });
            Button exportButton = new Button();
            exportButton.layout(l -> l.flex(1).height(20));
            exportButton.setText("originsx.creator.save");
                        exportButton.setOnClick(e -> exportToDatapack());
            Button copyButton = new Button();
            copyButton.layout(l -> l.flex(1).height(20));
            copyButton.setText("originsx.creator.copy");
            copyButton.setOnClick(e -> copyJson());
            Button shareButton = new Button();
            shareButton.layout(l -> l.flex(1).height(20));
            shareButton.setText("originsx.creator.share");
            shareButton.style(s -> s.tooltips("originsx.creator.share.tooltip"));
            shareButton.setOnClick(e -> shareRace());
            actions.addChildren(guideButton, exportButton, copyButton, shareButton);
            view.addChild(actions);

            refreshTypeCandidates();
            refreshPowersList();
            refreshPreview();
        });
        return scroller;
    }

    // ---------------------------------------------------------------- rows

    private static UIElement sectionTitle(String key) {
        var title = new Label().setText(key);
        title.textStyle(s -> s.fontSize(11).adaptiveHeight(true).textColor(UiPalette.ACCENT));
        return title;
    }

    private static UIElement activeHint() {
        var hint = new Label().setText("originsx.creator.active.hint");
        hint.textStyle(s -> s.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true)
                .textColor(UiPalette.TEXT_HINT));
        return hint;
    }

    private static UIElement fieldRow(String labelKey, UIElement input) {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                .alignItems(AlignItems.CENTER));
        var label = new Label().setText(labelKey);
        label.textStyle(s -> s.fontSize(9).textColor(UiPalette.TEXT_HINT));
        label.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
        row.addChildren(label, input);
        return row;
    }

    /**
     * Datapack name (latin, used for the saved file) with the optional ID field
     * placed to its right.
     */
    /**
     * Race icon: the raw id text field plus an inline searchable item picker
     * (creative-search style) that fills it.
     */
    private UIElement iconRow() {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .gapAll(6).alignItems(AlignItems.CENTER));
        var col = fieldRow("originsx.creator.icon", iconField);
        col.layout(l -> l.flex(1));
        row.addChild(col);
        var picker = new RegistryPicker(RegistryPicker.Kind.ITEM);
        picker.layout(l -> l.width(150).height(18));
        picker.setValue(draft.icon, false);
        picker.setOnValueChanged(v -> {
            if (v != null && !v.isEmpty()) {
                draft.icon = v;
                iconField.setText(v);
            }
        });
        row.addChild(picker);
        return row;
    }

    private UIElement datapackRow() {        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                .alignItems(AlignItems.CENTER));
        var label = new Label().setText("originsx.creator.datapack.name");
        label.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
        var idLabel = new Label().setText("originsx.creator.id");
        idLabel.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
        idField.layout(l -> l.width(130).height(18));
        row.addChildren(label, datapackNameField, idLabel, idField);
        return row;
    }

    private static TextField textField(String initial, java.util.function.Consumer<String> responder) {
        var field = new TextField();
        field.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        field.layout(l -> l.flex(1).height(18));
        field.setText(initial);
        field.setTextResponder(responder);
        return field;
    }

    private UIElement difficultyRow() {
        var column = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                .alignItems(AlignItems.CENTER));
        var label = new Label().setText("originsx.creator.difficulty");
        label.textStyle(s -> s.fontSize(9).adaptiveWidth(true));

        difficultyField = new TextField();
        difficultyField.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        difficultyField.layout(l -> l.flex(1).height(18));
        difficultyField.setNumbersOnlyInt(-5, 5);
        difficultyField.setText(draft.difficulty);
        difficultyField.setTextResponder(v -> {
            draft.difficulty = v;
            updateDifficultyButtons();
        });

        row.addChildren(label, difficultyField);
        column.addChild(row);

        // full -5..+5 scale: -1/0/+1 alone was too coarse for fine balance work
        var buttonsRow = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .gapAll(2));
        for (int value = -5; value <= 5; value++) {
            addDifficultyButton(buttonsRow, value);
        }
        column.addChild(buttonsRow);

        // visual gauge mirroring the numeric value: fill grows left (strong)
        // or right (weak) from the center notch
        raceDifficultyFill = new UIElement();
        UIElement gauge = new UIElement().layout(l -> l.widthPercent(100).height(6).alignItems(AlignItems.CENTER));
        UIElement gaugeTrack = new UIElement().layout(l -> l.width(120).height(6));
        gaugeTrack.style(s -> s.background(new ColorRectTexture(0xFF151a22)));
        UIElement gaugeNotch = new UIElement().layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .left(59).top(0).width(2).heightPercent(100));
        gaugeNotch.style(s -> s.background(new ColorRectTexture(0xFF3a4552)));
        gaugeTrack.addChildren(gaugeNotch, raceDifficultyFill);
        gauge.addChild(gaugeTrack);
        column.addChild(gauge);

        updateDifficultyButtons();
        return column;
    }

    private void addDifficultyButton(UIElement row, int value) {
        var button = new Button();
        button.layout(l -> l.width(24).height(16));
        button.setText(Component.literal(String.valueOf(value)));
        button.textStyle(s -> s.fontSize(7));
        button.setOnClick(e -> difficultyField.setText(String.valueOf(value)));
        difficultyButtonValues.put(button, value);
        row.addChild(button);
    }

    private void updateDifficultyButtons() {
        int value = parseInt(draft.difficulty, 0);
        for (Map.Entry<Button, Integer> entry : difficultyButtonValues.entrySet()) {
            boolean active = entry.getValue() == value;
            entry.getKey().textStyle(s -> s.fontSize(7)
                    .textColor(active ? 0xFF55CCFF : 0xFFDDDDDD));
        }
        if (raceDifficultyFill != null) {
            // -5..+5 maps to a 59px half: negative grows left, positive right
            int fillWidth = Math.round(59f * Math.abs(value) / 5f);
            int fillLeft = value < 0 ? 59 - fillWidth : 60;
            raceDifficultyFill.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                    .left(fillLeft).top(0).width(Math.max(fillWidth, 1)).heightPercent(100));
            raceDifficultyFill.style(s -> s.background(new ColorRectTexture(
                    value < 0 ? 0xFFFF5555 : value > 0 ? 0xFF55FF55 : 0xFF3a4552)));
        }
    }

    private UIElement descriptionArea() {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                .alignItems(AlignItems.CENTER));
        var label = new Label().setText("originsx.creator.description");
        label.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
        descriptionArea = new TextArea();
        descriptionArea.layout(l -> l.flex(1).height(50));
        descriptionArea.textAreaStyle(s -> s.fontSize(9));
        if (!draft.description.isEmpty()) {
            descriptionArea.setLines(List.of(draft.description.split("\n")));
        }
        descriptionArea.setLinesResponder(lines -> {
            draft.description = String.join("\n", lines);
            refreshPreview();
        });
        row.addChildren(label, descriptionArea);
        return row;
    }

    private void initPowerFields() {
        newPowerName = new TextField();
        newPowerName.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        newPowerName.layout(l -> l.flex(1).height(18));
        newPowerName.textFieldStyle(s -> s.placeholder(Component.translatable("originsx.creator.power.name.hint")));
        newPowerName.setTextResponder(v -> newPowerValues.put("display_name", v));

        newPowerDescription = new TextField();
        newPowerDescription.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        newPowerDescription.layout(l -> l.flex(1).height(18));
        newPowerDescription.textFieldStyle(s -> s.placeholder(Component.translatable("originsx.creator.power.description.hint")));
        newPowerDescription.setTextResponder(v -> newPowerValues.put("description", v));
    }

    private UIElement powerSearchRow() {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                .alignItems(AlignItems.CENTER));
        var label = new Label().setText("originsx.creator.power.search");
        label.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
        newPowerSearch = new TextField();
        newPowerSearch.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        newPowerSearch.layout(l -> l.flex(1).height(18));
        newPowerSearch.textFieldStyle(s -> s.placeholder(Component.translatable("originsx.creator.power.search.hint")));
        newPowerSearch.setTextResponder(v -> refreshTypeCandidates());
        row.addChildren(label, newPowerSearch);
        return row;
    }

    private void refreshTypeCandidates() {
        String query = newPowerSearch == null ? "" : newPowerSearch.getValue().toLowerCase(Locale.ROOT).trim();
        List<PowerTypeSpec> filtered = new ArrayList<>();
        for (PowerTypeSpec spec : PowerTypeSpec.values()) {
            if (query.isEmpty() || matches(spec, query)) {
                filtered.add(spec);
            }
        }
        typePicker.setCandidates(filtered);
        PowerTypeSpec current = typePicker.getValue();
        if (current == null || !filtered.contains(current)) {
            typePicker.setValue(filtered.isEmpty() ? null : filtered.get(0), false);
        }
        refreshParams();
    }

    private static boolean matches(PowerTypeSpec spec, String query) {
        if (spec.type.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return Component.translatable(spec.labelKey).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private UIElement addPowerRow() {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                .alignItems(AlignItems.CENTER));
        var label = new Label().setText("originsx.creator.power.type");
        label.textStyle(s -> s.fontSize(9).adaptiveWidth(true));

        typePicker = new CategorizedPicker<>(powerTypeFormat());
        typePicker.layout(l -> l.flex(1).height(28));
        typePicker.setOnValueChanged(spec -> refreshParams());

        row.addChildren(label, typePicker);
        return row;
    }

    /** Display/grouping rules for the power type dropdown. */
    private static CategorizedPicker.Format<PowerTypeSpec> powerTypeFormat() {
        return new CategorizedPicker.Format<>() {
            @Override
            public Component name(PowerTypeSpec spec) {
                var name = Component.translatable(spec.labelKey);
                // same weights as the balance meter: mark what strengthens
                // (▲ buff) and what weakens (▼ debuff) the race, so players
                // know what to pick when they need a nerf
                int weight = POWER_TYPE_WEIGHTS.getOrDefault(spec.type, 0);
                if (weight < 0) {
                    return Component.literal("▲ ").withStyle(ChatFormatting.GREEN).append(name);
                }
                if (weight > 0) {
                    return Component.literal("▼ ").withStyle(ChatFormatting.RED).append(name);
                }
                return name;
            }

            @Override
            public Component description(PowerTypeSpec spec) {
                String text = Component.translatable(spec.descKey).getString();
                if (text.isEmpty() || text.equals(spec.descKey)) {
                    return null;
                }
                return Component.literal(text);
            }

            @Override
            public String categoryKey(PowerTypeSpec spec) {
                return spec.category().key;
            }

            @Override
            public int categoryOrder(PowerTypeSpec spec) {
                return spec.category().ordinal();
            }

            @Override
            public boolean highlighted(PowerTypeSpec spec) {
                return spec.bound;
            }
        };
    }

    /** True once a resource (mana pool) power was added to the draft race. */
    private boolean hasResourcePower() {
        for (PowerDraft power : powers) {
            if ("resource".equals(power.type)) {
                return true;
            }
        }
        return false;
    }

    private void refreshParams() {
        PowerTypeSpec spec = typePicker.getValue();
        if (spec == null) {
            spec = PowerTypeSpec.ATTRIBUTE;
        }
        // The name/description text fields are the source of truth. Use put(),
        // not putIfAbsent(), so stale values from a previously edited power
        // never leak into a newly selected power type.
        newPowerValues.put("display_name", newPowerName.getValue());
        newPowerValues.put("description", newPowerDescription.getValue());
        for (ParamSpec param : spec.params) {
            newPowerValues.putIfAbsent(param.key, param.defaultValue);
        }
        newPowerValues.putIfAbsent("difficulty", "0");
        newPowerValues.putIfAbsent("bind_slot", "0");
        newPowerValues.putIfAbsent("cost", "0");

        newPowerParams.clearAllChildren();

        newPowerParams.addChild(fieldRow("originsx.creator.power.name", newPowerName));
        newPowerParams.addChild(fieldRow("originsx.creator.power.description", newPowerDescription));
        newPowerParams.addChild(fieldRow("originsx.creator.power.difficulty",
                numberField("difficulty", newPowerValues, true, -5, 5)));

        if (spec == PowerTypeSpec.CONDITIONAL) {
            buildConditionalRows();
            return;
        }

        if (spec.bound) {
            String[] slotLabels = new String[OriginsXClient.MAX_ACTIVE_SLOTS];
            for (int i = 0; i < OriginsXClient.MAX_ACTIVE_SLOTS; i++) {
                slotLabels[i] = "Slot " + i;
            }
            newPowerParams.addChild(fieldRow("originsx.creator.power.slot",
                    optionField("bind_slot", newPowerValues, "0", slotLabels)));
            // resource (mana/stamina) cost: only shown once the race actually
            // has a resource pool power; 0 = free activation
            if (hasResourcePower()) {
                newPowerParams.addChild(fieldRow("originsx.creator.param.cost",
                        numberField("cost", newPowerValues, false, 0.0, 10000.0)));
            }
        }

        Map<String, UIElement> rows = new HashMap<>();
        onInteractTriggerModeSelector = null;
        onInteractActionSelector = null;
        resourceKindSelector = null;
        for (ParamSpec param : spec.params) {
            UIElement input = paramInput(param, newPowerValues);
            if (spec == PowerTypeSpec.ON_INTERACT && input instanceof Selector<?> sel) {
                if (param.key.equals("trigger_mode")) {
                    @SuppressWarnings("unchecked")
                    Selector<String> s = (Selector<String>) sel;
                    onInteractTriggerModeSelector = s;
                } else if (param.key.equals("action")) {
                    @SuppressWarnings("unchecked")
                    Selector<String> s = (Selector<String>) sel;
                    onInteractActionSelector = s;
                }
            }
            if (spec == PowerTypeSpec.RESOURCE && param.key.equals("resource_kind")
                    && input instanceof Selector<?> sel) {
                @SuppressWarnings("unchecked")
                Selector<String> s = (Selector<String>) sel;
                resourceKindSelector = s;
            }
            if (param.kind == Kind.TOGGLE && param.hidesWhenOn.length > 0 && input instanceof Switch sw) {
                // Toggle with side effects (e.g. "infinite" hiding
                // duration/interval): put the hint to the right of the switch,
                // visible only while the toggle is on.
                UIElement hint = alwaysHint();
                UIElement row = fieldRowWithHint(param.labelKey, input, hint);
                newPowerParams.addChild(row);
                rows.put(param.key, row);
                hint.setDisplay(Boolean.parseBoolean(newPowerValues.getOrDefault(param.key, "false")));
                sw.setOnSwitchChanged(on -> {
                    for (String key : param.hidesWhenOn) {
                        UIElement target = rows.get(key);
                        if (target != null) {
                            // setDisplay(false) collapses the row in the layout
                            // (setVisible alone only stops rendering, leaving a gap).
                            target.setDisplay(!on);
                        }
                    }
                    hint.setDisplay(on);
                });
                continue;
            }
            UIElement row = fieldRow(param.labelKey, input);
            // optional explanation shows as a tooltip on hover over the
            // parameter row (same way power descriptions work)
            String hintKey = paramHintKey(spec, param.key);
            if (hintKey != null) {
                row.style(s -> s.tooltips(Component.translatable(hintKey)));
            }
            newPowerParams.addChild(row);
            rows.put(param.key, row);
        }
        for (ParamSpec param : spec.params) {
            if (param.kind == Kind.TOGGLE && param.hidesWhenOn.length > 0
                    && Boolean.parseBoolean(newPowerValues.getOrDefault(param.key, "false"))) {
                for (String key : param.hidesWhenOn) {
                    UIElement target = rows.get(key);
                    if (target != null) {
                        target.setDisplay(false);
                    }
                }
            }
        }

        if (spec == PowerTypeSpec.ON_INTERACT) {
            buildOnInteractVisibility(rows);
        }
        if (spec == PowerTypeSpec.RESOURCE) {
            buildResourceVisibility(rows);
        }
    }

    /**
     * Translation key of an optional explanation shown under a parameter row,
     * or null when the parameter needs no explanation.
     */
    private static String paramHintKey(PowerTypeSpec spec, String paramKey) {
        if (spec == PowerTypeSpec.KILL_REWARD || spec == PowerTypeSpec.EAT_REWARD) {
            return switch (paramKey) {
                case "resource_gain" -> "originsx.creator.param.resource_gain.hint";
                case "heal" -> "originsx.creator.param.heal.hint";
                default -> null;
            };
        }
        return null;
    }

    /** Selector of the "mana / stamina" kind picker of the resource power. */    private Selector<String> resourceKindSelector;

    /**
     * Stamina drain rows (sprint/attack) are only relevant for the stamina
     * kind - hidden while the pool is a mana pool.
     */
    private void buildResourceVisibility(Map<String, UIElement> rows) {
        Runnable applyVisibility = () -> {
            boolean stamina = "stamina".equals(
                    newPowerValues.getOrDefault("resource_kind", "mana"));
            setRowVisible(rows, "consume_sprint", stamina);
            setRowVisible(rows, "sprint_cost", stamina);
            setRowVisible(rows, "consume_attack", stamina);
            setRowVisible(rows, "attack_cost", stamina);
        };
        if (resourceKindSelector != null) {
            resourceKindSelector.setOnValueChanged(v -> {
                newPowerValues.put("resource_kind", v == null ? "mana" : String.valueOf(v));
                applyVisibility.run();
            });
        }
        applyVisibility.run();
    }

    /**
     * Row with an extra element (e.g. a hint label) placed after the input,
     * so the hint shows up to the right of a toggle.
     */
    private static UIElement fieldRowWithHint(String labelKey, UIElement input, UIElement trailing) {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(6)
                .alignItems(AlignItems.CENTER));
        var label = new Label().setText(labelKey);
        label.textStyle(s -> s.fontSize(9).textColor(UiPalette.TEXT_HINT));
        label.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
        row.addChildren(label, input, trailing);
        return row;
    }

    /**
     * Small gray hint shown next to an "always/infinite" toggle, explaining
     * that duration and interval don't need to be set while it's on.
     */
    private static UIElement alwaysHint() {
        var hint = new Label().setText("originsx.creator.param.infinite_hint");
        hint.layout(l -> l.flex(1));
        hint.textStyle(s -> s.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true)
                .textColor(UiPalette.TEXT_HINT));
        return hint;
    }

    private void buildOnInteractVisibility(Map<String, UIElement> rows) {
        Set<String> triggerItemModes = Set.of("block_with_item", "entity_with_item");
        Set<String> triggerEntityModes = Set.of("specific_entity");
        Set<String> triggerBlockModes = Set.of("specific_block");
        Set<String> effectActions = Set.of("effect");
        Set<String> healActions = Set.of("heal", "heal_target");
        Set<String> damageActions = Set.of("damage", "damage_target");
        Set<String> summonActions = Set.of("summon");

        Runnable applyVisibility = () -> {
            String triggerMode = newPowerValues.getOrDefault("trigger_mode", "any");
            String action = newPowerValues.getOrDefault("action", "effect");
            setRowVisible(rows, "trigger_item", triggerItemModes.contains(triggerMode));
            setRowVisible(rows, "trigger_entity", triggerEntityModes.contains(triggerMode));
            setRowVisible(rows, "trigger_block", triggerBlockModes.contains(triggerMode));
            setRowVisible(rows, "effect", effectActions.contains(action));
            setRowVisible(rows, "effect_duration", effectActions.contains(action));
            setRowVisible(rows, "effect_amplifier", effectActions.contains(action));
            setRowVisible(rows, "heal_amount", healActions.contains(action));
            setRowVisible(rows, "damage_amount", damageActions.contains(action));
            setRowVisible(rows, "summon_entity", summonActions.contains(action));
            setRowVisible(rows, "summon_count", summonActions.contains(action));
        };

        if (onInteractTriggerModeSelector != null) {
            onInteractTriggerModeSelector.setOnValueChanged(v -> {
                newPowerValues.put("trigger_mode", v == null ? "any" : String.valueOf(v));
                applyVisibility.run();
            });
        }
        if (onInteractActionSelector != null) {
            onInteractActionSelector.setOnValueChanged(v -> {
                newPowerValues.put("action", v == null ? "effect" : String.valueOf(v));
                applyVisibility.run();
            });
        }
        applyVisibility.run();
    }

    private static void setRowVisible(Map<String, UIElement> rows, String key, boolean visible) {
        UIElement row = rows.get(key);
        if (row != null) {
            row.setDisplay(visible);
        }
    }

    /** Translated option label ("originsx.creator.option.<value>") or the raw value. */
    private static Component optionLabel(String value) {
        String key = "originsx.creator.option." + value;
        String text = Component.translatable(key).getString();
        return text.equals(key) ? Component.literal(value) : Component.literal(text);
    }

    private void buildConditionalRows() {
        newPowerParams.addChild(fieldRow("originsx.creator.param.condition_type", conditionSelector()));

        conditionalExtras = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
        newPowerParams.addChild(conditionalExtras);
        refreshConditionalExtras();

        newPowerParams.addChild(fieldRow("originsx.creator.param.condition_label", conditionLabelField()));

        var effectTitle = new Label().setText("originsx.creator.conditional.effect.title");
        effectTitle.textStyle(s -> s.fontSize(9));
        newPowerParams.addChild(effectTitle);

        nestedTypePicker = new CategorizedPicker<>(powerTypeFormat());
        nestedTypePicker.layout(l -> l.flex(1).height(24));
        // the nested picker has no search field of its own - always offer the
        // full type list (it used to stay empty and show "nothing found")
        nestedTypePicker.setCandidates(List.of(PowerTypeSpec.values()));
        nestedTypePicker.setValue(PowerTypeSpec.byType(nestedPower.type), false);
        nestedTypePicker.setOnValueChanged(spec -> {
            if (spec != null) {
                nestedPower.type = spec.type;
                nestedPower.values.keySet().retainAll(specParamKeys(spec));
            }
            refreshNestedParams();
        });
        newPowerParams.addChild(fieldRow("originsx.creator.power.type", nestedTypePicker));

        nestedPowerParams = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
        newPowerParams.addChild(nestedPowerParams);
        refreshNestedParams();
    }

    private static List<String> specParamKeys(PowerTypeSpec spec) {
        List<String> keys = new ArrayList<>();
        for (ParamSpec param : spec.params) {
            keys.add(param.key);
        }
        keys.add("difficulty");
        return keys;
    }

    private UIElement conditionSelector() {
        var selector = new Selector<String>();
        selector.layout(l -> l.flex(1).height(18));
        selector.setCandidates(List.of(CONDITIONAL_CONDITIONS));
        String current = newPowerValues.getOrDefault("condition_type", "in_water");
        if (!List.of(CONDITIONAL_CONDITIONS).contains(current)) {
            current = "in_water";
        }
        selector.setSelected(current, false);
        selector.setCandidateUIProvider(value -> {
            var item = new Label().setText(value == null ? Component.empty()
                    : Component.translatable("originsx.creator.condition." + value));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        selector.setOnValueChanged(v -> {
            newPowerValues.put("condition_type", v == null ? "in_water" : v);
            refreshConditionalExtras();
        });
        return selector;
    }

    private void refreshConditionalExtras() {
        if (conditionalExtras == null) {
            return;
        }
        conditionalExtras.clearAllChildren();
        String condition = newPowerValues.getOrDefault("condition_type", "in_water");
        if ("all_of".equals(condition) || "any_of".equals(condition)) {
            for (int i = 1; i <= 3; i++) {
                addSubConditionEditor(conditionalExtras, i);
            }
        } else if ("not".equals(condition)) {
            addSubConditionEditor(conditionalExtras, 1);
        } else {
            addConditionParamRows(conditionalExtras, condition, "");
        }
    }

    /**
     * One sub-condition slot of a composite condition. Sub-values are stored
     * flat under a "subN_" prefix (sub2_type, sub2_threshold, ...) and are
     * re-assembled into nested JSON on export.
     */
    private void addSubConditionEditor(UIElement parent, int index) {
        String prefix = "sub" + index + "_";
        var selector = new Selector<String>();
        selector.layout(l -> l.flex(1).height(18));
        // composites inside composites are valid JSON but the no-code editor
        // only offers simple conditions per slot
        List<String> simple = new ArrayList<>();
        for (String type : CONDITIONAL_CONDITIONS) {
            if (!isCompositeCondition(type)) {
                simple.add(type);
            }
        }
        selector.setCandidates(simple);
        String current = newPowerValues.getOrDefault(prefix + "type", "in_water");
        if (!simple.contains(current)) {
            current = "in_water";
        }
        selector.setSelected(current, false);
        selector.setCandidateUIProvider(value -> {
            var item = new Label().setText(value == null ? Component.empty()
                    : Component.translatable("originsx.creator.condition." + value));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        selector.setOnValueChanged(v -> {
            newPowerValues.put(prefix + "type", v == null ? "in_water" : v);
            refreshConditionalExtras();
        });
        var title = new Label().setText(Component.translatable("originsx.creator.param.condition.sub",
                index).getString().toUpperCase(Locale.ROOT));
        title.textStyle(s -> s.fontSize(8).textColor(UiPalette.ACCENT));
        parent.addChild(title);
        parent.addChild(fieldRow("originsx.creator.param.condition_type", selector));
        addConditionParamRows(parent, current, prefix);
    }

    /**
     * The parameter rows a given condition type needs (threshold / effect /
     * tag / dimension), stored under {@code prefix} ("sub1_..." or plain).
     */
    private void addConditionParamRows(UIElement parent, String type, String prefix) {
        switch (type) {
            case "health_below", "health_above" -> {
                double health = parseDouble(newPowerValues.get(prefix + "threshold"), 0.5);
                health = Math.max(0.0, Math.min(1.0, health));
                newPowerValues.put(prefix + "threshold", Double.toString(health));
                parent.addChild(fieldRow("originsx.creator.param.threshold",
                        numberField(prefix + "threshold", newPowerValues, false, 0.0, 1.0)));
            }
            case "light_level_below", "light_level_above" -> {
                int light = parseInt(newPowerValues.get(prefix + "threshold"), 7);
                light = Math.max(0, Math.min(15, light));
                newPowerValues.put(prefix + "threshold", Integer.toString(light));
                parent.addChild(fieldRow("originsx.creator.param.threshold",
                        numberField(prefix + "threshold", newPowerValues, true, 0, 15)));
            }
            case "below_y", "above_y" -> {
                int y = parseInt(newPowerValues.get(prefix + "threshold"), 64);
                y = Math.max(-64, Math.min(320, y));
                newPowerValues.put(prefix + "threshold", Integer.toString(y));
                parent.addChild(fieldRow("originsx.creator.param.threshold",
                        numberField(prefix + "threshold", newPowerValues, true, -64, 320)));
            }
            case "has_effect" -> {
                var effectPicker = new RegistryPicker(RegistryPicker.Kind.EFFECT);
                effectPicker.layout(l -> l.flex(1).height(18));
                effectPicker.setValue(
                        newPowerValues.getOrDefault(prefix + "effect", "minecraft:poison"), false);
                effectPicker.setOnValueChanged(v ->
                        newPowerValues.put(prefix + "effect", v == null ? "" : v));
                parent.addChild(fieldRow("originsx.creator.param.effect", effectPicker));
            }
            case "in_biome" ->
                    parent.addChild(fieldRow("originsx.creator.param.tag",
                            textFieldFor(prefix + "tag", newPowerValues)));
            case "in_dimension" ->
                    parent.addChild(fieldRow("originsx.creator.param.dimension",
                            textFieldFor(prefix + "dimension", newPowerValues)));
            // ── conditions added in Race API 0.7.5 ──────────────────────
            case "hunger_below", "hunger_above" -> {
                int hunger = parseInt(newPowerValues.get(prefix + "threshold"), 10);
                hunger = Math.max(0, Math.min(20, hunger));
                newPowerValues.put(prefix + "threshold", Integer.toString(hunger));
                parent.addChild(fieldRow("originsx.creator.param.threshold",
                        numberField(prefix + "threshold", newPowerValues, true, 0, 20)));
            }
            case "xp_level_above", "xp_level_below" ->
                    parent.addChild(fieldRow("originsx.creator.param.threshold",
                            numberField(prefix + "threshold", newPowerValues, true, -100, 10000)));
            case "air_below", "air_above" -> {
                double air = parseDouble(newPowerValues.get(prefix + "threshold"), 0.5);
                air = Math.max(0.0, Math.min(1.0, air));
                newPowerValues.put(prefix + "threshold", Double.toString(air));
                parent.addChild(fieldRow("originsx.creator.param.threshold",
                        numberField(prefix + "threshold", newPowerValues, false, 0.0, 1.0)));
            }
            case "armor_count_above", "armor_count_below" -> {
                int pieces = parseInt(newPowerValues.get(prefix + "threshold"), 1);
                pieces = Math.max(0, Math.min(4, pieces));
                newPowerValues.put(prefix + "threshold", Integer.toString(pieces));
                parent.addChild(fieldRow("originsx.creator.param.threshold",
                        numberField(prefix + "threshold", newPowerValues, true, 0, 4)));
            }
            case "entities_nearby_above", "entities_nearby_below" -> {
                int count = parseInt(newPowerValues.get(prefix + "threshold"), 1);
                newPowerValues.put(prefix + "threshold", Integer.toString(count));
                parent.addChild(fieldRow("originsx.creator.param.threshold",
                        numberField(prefix + "threshold", newPowerValues, true, 0, 100)));
                double radius = parseDouble(newPowerValues.get(prefix + "radius"), 8.0);
                newPowerValues.put(prefix + "radius", Double.toString(radius));
                parent.addChild(fieldRow("originsx.creator.param.radius",
                        numberField(prefix + "radius", newPowerValues, false, 1.0, 64.0)));
            }
            case "held_item", "offhand_item", "wearing_item" -> {
                if ("wearing_item".equals(type)) {
                    var slotSelector = new Selector<String>();
                    slotSelector.layout(l -> l.flex(1).height(18));
                    slotSelector.setCandidates(List.of("head", "chest", "legs", "feet"));
                    String slot = newPowerValues.getOrDefault(prefix + "slot", "head");
                    slotSelector.setSelected(slot, false);
                    slotSelector.setOnValueChanged(v ->
                            newPowerValues.put(prefix + "slot", v == null ? "head" : v));
                    parent.addChild(fieldRow("originsx.creator.param.slot", slotSelector));
                }
                var itemPicker = new RegistryPicker(RegistryPicker.Kind.ITEM);
                itemPicker.layout(l -> l.flex(1).height(18));
                itemPicker.setValue(newPowerValues.getOrDefault(prefix + "item", "minecraft:diamond_sword"), false);
                itemPicker.setOnValueChanged(v ->
                        newPowerValues.put(prefix + "item", v == null ? "" : v));
                parent.addChild(fieldRow("originsx.creator.param.item", itemPicker));
                parent.addChild(fieldRow("originsx.creator.param.item_tag",
                        textFieldFor(prefix + "item_tag", newPowerValues)));
            }
            case "standing_on" -> {
                parent.addChild(fieldRow("originsx.creator.param.block",
                        textFieldFor(prefix + "block", newPowerValues)));
                parent.addChild(fieldRow("originsx.creator.param.block_tag",
                        textFieldFor(prefix + "block_tag", newPowerValues)));
            }
            case "biome_id" ->
                    parent.addChild(fieldRow("originsx.creator.param.biome",
                            textFieldFor(prefix + "biome", newPowerValues)));
            case "moon_phase" -> {
                var phaseSelector = new Selector<String>();
                phaseSelector.layout(l -> l.flex(1).height(18));
                phaseSelector.setCandidates(List.of("full_moon", "waning_gibbous", "third_quarter",
                        "waning_crescent", "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"));
                phaseSelector.setSelected(
                        newPowerValues.getOrDefault(prefix + "phase", "full_moon"), false);
                phaseSelector.setOnValueChanged(v ->
                        newPowerValues.put(prefix + "phase", v == null ? "full_moon" : v));
                parent.addChild(fieldRow("originsx.creator.param.phase", phaseSelector));
            }
            case "time_between" -> {
                int min = parseInt(newPowerValues.get(prefix + "min"), 0);
                newPowerValues.put(prefix + "min", Integer.toString(min));
                parent.addChild(fieldRow("originsx.creator.param.min",
                        numberField(prefix + "min", newPowerValues, true, 0, 24000)));
                int max = parseInt(newPowerValues.get(prefix + "max"), 24000);
                newPowerValues.put(prefix + "max", Integer.toString(max));
                parent.addChild(fieldRow("originsx.creator.param.max",
                        numberField(prefix + "max", newPowerValues, true, 0, 24000)));
            }
            case "gamemode" -> {
                var modeSelector = new Selector<String>();
                modeSelector.layout(l -> l.flex(1).height(18));
                modeSelector.setCandidates(List.of("survival", "creative", "adventure", "spectator"));
                modeSelector.setSelected(
                        newPowerValues.getOrDefault(prefix + "mode", "survival"), false);
                modeSelector.setOnValueChanged(v ->
                        newPowerValues.put(prefix + "mode", v == null ? "survival" : v));
                parent.addChild(fieldRow("originsx.creator.param.mode", modeSelector));
            }
            case "riding" ->
                    parent.addChild(fieldRow("originsx.creator.param.entity",
                            textFieldFor(prefix + "entity", newPowerValues)));
            case "scoreboard_above", "scoreboard_below" -> {
                parent.addChild(fieldRow("originsx.creator.param.objective",
                        textFieldFor(prefix + "objective", newPowerValues)));
                int threshold = parseInt(newPowerValues.get(prefix + "threshold"), 0);
                newPowerValues.put(prefix + "threshold", Integer.toString(threshold));
                parent.addChild(fieldRow("originsx.creator.param.threshold",
                        numberField(prefix + "threshold", newPowerValues, true, -100000, 100000)));
            }
            default -> {
            }
        }
    }

    private TextField conditionLabelField() {
        var field = new TextField();
        field.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        field.layout(l -> l.flex(1).height(18));
        field.textFieldStyle(s -> s.placeholder(Component.translatable("originsx.creator.param.condition_label.hint")));
        field.setText(newPowerValues.getOrDefault("condition_label", ""));
        field.setTextResponder(v -> newPowerValues.put("condition_label", v));
        return field;
    }

    private UIElement stringOption(String key, Map<String, String> store, String defaultValue, String[] options) {
        var selector = new Selector<String>();
        selector.layout(l -> l.flex(1).height(18));
        selector.setCandidates(List.of(options));
        String current = store.getOrDefault(key, defaultValue);
        if (!List.of(options).contains(current)) {
            current = defaultValue;
        }
        selector.setSelected(current, false);
        selector.setCandidateUIProvider(value -> {
            var item = new Label().setText(value == null ? Component.empty() : Component.literal(value));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        selector.setOnValueChanged(v -> store.put(key, v));
        return selector;
    }

    private TextField textFieldFor(String key, Map<String, String> store) {
        var field = new TextField();
        field.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        field.layout(l -> l.flex(1).height(18));
        field.setText(store.getOrDefault(key, ""));
        field.setTextResponder(v -> store.put(key, v));
        return field;
    }

    private record AttrConstraints(double min, double max, double baseValue, String hintKey, String defaultOp) {}

    private static final Map<String, AttrConstraints> ATTR_CONSTRAINTS = Map.ofEntries(
            Map.entry("minecraft:movement_speed", new AttrConstraints(-0.5, 2.0, 0.1, "originsx.creator.hint.movement_speed", "add_multiplied_base")),
            Map.entry("minecraft:max_health",     new AttrConstraints(-20, 40, 20.0, null, "add_value")),
            Map.entry("minecraft:attack_damage",  new AttrConstraints(-10, 20, 1.0, null, "add_value")),
            Map.entry("minecraft:armor",          new AttrConstraints(-10, 30, 0.0, null, "add_value")),
            Map.entry("minecraft:armor_toughness",new AttrConstraints(-10, 30, 0.0, null, "add_value")),
            Map.entry("minecraft:knockback_resistance", new AttrConstraints(-1, 1, 0.0, null, "add_multiplied_base")),
            Map.entry("minecraft:luck",           new AttrConstraints(-10, 10, 0.0, null, "add_value")),
            Map.entry("minecraft:gravity",        new AttrConstraints(-0.1, 1.0, 0.08, null, "add_multiplied_base")),
            Map.entry("minecraft:fly_speed",      new AttrConstraints(-1, 5, 0.4, null, "add_multiplied_base")),
            Map.entry("minecraft:attack_reach",   new AttrConstraints(-2, 10, 4.5, null, "add_value")),
            Map.entry("minecraft:entity_interaction_range", new AttrConstraints(-2, 10, 3.0, null, "add_value")),
            Map.entry("minecraft:block_interaction_range",  new AttrConstraints(-2, 10, 4.5, null, "add_value"))
    );

    private void refreshNestedParams() {
        if (nestedPowerParams == null) {
            return;
        }
        nestedPowerParams.clearAllChildren();
        PowerTypeSpec spec = PowerTypeSpec.byType(nestedPower.type);

        if (spec == PowerTypeSpec.ATTRIBUTE) {
            refreshAttributeParams(spec);
        } else {
            for (ParamSpec param : spec.params) {
                if (param.defaultValue != null && !param.defaultValue.isEmpty()) {
                    nestedPower.values.putIfAbsent(param.key, param.defaultValue);
                }
                nestedPowerParams.addChild(fieldRow(param.labelKey, paramInput(param, nestedPower.values)));
            }
        }
        nestedPower.values.putIfAbsent("difficulty", "0");
        nestedPowerParams.addChild(fieldRow("originsx.creator.power.difficulty",
                numberField("difficulty", nestedPower.values, true, -5, 5)));
    }

    private void refreshAttributeParams(PowerTypeSpec spec) {
        ParamSpec attributeSpec = spec.params.get(0);
        ParamSpec amountSpec = spec.params.get(1);
        ParamSpec operationSpec = spec.params.get(2);

        String currentAttr = nestedPower.values.getOrDefault(attributeSpec.key, attributeSpec.defaultValue);
        AttrConstraints constraints = ATTR_CONSTRAINTS.getOrDefault(currentAttr,
                new AttrConstraints(-100000, 100000, 1.0, null, "add_value"));

        Selector<String> attrSelector = new Selector<>();
        attrSelector.layout(l -> l.flex(1).height(18));
        attrSelector.setCandidates(List.of(attributeSpec.options));
        String attrValue = nestedPower.values.getOrDefault(attributeSpec.key, attributeSpec.defaultValue);
        if (!List.of(attributeSpec.options).contains(attrValue)) {
            attrValue = attributeSpec.defaultValue;
        }
        attrSelector.setSelected(attrValue, false);
        attrSelector.setCandidateUIProvider(value -> {
            var item = new Label().setText(value == null ? Component.empty() : Component.literal(value));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        attrSelector.setOnValueChanged(v -> nestedPower.values.put(attributeSpec.key, v));
        nestedPowerParams.addChild(fieldRow(attributeSpec.labelKey, attrSelector));

        TextField amountField = new TextField();
        amountField.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        amountField.layout(l -> l.flex(1).height(18));
        amountField.setNumbersOnlyDouble(constraints.min(), constraints.max());
        String amountInitial = nestedPower.values.getOrDefault(amountSpec.key, amountSpec.defaultValue);
        amountField.setText(amountInitial);
        amountField.setTextResponder(v -> nestedPower.values.put(amountSpec.key, v));
        nestedPowerParams.addChild(fieldRow(amountSpec.labelKey, amountField));

        Selector<String> opSelector = new Selector<>();
        opSelector.layout(l -> l.flex(1).height(18));
        opSelector.setCandidates(List.of(operationSpec.options));
        String opValue = nestedPower.values.getOrDefault(operationSpec.key, operationSpec.defaultValue);
        if (!List.of(operationSpec.options).contains(opValue)) {
            opValue = operationSpec.defaultValue;
        }
        opSelector.setSelected(opValue, false);
        opSelector.setCandidateUIProvider(value -> {
            String label = switch (value == null ? "" : value) {
                case "add_value" -> "add_value (+n)";
                case "add_multiplied_base" -> "multiply_base (+n%)";
                case "add_multiplied_total" -> "multiply_total (+n%)";
                default -> value;
            };
            var item = new Label().setText(Component.literal(label));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        opSelector.setOnValueChanged(v -> nestedPower.values.put(operationSpec.key, v));
        nestedPowerParams.addChild(fieldRow(operationSpec.labelKey, opSelector));

        var opsInfo = new Label().setText(Component.translatable("originsx.creator.hint.attribute_ops"));
        opsInfo.layout(l -> l.flex(1).height(20));
        opsInfo.textStyle(s -> s.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        nestedPowerParams.addChild(opsInfo);
    }

    private UIElement paramInput(ParamSpec param, Map<String, String> store) {
        if (param.kind == Kind.REGISTRY) {
            // searchable creative-style picker over a full registry
            // (options[0] = "item" | "entity" | "effect")
            var picker = new RegistryPicker(RegistryPicker.Kind.valueOf(
                    param.options[0].toUpperCase(Locale.ROOT)));
            picker.layout(l -> l.flex(1).height(18));
            picker.setValue(store.getOrDefault(param.key, param.defaultValue), false);
            picker.setOnValueChanged(v -> store.put(param.key, v == null ? "" : v));
            return picker;
        }
        if (param.kind == Kind.OPTION) {
            var selector = new Selector<String>();
            selector.layout(l -> l.flex(1).height(18));
            selector.setCandidates(List.of(param.options));
            String current = store.getOrDefault(param.key, param.defaultValue);
            if (!List.of(param.options).contains(current)) {
                current = param.defaultValue;
            }
            selector.setSelected(current, false);
            selector.setCandidateUIProvider(value -> {
                var item = new Label().setText(value == null ? Component.empty()
                        : optionLabel(value));
                item.textStyle(style -> style.fontSize(9));
                return item;
            });
            selector.setOnValueChanged(v -> store.put(param.key, v));
            return selector;
        }
        if (param.kind == Kind.TOGGLE) {
            var toggle = new Switch();
            toggle.layout(l -> l.height(18).alignSelf(AlignItems.CENTER));
            toggle.setOn(Boolean.parseBoolean(store.getOrDefault(param.key, param.defaultValue)));
            toggle.setOnSwitchChanged(on -> store.put(param.key, Boolean.toString(on)));
            return toggle;
        }
        boolean isInt = param.kind == Kind.INT;
        return numberField(param.key, store, isInt, isInt ? -100000 : -100000.0, isInt ? 100000 : 100000.0);
    }

    private TextField numberField(String key, Map<String, String> store, boolean isInt, double min, double max) {
        var field = new TextField();
        field.style(s -> s.background(new ColorRectTexture(UiPalette.DEEP_BG)));
        field.layout(l -> l.flex(1).height(18));
        if (isInt) {
            field.setNumbersOnlyInt((int) min, (int) max);
        } else {
            field.setNumbersOnlyDouble(min, max);
        }
        String initial = store.getOrDefault(key, isInt ? "0" : "0.0");
        field.setText(initial);
        field.setTextResponder(v -> store.put(key, v));
        return field;
    }

    private UIElement optionField(String key, Map<String, String> store, String defaultValue, String[] labels) {
        var selector = new Selector<String>();
        selector.layout(l -> l.flex(1).height(18));
        selector.setCandidates(List.of(labels));
        int def = parseInt(store.getOrDefault(key, defaultValue), 0);
        if (labels.length == 0) {
            selector.setSelected(null, false);
        } else {
            selector.setSelected(labels[Math.max(0, Math.min(def, labels.length - 1))], false);
        }
        selector.setCandidateUIProvider(value -> {
            var item = new Label().setText(value == null ? Component.empty() : Component.literal(value));
            item.textStyle(style -> style.fontSize(9));
            return item;
        });
        selector.setOnValueChanged(v -> {
            String slotNum = v != null && v.startsWith("Slot ") ? v.substring("Slot ".length()) : v;
            store.put(key, slotNum == null ? "" : slotNum);
        });
        return selector;
    }

    private void addPower() {
        PowerTypeSpec spec = typePicker.getValue();
        if (spec == null) {
            spec = PowerTypeSpec.ATTRIBUTE;
        }
        PowerDraft power = new PowerDraft(spec.type);
        power.name = newPowerValues.getOrDefault("display_name", "");
        if (power.name.trim().isEmpty()) {
            power.name = Component.translatable(spec.labelKey).getString();
        }
        power.description = newPowerValues.getOrDefault("description", "");
        for (ParamSpec param : spec.params) {
            String value = newPowerValues.getOrDefault(param.key, param.defaultValue);
            if (!value.isEmpty()) {
                power.values.put(param.key, value);
            }
        }
        String difficulty = newPowerValues.getOrDefault("difficulty", "0");
        power.values.put("difficulty", difficulty.isEmpty() ? "0" : difficulty);
        if (spec.bound) {
            String slot = newPowerValues.getOrDefault("bind_slot", "0");
            power.values.put("bind_slot", slot.isEmpty() ? "0" : slot);
            String cost = newPowerValues.getOrDefault("cost", "0");
            power.values.put("cost", cost.isEmpty() ? "0" : cost);
        }
        if (spec == PowerTypeSpec.CONDITIONAL) {
            power.nested = copyDraft(nestedPower);
            // copy EVERYTHING the condition editors wrote (subN_* slots, plus
            // per-type params like item/phase/min/radius that are not part of
            // spec.params) — otherwise freshly created conditionals lose data
            for (var entry : newPowerValues.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    power.values.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }
        int conflict = conflictingSlot(power);
        if (conflict >= 0) {
            message(Component.translatable("originsx.creator.power.slot.conflict", conflict)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (editingPowerIndex >= 0 && editingPowerIndex < powers.size()) {
            powers.set(editingPowerIndex, power);
        } else {
            powers.add(power);
        }
        resetPowerForm();
        refreshPowersList();
        refreshPreview();
    }

    private void editPower(int index) {
        PowerDraft power = powers.get(index);
        if (power.reference != null) {
            return;
        }
        editingPowerIndex = index;
        if (newPowerSearch != null && !newPowerSearch.getValue().isEmpty()) {
            newPowerSearch.setText("");
        }
        newPowerValues.clear();
        newPowerValues.putAll(power.values);
        newPowerValues.put("display_name", power.name);
        newPowerValues.put("description", power.description);
        newPowerName.setText(power.name);
        newPowerDescription.setText(power.description);
        PowerTypeSpec spec = PowerTypeSpec.byType(power.type);
        typePicker.setValue(spec, false);
        if (spec == PowerTypeSpec.CONDITIONAL && power.nested != null) {
            nestedPower.type = power.nested.type;
            nestedPower.values.clear();
            nestedPower.values.putAll(power.nested.values);
        } else {
            nestedPower.type = "attribute";
            nestedPower.values.clear();
        }
        refreshParams();
        updateAddButton();
    }

    private void resetPowerForm() {
        editingPowerIndex = -1;
        newPowerValues.clear();
        newPowerName.setText("");
        newPowerDescription.setText("");
        nestedPower.type = "attribute";
        nestedPower.values.clear();
        updateAddButton();
    }

    private void updateAddButton() {
        if (addButton != null) {
            addButton.setText(editingPowerIndex >= 0 ? "originsx.creator.update" : "originsx.creator.add");
        }
    }

    private void removePower(int index) {
        powers.remove(index);
        if (editingPowerIndex == index) {
            editingPowerIndex = -1;
        } else if (editingPowerIndex > index) {
            editingPowerIndex--;
        }
        refreshPowersList();
        refreshPreview();
    }

    /** The explicit active-slot a draft uses, or -1 if it has none. */
    private static int draftSlot(PowerDraft power) {
        String slot = power.values.get("bind_slot");
        if (slot == null || slot.isEmpty()) {
            return -1;
        }
        return parseInt(slot, -1);
    }

    /**
     * Returns the slot that would collide with another bound power already in
     * the list, or -1 when the candidate can be added safely. The power being
     * edited is skipped so it can keep its own slot.
     */
    private int conflictingSlot(PowerDraft candidate) {
        int slot = draftSlot(candidate);
        if (slot < 0) {
            return -1;
        }
        for (int i = 0; i < powers.size(); i++) {
            if (i == editingPowerIndex) {
                continue;
            }
            if (draftSlot(powers.get(i)) == slot) {
                return slot;
            }
        }
        return -1;
    }

    /** First active slot claimed by two or more powers, or -1. */
    private int firstSlotConflict() {
        for (int i = 0; i < powers.size(); i++) {
            int slot = draftSlot(powers.get(i));
            if (slot < 0) {
                continue;
            }
            for (int j = i + 1; j < powers.size(); j++) {
                if (draftSlot(powers.get(j)) == slot) {
                    return slot;
                }
            }
        }
        return -1;
    }

    /** Whether more than one power in the draft claims the given slot. */
    private boolean slotIsConflicted(int slot) {
        int count = 0;
        for (PowerDraft power : powers) {
            if (draftSlot(power) == slot) {
                count++;
            }
        }
        return count > 1;
    }

    private void refreshPowersList() {
        powersList.clearAllChildren();
        refreshBalanceMeter();
        for (int i = 0; i < powers.size(); i++) {
            PowerDraft power = powers.get(i);
            String slotInfo = power.values.get("bind_slot");
            int slotIdx = -1;
            if (slotInfo != null && !slotInfo.isEmpty()) {
                try {
                    slotIdx = Integer.parseInt(slotInfo);
                } catch (NumberFormatException ignored) {
                }
            }
            boolean bound = slotIdx >= 0 && slotIdx < OriginsXClient.MAX_ACTIVE_SLOTS;
            int diff = parseInt(power.values.getOrDefault("difficulty", "0"), 0);
            var entry = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2)
                    .paddingAll(2));
            var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                    .gapAll(6).alignItems(AlignItems.CENTER));
            var name = new Label();
            String display = (i + 1) + ". " + displayName(power);
            // buffs/debuffs are marked right in the list: an up/down arrow
            // before the name (color follows below)
            if (!bound && diff > 0) {
                display = "▲ " + display;
            } else if (!bound && diff < 0) {
                display = "▼ " + display;
            }
            if (bound) {
                display += " [Slot " + slotIdx + "]";
            }
            name.setText(Component.literal(display));
            if (!bound && diff != 0) {
                display += diff > 0 ? " [" + Component.translatable(
                        "originsx.creator.tag.buff").getString() + "]"
                        : " [" + Component.translatable(
                        "originsx.creator.tag.debuff").getString() + "]";
                name.setText(Component.literal(display));
            }
            // ARGB: without the FF alpha byte the name renders fully transparent
            int color = diff < 0 ? 0xFFFF5555 : 0xFF55FF55;
            if (bound) {
                // Active (keybind-bound) powers are always white so they stand
                // out in the list; conflicts still get red.
                color = slotIsConflicted(slotIdx) ? 0xFFFF5555 : 0xFFFFFFFF;
            }
            final int finalColor = color;
            name.layout(l -> l.flex(1));
            name.textStyle(s -> s.fontSize(9).textColor(finalColor));
            final int index = i;
            row.addChild(name);
            if (power.reference == null && power.rawJson == null) {
                var edit = new Button();
                edit.layout(l -> l.width(34).height(16));
                edit.setText("originsx.creator.edit");
                edit.textStyle(s -> s.fontSize(7));
                edit.setOnClick(e -> editPower(index));
                row.addChild(edit);
            }
            var remove = new Button();
            remove.layout(l -> l.width(46).height(16));
            remove.setText("originsx.creator.remove");
            remove.textStyle(s -> s.fontSize(7));
            remove.setOnClick(e -> removePower(index));
            row.addChild(remove);
            entry.addChild(row);
            if (power.reference != null) {
                var ref = new Label().setText(Component.literal(power.reference));
                ref.textStyle(s -> s.fontSize(7));
                entry.addChild(ref);
            }
            String descText = powerDescriptionText(power);
            if (!descText.isEmpty()) {
                boolean isDefault = power.description.isEmpty();
                var desc = new Label().setText(Component.literal(descText));
                desc.textStyle(s -> s.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true));
                entry.addChild(desc);
            }
            powersList.addChild(entry);
        }
    }

    /**
     * The description shown under a power in the list: the typed one, or the
     * translated default for the power's type, so powers without a custom
     * description always display something instead of nothing.
     */
    private static String powerDescriptionText(PowerDraft power) {
        if (power.reference != null) {
            return "";
        }
        if (!power.description.isEmpty()) {
            return power.description;
        }
        PowerTypeSpec spec = PowerTypeSpec.byType(power.type);
        String text = Component.translatable(spec.descKey).getString();
        return text.equals(spec.descKey) ? "" : text;
    }

    private String displayName(PowerDraft power) {
        if (power.reference != null) {
            Identifier ref = Identifier.tryParse(power.reference);
            if (ref != null) {
                Power resolved = PowerRegistry.createOrNull(ref);
                if (resolved != null) {
                    String name = resolved.getDisplayName().getString();
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            }
            return power.reference;
        }
        String type = power.type.replace('_', ' ');
        if (power.name.isEmpty()) {
            String key = "originsx.creator.type." + power.type;
            String translated = Component.translatable(key).getString();
            if (!translated.equals(key)) {
                return translated;
            }
            return type;
        }
        return power.name;
    }

    // ---------------------------------------------------------------- export

    /**
     * Heuristic strength estimate per power type (negative = strong, positive
     * = weakness). Combined with the manual difficulty values it feeds the
     * balance meter, so a race full of flight/summon/immunity powers no longer
     * reads as "balanced" just because the author left every difficulty at 0.
     */
    private static final Map<String, Integer> POWER_TYPE_WEIGHTS = Map.ofEntries(
            Map.entry("flight", -4), Map.entry("creative_flight", -4),
            Map.entry("damage_immunity", -3),
            Map.entry("summon", -3),
            Map.entry("phase_dash", -2), Map.entry("teleport_strike", -2),
            Map.entry("shadow_step", -2), Map.entry("size_control", -2),
            Map.entry("lifesteal", -2), Map.entry("life_link", -2),
            Map.entry("kinetic_slam", -2), Map.entry("gravity_pulse", -2),
            Map.entry("overdrive", -2), Map.entry("earthquake", -2),
            Map.entry("blink", -1), Map.entry("dash", -1), Map.entry("double_jump", -1),
            Map.entry("wall_jump", -1), Map.entry("spider_climb", -1), Map.entry("high_jump", -1),
            Map.entry("magnet", -1), Map.entry("reach", -1), Map.entry("step_height", -1),
            Map.entry("detector", -1), Map.entry("aqua_haste", -1), Map.entry("purified", -1),
            Map.entry("toughness", -1), Map.entry("thorns", -1), Map.entry("heavy_hitter", -1),
            Map.entry("kinetic_counter", -1), Map.entry("magnetic_hook", -1),
            Map.entry("disarm_wave", -1), Map.entry("life_tether", -1),
            Map.entry("fire_aura", -1), Map.entry("frost_aura", -1),
            Map.entry("light_sensitive", 2), Map.entry("metal_intolerance", 2),
            Map.entry("ravenous", 2), Map.entry("inverse_regeneration", 2),
            Map.entry("airborne_fragility", 2), Map.entry("thermal_shock", 2),
            Map.entry("action_restriction", 2), Map.entry("directional_exposure", 1),
            Map.entry("hyper_inertia", 1), Map.entry("density_anchor", 1));

    /** Heuristic character of a power type: negative = buff, positive = weakness. */
    static int powerTypeWeight(String type) {
        return POWER_TYPE_WEIGHTS.getOrDefault(type, 0);
    }

    private UIElement buildBalanceMeter() {
        var row = new UIElement().layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .gapAll(6).alignItems(AlignItems.CENTER));
        var title = new Label().setText("originsx.creator.balance");
        // adaptiveWidth: without it the label clips to a single letter
        title.textStyle(s -> s.fontSize(9).adaptiveWidth(true).textColor(UiPalette.TEXT_HINT));
        title.style(s -> s.tooltips("originsx.creator.balance.hint"));

        UIElement bar = new UIElement().layout(l -> l.width(120).height(8));
        bar.style(s -> s.background(new ColorRectTexture(0xFF151a22)));
        // center notch
        UIElement notch = new UIElement().layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .left(59).top(0).width(2).heightPercent(100));
        notch.style(s -> s.background(new ColorRectTexture(0xFF3a4552)));
        balanceFill = new UIElement();
        bar.addChildren(notch, balanceFill);
        balanceBar = bar;

        balanceVerdict = new Label();
        balanceVerdict.textStyle(s -> s.fontSize(9).adaptiveWidth(true));
        balanceVerdict.style(s -> s.tooltips("originsx.creator.balance.hint"));

        row.addChildren(title, bar, balanceVerdict);
        return row;
    }

    /**
     * Live balance readout above the power list: manual difficulty values plus
     * a heuristic weight per power type. Negative = stronger than baseline
     * (needs weaknesses), positive = weaker. |score| &le; 2 counts as balanced.
     * <p>
     * Conditional powers count partially: a power restricted by a condition is
     * worth a fraction of its full weight ({@link #conditionFactor}), so
     * "flight only at night" no longer scores like permanent flight.
     */
    private void refreshBalanceMeter() {
        if (balanceFill == null || balanceVerdict == null) {
            return;
        }
        int score = 0;
        for (PowerDraft power : powers) {
            score += Math.round(powerScore(power, 1f));
        }
        String verdictKey = score < -2 ? "originsx.creator.balance.imba"
                : score > 2 ? "originsx.creator.balance.weak"
                : "originsx.creator.balance.ok";
        int color = score < -2 ? 0xFFFF5555 : score > 2 ? 0xFFFFFF55 : 0xFF55FF55;

        int max = Math.max(12, powers.size() * 4);
        int fillWidth = Math.round(59f * Math.min(1f, Math.abs(score) / (float) max));
        int fillLeft = score < 0 ? 59 - fillWidth : 60;
        // no stray 1px sliver at zero: detach the fill entirely when balanced
        if (score == 0) {
            if (balanceFill.getParent() != null) {
                balanceFill.getParent().removeChild(balanceFill);
            }
        } else {
            balanceFill.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                    .left(fillLeft).top(0).width(fillWidth).heightPercent(100));
            balanceFill.style(s -> s.background(new ColorRectTexture(color)));
            if (balanceFill.getParent() == null && balanceBar != null) {
                balanceBar.addChild(balanceFill);
            }
        }
        balanceVerdict.setText(Component.translatable(verdictKey));
        balanceVerdict.textStyle(s -> s.fontSize(9).adaptiveWidth(true).textColor(color));
    }

    /**
     * Heuristic score of one power (manual difficulty + type weight), scaled
     * by the parent condition factor. Conditional powers recurse into their
     * inner power with a reduced factor: each restricting condition halves the
     * remaining weight ({@code all_of} multiplies per sub-condition, {@code
     * any_of} and a plain condition count once, {@code not} is softer), so a
     * condition chain never discounts a power below 10% of its base value.
     */
    private float powerScore(PowerDraft power, float factor) {
        if (power == null) {
            return 0;
        }
        float self = parseInt(power.values.getOrDefault("difficulty", "0"), 0)
                + POWER_TYPE_WEIGHTS.getOrDefault(power.type, 0);
        float inner = powerScore(power.nested, factor * conditionFactor(power));
        float own = (self + inner) * ("conditional".equals(power.type) ? 1f : factor);
        return own;
    }

    /** How much a conditional power's weight survives its condition. */
    private float conditionFactor(PowerDraft power) {
        if (power == null || !"conditional".equals(power.type)) {
            return 1f;
        }
        String conditionType = power.values.getOrDefault("condition_type", "");
        switch (conditionType) {
            case "all_of": {
                int subs = 0;
                for (int i = 1; i <= 3; i++) {
                    String subType = power.values.get("sub" + i + "_type");
                    if (subType != null && !subType.isEmpty()) {
                        subs++;
                    }
                }
                return (float) Math.max(0.1, Math.pow(0.5, Math.max(1, subs)));
            }
            case "not":
                return 0.75f;
            default:
                // a plain condition or any_of: roughly "true about half the time"
                return 0.5f;
        }
    }

    private void refreshPreview() {
        if (preview != null) {
            preview.setValue(buildJson().split("\n"));
        }
    }

    private String buildJson() {
        String name = draft.name.trim();
        String namespace = "mypack";
        String path = defaultPath();
        if (!draft.id.trim().isEmpty()) {
            Identifier id = Identifier.tryParse(draft.id.trim());
            if (id != null) {
                namespace = id.getNamespace();
                path = id.getPath();
            }
        } else {
            String datapackName = draft.datapackName.trim();
            if (!datapackName.isEmpty()) {
                path = slug(datapackName);
            } else if (!name.isEmpty()) {
                path = slug(name);
            }
        }

        JsonObject root = editingOriginalJson != null ? editingOriginalJson.deepCopy() : new JsonObject();
        // If no display name was entered, fall back to the datapack name so the
        // race doesn't show an ugly id tag in the list.
        String display = !name.isEmpty() ? name : draft.datapackName.trim();
        root.addProperty("display_name", display.isEmpty() ? path : display);
        if (!draft.description.trim().isEmpty()) {
            root.addProperty("description", draft.description.trim());
        } else {
            root.remove("description");
        }
        root.addProperty("icon", draft.icon.trim().isEmpty() ? "minecraft:feather" : draft.icon.trim());
        root.addProperty("difficulty", parseInt(draft.difficulty, 0));
        double scale = parseDouble(draft.scale, 1.0);
        scale = Math.max(0.25, Math.min(4.0, scale));
        root.addProperty("scale", scale);
        JsonArray powersArray = new JsonArray();
        for (PowerDraft power : powers) {
            powersArray.add(power.toJson());
        }
        root.add("powers", powersArray);
        return PRETTY.toJson(root);
    }

    private String packName() {
        String datapackName = draft.datapackName.trim();
        if (!datapackName.isEmpty()) {
            return slug(datapackName) + "_race";
        }
        String name = draft.name.trim();
        String base = name.isEmpty() ? defaultPath() : slug(name);
        return base + "_race";
    }

    private void exportToDatapack() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            message("originsx.creator.save.need.singleplayer");
            return;
        }
        int conflict = firstSlotConflict();
        if (conflict >= 0) {
            message(Component.translatable("originsx.creator.power.slot.conflict", conflict)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        try {
            Path packDir = editPackDir != null
                    ? editPackDir
                    : server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(packName());
            Path dataDir = packDir.resolve("data");
            String json = buildJson();
            Identifier id = raceId();
            Path racesDir = dataDir.resolve(id.getNamespace()).resolve("raceapi/races");
            if ((editingOriginalId == null || !editingOriginalId.equals(id))
                    && Files.exists(racesDir.resolve(id.getPath() + ".json"))) {
                String basePath = id.getPath();
                int suffix = 2;
                while (Files.exists(racesDir.resolve(basePath + "_" + suffix + ".json"))) {
                    suffix++;
                }
                id = Identifier.fromNamespaceAndPath(id.getNamespace(), basePath + "_" + suffix);
                draft.id = id.toString();
                if (idField != null) {
                    idField.setText(draft.id);
                }
                message(Component.translatable("originsx.creator.save.id.taken", id.toString()));
            }
            Path raceFile = racesDir.resolve(id.getPath() + ".json");
            Files.createDirectories(raceFile.getParent());
            Files.writeString(raceFile, json, StandardCharsets.UTF_8);

            if (editing && editingOriginalId != null && !editingOriginalId.equals(id)) {
                Path oldFile = dataDir.resolve(editingOriginalId.getNamespace()).resolve("raceapi/races")
                        .resolve(editingOriginalId.getPath() + ".json");
                Files.deleteIfExists(oldFile);
            }

            Path meta = packDir.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                String mcmeta = "{\n  \"pack\": {\n    \"description\": \"" + escape(packName())
                        + "\",\n    \"pack_format\": " + PACK_FORMAT_1_21_1 + "\n  }\n}\n";
                Files.writeString(meta, mcmeta, StandardCharsets.UTF_8);
            }
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), "reload"));
            if (editing) {
                // show the toast, THEN close with a short delay: closing at
                // once would kill the toast together with the screen
                message(Component.translatable("originsx.creator.save.updated", id.toString()));
                closeScreenLater(40);
            } else {
                message(Component.translatable("originsx.creator.save.done", id.toString()));
            }
        } catch (IOException e) {
            message(Component.translatable("originsx.creator.save.failed").withStyle(ChatFormatting.RED));
        }
    }

    private void copyJson() {
        int conflict = firstSlotConflict();
        if (conflict >= 0) {
            message(Component.translatable("originsx.creator.power.slot.conflict", conflict)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(buildJson());
        message("originsx.creator.copy.done");
    }

    /**
     * Copies a compact share string (gzip+base64 of the race JSON) to the
     * clipboard so the race can be imported by another player via
     * "Импорт расы" in the selection screen.
     */
    private void shareRace() {
        int conflict = firstSlotConflict();
        if (conflict >= 0) {
            message(Component.translatable("originsx.creator.power.slot.conflict", conflict)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(buildJson()).getAsJsonObject();
            String share = dev.originsx.share.RaceShare.encode(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(share);
            message("originsx.creator.share.done");
        } catch (Exception e) {
            message(Component.translatable("originsx.creator.share.failed")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Loads an existing race definition into the form for editing. {@code id}
     * is the full {@code namespace:path} of the race, {@code packDir} the world
     * datapack folder it lives in, so re-export overwrites the same file.
     */
    public void load(String id, JsonObject root, Path packDir) {
        editing = true;
        editPackDir = packDir;
        editingOriginalId = Identifier.tryParse(id);
        editingOriginalJson = root.deepCopy();
        generatedDefaultPath = null;

        draft.name = stringOr(root, "display_name", "");
        draft.id = id;
        draft.datapackName = "";
        draft.icon = stringOr(root, "icon", "minecraft:feather");
        draft.description = stringOr(root, "description", "");
        draft.difficulty = String.valueOf(intOr(root, "difficulty", 0));
        draft.scale = String.valueOf(doubleOr(root, "scale", 1.0));

        powers.clear();
        if (root.has("powers") && root.get("powers").isJsonArray()) {
            for (com.google.gson.JsonElement element : root.getAsJsonArray("powers")) {
                if (element.isJsonPrimitive()) {
                    PowerDraft power = new PowerDraft("");
                    power.reference = element.getAsString();
                    powers.add(power);
                    continue;
                }
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject powerJson = element.getAsJsonObject();
                String type = stringOr(powerJson, "type", "attribute");
                if (type.startsWith("raceapi:")) {
                    type = type.substring("raceapi:".length());
                }
                PowerDraft power = new PowerDraft(type);
                if (PowerTypeSpec.byTypeOrNull(type) == null) {
                    // unknown (another mod's) power type: keep the raw JSON so
                    // it exports untouched instead of degrading into attribute
                    power.rawJson = powerJson.deepCopy();
                    powers.add(power);
                    continue;
                }
                power.name = stringOr(powerJson, "display_name", "");
                power.description = stringOr(powerJson, "description", "");
                for (ParamSpec param : PowerTypeSpec.byType(type).params) {
                    if (powerJson.has(param.key)) {
                        com.google.gson.JsonElement value = powerJson.get(param.key);
                        power.values.put(param.key, value.isJsonPrimitive()
                                ? value.getAsString() : value.toString());
                    }
                }
                if (type.equals("conditional")) {
                    for (String key : conditionParamKeys(stringOr(powerJson, "condition_type", ""))) {
                        if (powerJson.has(key) && powerJson.get(key).isJsonPrimitive()) {
                            power.values.put(key, powerJson.get(key).getAsString());
                        }
                    }
                    loadConditionals(powerJson, power);
                }
                if (powerJson.has("difficulty")) {
                    power.values.put("difficulty", powerJson.get("difficulty").getAsString());
                }
                if (powerJson.has("bind_slot")) {
                    power.values.put("bind_slot", powerJson.get("bind_slot").getAsString());
                }
                if (powerJson.has("cost") && powerJson.get("cost").isJsonPrimitive()) {
                    power.values.put("cost", powerJson.get("cost").getAsString());
                }
                if (type.equals("conditional") && powerJson.has("power") && powerJson.get("power").isJsonObject()) {
                    JsonObject inner = powerJson.getAsJsonObject("power");
                    String innerType = stringOr(inner, "type", "attribute");
                    if (innerType.startsWith("raceapi:")) {
                        innerType = innerType.substring("raceapi:".length());
                    }
                    PowerDraft nested = new PowerDraft(innerType);
                    nested.name = stringOr(inner, "display_name", "");
                    nested.description = stringOr(inner, "description", "");
                    if (PowerTypeSpec.byTypeOrNull(innerType) == null) {
                        nested.rawJson = inner.deepCopy();
                    } else {
                        for (ParamSpec param : PowerTypeSpec.byType(innerType).params) {
                            if (inner.has(param.key)) {
                                com.google.gson.JsonElement value = inner.get(param.key);
                                nested.values.put(param.key, value.isJsonPrimitive()
                                        ? value.getAsString() : value.toString());
                            }
                        }
                        if (inner.has("difficulty")) {
                            nested.values.put("difficulty", inner.get("difficulty").getAsString());
                        }
                        if (inner.has("bind_slot")) {
                            nested.values.put("bind_slot", inner.get("bind_slot").getAsString());
                        }
                    }
                    power.nested = nested;
                }
                powers.add(power);
            }
        }

        nameField.setText(draft.name);
        idField.setText(draft.id);
        datapackNameField.setText(draft.datapackName);
        iconField.setText(draft.icon);
        difficultyField.setText(draft.difficulty);
        scaleField.setText(draft.scale);
        if (descriptionArea != null) {
            descriptionArea.setLines(List.of(draft.description.split("\n")));
        }

        resetPowerForm();
        refreshTypeCandidates();
        refreshPowersList();
        refreshPreview();
    }

    /** Clears the form and starts a brand-new race. */
    public void resetForm() {
        editing = false;
        editPackDir = null;
        editingOriginalId = null;
        editingOriginalJson = null;
        generatedDefaultPath = null;

        draft.name = "";
        draft.id = "";
        draft.datapackName = "";
        draft.icon = "minecraft:feather";
        draft.description = "";
        draft.difficulty = "0";
        draft.scale = "1.0";
        powers.clear();

        nameField.setText("");
        idField.setText("");
        datapackNameField.setText("");
        iconField.setText("minecraft:feather");
        difficultyField.setText("0");
        scaleField.setText("1.0");
        if (descriptionArea != null) {
            descriptionArea.setLines(List.of());
        }

        resetPowerForm();
        refreshTypeCandidates();
        refreshPowersList();
        refreshPreview();
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : fallback;
    }

    /**
     * The parameter keys a given condition type reads from JSON (mirrors the
     * rows built by {@link #addConditionParamRows}). Parameterless conditions
     * return an empty list.
     */
    private static List<String> conditionParamKeys(String type) {
        return switch (type) {
            case "has_effect" -> List.of("effect");
            case "in_biome" -> List.of("tag");
            case "in_dimension" -> List.of("dimension");
            case "entities_nearby_above", "entities_nearby_below" -> List.of("threshold", "radius");
            case "held_item", "offhand_item", "wearing_item" -> List.of("slot", "item", "item_tag");
            case "standing_on" -> List.of("block", "block_tag");
            case "biome_id" -> List.of("biome");
            case "moon_phase" -> List.of("phase");
            case "time_between" -> List.of("min", "max");
            case "gamemode" -> List.of("mode");
            case "riding" -> List.of("entity");
            case "scoreboard_above", "scoreboard_below" -> List.of("objective", "threshold");
            default -> List.of();
        };
    }

    /**
     * Restores composite conditions ("conditions" array / "inner" object)
     * into flat "subN_" form values so the editor can re-edit them.
     */
    private static void loadConditionals(JsonObject powerJson, PowerDraft power) {
        String conditionType = stringOr(powerJson, "condition_type", "");
        if ("all_of".equals(conditionType) || "any_of".equals(conditionType)) {
            if (powerJson.has("conditions") && powerJson.get("conditions").isJsonArray()) {
                int index = 1;
                for (com.google.gson.JsonElement element : powerJson.getAsJsonArray("conditions")) {
                    if (element.isJsonObject() && index <= 3) {
                        loadSubCondition(element.getAsJsonObject(), power, index++);
                    }
                }
            }
        } else if ("not".equals(conditionType)) {
            if (powerJson.has("inner") && powerJson.get("inner").isJsonObject()) {
                loadSubCondition(powerJson.getAsJsonObject("inner"), power, 1);
            }
        }
    }

    private static void loadSubCondition(JsonObject sub, PowerDraft power, int index) {
        String prefix = "sub" + index + "_";
        String type = stringOr(sub, "condition_type", "in_water");
        power.values.put(prefix + "type", type);
        for (String key : conditionParamKeys(type)) {
            if (sub.has(key) && sub.get(key).isJsonPrimitive()) {
                power.values.put(prefix + key, sub.get(key).getAsString());
            }
        }
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive()
                    ? json.get(key).getAsInt() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleOr(JsonObject json, String key, double fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive()
                    ? json.get(key).getAsDouble() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Current draft race id as a string (for the skill tree editor). */
    public String currentRaceId() {
        return raceId().toString();
    }

    /**
     * JSON array of the draft's powers as {@code [[index, name, powerJson], ...]}
     * so the skill tree editor can offer a picker instead of manual index/JSON
     * typing.
     */
    public String currentPowersJson() {
        var array = new com.google.gson.JsonArray();
        for (int i = 0; i < powers.size(); i++) {
            PowerDraft draft = powers.get(i);
            String name = draft.name == null || draft.name.isEmpty()
                    ? translatedTypeName(draft.type) : draft.name;
            var entry = new com.google.gson.JsonArray();
            entry.add(i);
            entry.add(name);
            JsonElement json = draft.toJson();
            entry.add(json.isJsonObject() ? json : new JsonObject());
            array.add(entry);
        }
        return array.toString();
    }

    /** Translated power type name ("Атрибут"); raw type when untranslated. */
    private static String translatedTypeName(String type) {
        String key = "originsx.creator.type." + type;
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? type : translated;
    }

    private Identifier raceId() {
        if (!draft.id.trim().isEmpty()) {
            Identifier id = Identifier.tryParse(draft.id.trim());
            if (id != null) {
                return id;
            }
        }
        String datapackName = draft.datapackName.trim();
        if (!datapackName.isEmpty()) {
            return Identifier.fromNamespaceAndPath("mypack", slug(datapackName));
        }
        String name = draft.name.trim();
        String path = name.isEmpty() ? defaultPath() : slug(name);
        return Identifier.fromNamespaceAndPath("mypack", path);
    }

    private String defaultPath() {
        if (generatedDefaultPath == null) {
            generatedDefaultPath = "custom_race_" + Long.toHexString(System.currentTimeMillis())
                    + "_" + Integer.toHexString((int) (Math.random() * 0x10000));
        }
        return generatedDefaultPath;
    }

    private static void message(String key) {
        message(Component.translatable(key));
    }

    private static void message(Component text) {
        UiToaster.show(text);
    }

    /** Closes the current screen after {@code frames} rendered frames, so a toast has time to be seen. */
    private static void closeScreenLater(int frames) {
        if (frames <= 0) {
            Minecraft.getInstance().setScreenAndShow(null);
            return;
        }
        Minecraft.getInstance().execute(() -> closeScreenLater(frames - 1));
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String slug(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static PowerDraft copyDraft(PowerDraft source) {
        if (source == null) {
            return null;
        }
        PowerDraft copy = new PowerDraft(source.type);
        copy.name = source.name;
        copy.description = source.description;
        copy.reference = source.reference;
        copy.rawJson = source.rawJson;
        copy.values.putAll(source.values);
        copy.nested = copyDraft(source.nested);
        return copy;
    }

    // ---------------------------------------------------------------- models

    private static class RaceDraft {
        private String name = "";
        private String datapackName = "";
        private String id = "";
        private String icon = "minecraft:feather";
        private String description = "";
        private String difficulty = "0";
        private String scale = "1.0";
    }

    private static class PowerDraft {
        private String type;
        private String name = "";
        private String description = "";
        @Nullable
        private String reference;
        @Nullable
        private JsonObject rawJson;
        private final Map<String, String> values = new HashMap<>();
        @Nullable
        private PowerDraft nested;

        private PowerDraft(String type) {
            this.type = type;
        }

        /**
         * One sub-condition slot ("subN_type" + params) as engine JSON;
         * {@code null} when the slot has no type selected.
         */
        private JsonObject subConditionJson(int index) {
            String prefix = "sub" + index + "_";
            String subType = values.get(prefix + "type");
            if (subType == null || subType.isEmpty()) {
                return null;
            }
            JsonObject sub = new JsonObject();
            sub.addProperty("condition_type", subType);
            for (String key : conditionParamKeys(subType)) {
                String value = values.get(prefix + key);
                if (value == null || value.isEmpty()) {
                    continue;
                }
                if ("threshold".equals(key) || "radius".equals(key)) {
                    addTyped(sub, key, value, Kind.DOUBLE);
                } else if ("min".equals(key) || "max".equals(key)) {
                    addTyped(sub, key, value, Kind.INT);
                } else {
                    sub.addProperty(key, value);
                }
            }
            return sub;
        }

        private JsonElement toJson() {
            if (reference != null) {
                return new JsonPrimitive(reference);
            }
            if (rawJson != null) {
                return rawJson.deepCopy();
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "raceapi:" + type);
            if (!name.isEmpty()) {
                obj.addProperty("display_name", name);
            }
            if (!description.isEmpty()) {
                obj.addProperty("description", description);
            }
            if (type.equals("conditional")) {
                String conditionType = values.get("condition_type");
                if (conditionType != null && !conditionType.isEmpty()) {
                    obj.addProperty("condition_type", conditionType);
                }
                String conditionLabel = values.get("condition_label");
                if (conditionLabel != null && !conditionLabel.isEmpty()) {
                    obj.addProperty("condition_label", conditionLabel);
                }
                if ("all_of".equals(conditionType) || "any_of".equals(conditionType)) {
                    JsonArray conditions = new JsonArray();
                    for (int i = 1; i <= 3; i++) {
                        JsonObject sub = subConditionJson(i);
                        if (sub != null) {
                            conditions.add(sub);
                        }
                    }
                    obj.add("conditions", conditions);
                } else if ("not".equals(conditionType)) {
                    JsonObject inner = subConditionJson(1);
                    obj.add("inner", inner != null ? inner : new JsonObject());
                } else {
                    // write every non-empty form value except the keys handled
                    // above and the sub-condition slots: covers all 50
                    // condition types and their parameters (item, phase, min,
                    // radius, objective, ...). Unknown leftovers are ignored
                    // by RaceAPI, so stale params from a switched condition
                    // type are harmless.
                    for (var entry : values.entrySet()) {
                        String key = entry.getKey();
                        if (key.isEmpty() || key.equals("condition_type")
                                || key.equals("condition_label") || key.startsWith("sub")) {
                            continue;
                        }
                        String value = entry.getValue();
                        if (value == null || value.isEmpty()) {
                            continue;
                        }
                        if ("threshold".equals(key) || "radius".equals(key)) {
                            addTyped(obj, key, value, Kind.DOUBLE);
                        } else if ("min".equals(key) || "max".equals(key)) {
                            addTyped(obj, key, value, Kind.INT);
                        } else {
                            obj.addProperty(key, value);
                        }
                    }
                }
                if (nested != null) {
                    obj.add("power", nested.toJson());
                } else {
                    obj.add("power", new JsonObject());
                }
                String difficulty = values.get("difficulty");
                addTyped(obj, "difficulty", difficulty, Kind.INT);
                return obj;
            }
            PowerTypeSpec spec = PowerTypeSpec.byType(type);
            for (ParamSpec param : spec.params) {
                String value = values.get(param.key);
                if (value == null || value.isEmpty()) {
                    continue;
                }
                addTyped(obj, param.key, value, param.kind);
            }
            String difficulty = values.get("difficulty");
            addTyped(obj, "difficulty", difficulty, Kind.INT);
            String bindSlot = values.get("bind_slot");
            if (bindSlot != null && !bindSlot.isEmpty()) {
                obj.addProperty("bind_slot", parseInt(bindSlot, 0));
            }
            PowerTypeSpec boundSpec = PowerTypeSpec.byType(type);
            if (boundSpec.bound) {
                String cost = values.getOrDefault("cost", "0").trim();
                double costValue = cost.isEmpty() ? 0.0 : parseDouble(cost, 0.0);
                if (costValue > 0) {
                    obj.addProperty("cost", costValue);
                }
            }
            return obj;
        }
    }

    private static void addTyped(JsonObject obj, String key, String value, Kind kind) {
        switch (kind) {
            case INT -> obj.addProperty(key, parseInt(value, 0));
            case DOUBLE -> obj.addProperty(key, parseDouble(value, 0.0));
            case TOGGLE -> obj.addProperty(key, Boolean.parseBoolean(value));
            default -> obj.addProperty(key, value);
        }
    }

    private enum Kind {
        INT, DOUBLE, OPTION, TOGGLE, REGISTRY
    }

    private static class ParamSpec {
        private final String key;
        private final String labelKey;
        private final Kind kind;
        private final String defaultValue;
        private final String[] options;
        private final String[] hidesWhenOn;

        private ParamSpec(String key, String labelKey, Kind kind, String defaultValue, String... options) {
            this(key, labelKey, kind, defaultValue, new String[0], options);
        }

        private ParamSpec(String key, String labelKey, Kind kind, String defaultValue, String[] hidesWhenOn,
                          String... options) {
            this.key = key;
            this.labelKey = labelKey;
            this.kind = kind;
            this.defaultValue = defaultValue;
            this.hidesWhenOn = hidesWhenOn;
            this.options = options;
        }
    }

    private enum PowerTypeSpec {
        ATTRIBUTE("attribute", "originsx.creator.type.attribute", false, List.of(
                new ParamSpec("attribute", "originsx.creator.param.attribute", Kind.OPTION, "minecraft:max_health",
                        "minecraft:max_health", "minecraft:movement_speed", "minecraft:attack_damage",
                        "minecraft:armor", "minecraft:luck", "minecraft:knockback_resistance",
                        "minecraft:entity_interaction_range",
                        "minecraft:armor_toughness", "minecraft:attack_reach", "minecraft:fly_speed",
                        "minecraft:gravity", "minecraft:block_interaction_range"),
                new ParamSpec("amount", "originsx.creator.param.amount", Kind.DOUBLE, "1.0"),
                new ParamSpec("operation", "originsx.creator.param.operation", Kind.OPTION, "add_value",
                        "add_value", "add_multiplied_base", "add_multiplied_total"),
                new ParamSpec("hidden", "originsx.creator.param.hidden", Kind.TOGGLE, "false"))),
        STATUS_EFFECT("status_effect", "originsx.creator.type.status_effect", false, List.of(
                new ParamSpec("effect", "originsx.creator.param.effect", Kind.REGISTRY, "minecraft:speed",
                        "effect"),
                new ParamSpec("amplifier", "originsx.creator.param.amplifier", Kind.INT, "1"),
                new ParamSpec("infinite", "originsx.creator.param.infinite", Kind.TOGGLE, "true",
                        new String[]{"duration", "interval"}),
                new ParamSpec("duration", "originsx.creator.param.duration", Kind.INT, "20"),
                new ParamSpec("interval", "originsx.creator.param.interval", Kind.INT, "15"),
                new ParamSpec("hidden", "originsx.creator.param.hidden", Kind.TOGGLE, "false"))),
        DASH("dash", "originsx.creator.type.dash", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "3"),
                new ParamSpec("strength", "originsx.creator.param.strength", Kind.DOUBLE, "1.8"))),
        BLINK("blink", "originsx.creator.type.blink", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "4"),
                new ParamSpec("range", "originsx.creator.param.range", Kind.DOUBLE, "10.0"))),
        SAFE_LANDING("safe_landing", "originsx.creator.type.safe_landing", false, List.of()),
        TOUGHNESS("toughness", "originsx.creator.type.toughness", false, List.of(
                new ParamSpec("reduction", "originsx.creator.param.reduction", Kind.DOUBLE, "0.25"))),
        FIRE_AURA("fire_aura", "originsx.creator.type.fire_aura", false, List.of(
                new ParamSpec("radius", "originsx.creator.param.radius", Kind.DOUBLE, "3.0"),
                new ParamSpec("fire_ticks", "originsx.creator.param.fire_ticks", Kind.INT, "3"))),
        STEP_HEIGHT("step_height", "originsx.creator.type.step_height", false, List.of(
                new ParamSpec("amount", "originsx.creator.param.amount", Kind.DOUBLE, "1.0"))),
        LIFESTEAL("lifesteal", "originsx.creator.type.lifesteal", false, List.of(
                new ParamSpec("fraction", "originsx.creator.param.fraction", Kind.DOUBLE, "0.25"))),
        DOUBLE_JUMP("double_jump", "originsx.creator.type.double_jump", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "2"),
                new ParamSpec("boost", "originsx.creator.param.boost", Kind.DOUBLE, "0.7"))),
        NIGHT_BOOST("night_boost", "originsx.creator.type.night_boost", false, List.of()),
        DAY_BOOST("day_boost", "originsx.creator.type.day_boost", false, List.of()),
        LIGHT_SENSITIVE("light_sensitive", "originsx.creator.type.light_sensitive", false, List.of()),
        RAVENOUS("ravenous", "originsx.creator.type.ravenous", false, List.of(
                new ParamSpec("exhaustion", "originsx.creator.param.exhaustion", Kind.DOUBLE, "0.5"),
                // empty by default: only written to JSON when set, otherwise the
                // engine keeps using "exhaustion"
                new ParamSpec("amplifier", "originsx.creator.param.amplifier", Kind.DOUBLE, ""))),
        WALL_JUMP("wall_jump", "originsx.creator.type.wall_jump", false, List.of()),
        SPIDER_CLIMB("spider_climb", "originsx.creator.type.spider_climb", false, List.of()),
        SPRINT_JUMP("sprint_jump", "originsx.creator.type.sprint_jump", false, List.of()),
        BOUNCY("bouncy", "originsx.creator.type.bouncy", false, List.of()),
        FROST_TOUCH("frost_touch", "originsx.creator.type.frost_touch", false, List.of()),
        VENOM_TOUCH("venom_touch", "originsx.creator.type.venom_touch", false, List.of()),
        THORNS("thorns", "originsx.creator.type.thorns", false, List.of(
                new ParamSpec("fraction", "originsx.creator.param.fraction", Kind.DOUBLE, "0.25"))),
        HEAVY_HITTER("heavy_hitter", "originsx.creator.type.heavy_hitter", false, List.of()),
        MAGNET("magnet", "originsx.creator.type.magnet", false, List.of(
                new ParamSpec("radius", "originsx.creator.param.radius", Kind.DOUBLE, "4.0"))),
        PURIFIED("purified", "originsx.creator.type.purified", false, List.of()),
        DETECTOR("detector", "originsx.creator.type.detector", false, List.of(
                new ParamSpec("radius", "originsx.creator.param.radius", Kind.DOUBLE, "6.0"))),
        AQUA_HASTE("aqua_haste", "originsx.creator.type.aqua_haste", false, List.of()),
        FROST_AURA("frost_aura", "originsx.creator.type.frost_aura", false, List.of(
                new ParamSpec("radius", "originsx.creator.param.radius", Kind.DOUBLE, "3.0"),
                new ParamSpec("slowness_duration", "originsx.creator.param.slowness_duration", Kind.INT, "3"),
                new ParamSpec("slowness_amplifier", "originsx.creator.param.slowness_amplifier", Kind.INT, "0"))),
        CONDITIONAL("conditional", "originsx.creator.type.conditional", false, List.of(
                new ParamSpec("condition_type", "originsx.creator.param.condition_type", Kind.OPTION, "in_water",
                        "in_water", "not_in_water", "on_ground", "in_air", "is_day", "is_night",
                        "is_sprinting", "is_crouching", "is_on_fire",
                        "health_below", "health_above",
                        "light_level_below", "light_level_above",
                        "has_effect", "in_biome", "in_dimension",
                        "is_swimming", "is_raining", "below_y", "above_y",
                        "is_falling", "has_armor", "is_full_health"),
                new ParamSpec("condition_label", "originsx.creator.param.condition_label", Kind.OPTION, ""),
                new ParamSpec("threshold", "originsx.creator.param.threshold", Kind.DOUBLE, "0.5"),
                new ParamSpec("effect", "originsx.creator.param.effect", Kind.REGISTRY, "minecraft:poison",
                        "effect"),
                new ParamSpec("tag", "originsx.creator.param.tag", Kind.OPTION, ""),
                new ParamSpec("dimension", "originsx.creator.param.dimension", Kind.OPTION, ""))),
        EFFECT_REMOVAL("effect_removal", "originsx.creator.type.effect_removal", false, List.of(
                new ParamSpec("effect", "originsx.creator.param.effect", Kind.REGISTRY, "minecraft:poison",
                        "effect"))),
        ACTION_RESTRICTION("action_restriction", "originsx.creator.type.action_restriction", false, List.of(
                new ParamSpec("restrict_sprint", "originsx.creator.param.restrict_sprint", Kind.OPTION, "false", "true", "false"),
                new ParamSpec("restrict_jump", "originsx.creator.param.restrict_jump", Kind.OPTION, "false", "true", "false"),
                new ParamSpec("restrict_swim", "originsx.creator.param.restrict_swim", Kind.OPTION, "false", "true", "false"),
                new ParamSpec("restrict_flight", "originsx.creator.param.restrict_flight", Kind.OPTION, "false", "true", "false"),
                new ParamSpec("restrict_attack", "originsx.creator.param.restrict_attack", Kind.OPTION, "false", "true", "false"))),
        HYPER_INERTIA("hyper_inertia", "originsx.creator.type.hyper_inertia", false, List.of(
                new ParamSpec("turn_threshold", "originsx.creator.param.turn_threshold", Kind.DOUBLE, "0.7"),
                new ParamSpec("accel_factor", "originsx.creator.param.accel_factor", Kind.DOUBLE, "0.15"))),
        DENSITY_ANCHOR("density_anchor", "originsx.creator.type.density_anchor", false, List.of(
                new ParamSpec("fall_multiplier", "originsx.creator.param.fall_multiplier", Kind.DOUBLE, "2.5"),
                new ParamSpec("sink_speed", "originsx.creator.param.sink_speed", Kind.DOUBLE, "-0.5"))),
        AIRBORNE_FRAGILITY("airborne_fragility", "originsx.creator.type.airborne_fragility", false, List.of(
                new ParamSpec("damage_multiplier", "originsx.creator.param.damage_multiplier", Kind.DOUBLE, "2.0"),
                new ParamSpec("jump_disable_ticks", "originsx.creator.param.jump_disable_ticks", Kind.INT, "2"))),
        DIRECTIONAL_EXPOSURE("directional_exposure", "originsx.creator.type.directional_exposure", false, List.of(
                new ParamSpec("multiplier", "originsx.creator.param.multiplier", Kind.DOUBLE, "1.75"),
                new ParamSpec("rear_angle", "originsx.creator.param.rear_angle", Kind.DOUBLE, "60.0"))),
        METAL_INTOLERANCE("metal_intolerance", "originsx.creator.type.metal_intolerance", false, List.of(
                new ParamSpec("penalty_per_item", "originsx.creator.param.penalty_per_item", Kind.DOUBLE, "-0.10"))),
        INVERSE_REGENERATION("inverse_regeneration", "originsx.creator.type.inverse_regeneration", false, List.of(
                new ParamSpec("damage_per_tick", "originsx.creator.param.damage_per_tick", Kind.DOUBLE, "0.5"),
                new ParamSpec("damage_tick_interval", "originsx.creator.param.damage_tick_interval", Kind.INT, "4"),
                new ParamSpec("food_threshold", "originsx.creator.param.food_threshold", Kind.INT, "18"),
                new ParamSpec("regen_punishment", "originsx.creator.param.regen_punishment", Kind.DOUBLE, "1.5"))),
        THERMAL_SHOCK("thermal_shock", "originsx.creator.type.thermal_shock", false, List.of(
                new ParamSpec("shock_cooldown", "originsx.creator.param.shock_cooldown", Kind.INT, "5"),
                new ParamSpec("ability_disable_ticks", "originsx.creator.param.ability_disable_ticks", Kind.INT, "5"),
                new ParamSpec("shock_damage", "originsx.creator.param.shock_damage", Kind.DOUBLE, "2.0"))),
        LIFE_TETHER("life_tether", "originsx.creator.type.life_tether", false, List.of(
                new ParamSpec("range", "originsx.creator.param.range", Kind.DOUBLE, "15.0"),
                new ParamSpec("health_loss_fraction", "originsx.creator.param.health_loss_fraction", Kind.DOUBLE, "0.30"),
                new ParamSpec("slowness_duration", "originsx.creator.param.slowness_duration", Kind.INT, "3"),
                new ParamSpec("slowness_amplifier", "originsx.creator.param.slowness_amplifier", Kind.INT, "1"))),
        KINETIC_SLAM("kinetic_slam", "originsx.creator.type.kinetic_slam", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "8"),
                new ParamSpec("radius", "originsx.creator.param.radius", Kind.DOUBLE, "4.0"),
                new ParamSpec("damage", "originsx.creator.param.damage", Kind.DOUBLE, "14.0"),
                new ParamSpec("self_damage_fraction", "originsx.creator.param.self_damage_fraction", Kind.DOUBLE, "0.25"),
                new ParamSpec("knockback_strength", "originsx.creator.param.knockback_strength", Kind.DOUBLE, "1.5"))),
        TIME_TRACE("time_trace", "originsx.creator.type.time_trace", true, List.of(
                new ParamSpec("auto_return_ticks", "originsx.creator.param.auto_return_ticks", Kind.INT, "3"),
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "30"))),
        PHASE_DASH("phase_dash", "originsx.creator.type.phase_dash", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "3"),
                new ParamSpec("distance", "originsx.creator.param.distance", Kind.DOUBLE, "6.0"),
                new ParamSpec("dash_velocity", "originsx.creator.param.dash_velocity", Kind.DOUBLE, "2.5"),
                new ParamSpec("contact_damage", "originsx.creator.param.contact_damage", Kind.DOUBLE, "6.0"))),
        KINETIC_COUNTER("kinetic_counter", "originsx.creator.type.kinetic_counter", true, List.of(
                new ParamSpec("parry_window", "originsx.creator.param.parry_window", Kind.DOUBLE, "0.8"),
                new ParamSpec("stun_duration", "originsx.creator.param.stun_duration", Kind.INT, "1"),
                new ParamSpec("base_cooldown", "originsx.creator.param.base_cooldown", Kind.INT, "2"),
                new ParamSpec("miss_cooldown", "originsx.creator.param.miss_cooldown", Kind.INT, "3"),
                new ParamSpec("pushback_strength", "originsx.creator.param.pushback_strength", Kind.DOUBLE, "2.0"))),
        MAGNETIC_HOOK("magnetic_hook", "originsx.creator.type.magnetic_hook", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "2"),
                new ParamSpec("range", "originsx.creator.param.range", Kind.DOUBLE, "16.0"),
                new ParamSpec("pull_speed", "originsx.creator.param.pull_speed", Kind.DOUBLE, "1.2"),
                new ParamSpec("pull_damage", "originsx.creator.param.pull_damage", Kind.DOUBLE, "2.0"))),
        LIFE_LINK("life_link", "originsx.creator.type.life_link", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "4"),
                new ParamSpec("duration", "originsx.creator.param.duration", Kind.INT, "5"),
                new ParamSpec("redirect_fraction", "originsx.creator.param.redirect_fraction", Kind.DOUBLE, "0.4"),
                new ParamSpec("pick_range", "originsx.creator.param.pick_range", Kind.INT, "10"))),
        GRAVITY_PULSE("gravity_pulse", "originsx.creator.type.gravity_pulse", true, List.of(
                new ParamSpec("radius", "originsx.creator.param.radius", Kind.DOUBLE, "8.0"),
                new ParamSpec("force", "originsx.creator.param.force", Kind.DOUBLE, "2.0"),
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "4"),
                new ParamSpec("max_charge_ticks", "originsx.creator.param.max_charge_ticks", Kind.INT, "2"),
                new ParamSpec("damage", "originsx.creator.param.damage", Kind.DOUBLE, "2.0"))),
        OVERDRIVE("overdrive", "originsx.creator.type.overdrive", true, List.of(
                new ParamSpec("self_damage", "originsx.creator.param.self_damage", Kind.DOUBLE, "3.0"),
                new ParamSpec("aura_radius", "originsx.creator.param.aura_radius", Kind.DOUBLE, "1.5"),
                new ParamSpec("aura_damage", "originsx.creator.param.aura_damage", Kind.DOUBLE, "2.0"),
                new ParamSpec("aura_fire_ticks", "originsx.creator.param.aura_fire_ticks", Kind.DOUBLE, "0.1"))),
        DISARM_WAVE("disarm_wave", "originsx.creator.type.disarm_wave", true, List.of(
                new ParamSpec("range", "originsx.creator.param.range", Kind.DOUBLE, "8.0"),
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "25"),
                new ParamSpec("hit_damage", "originsx.creator.param.hit_damage", Kind.DOUBLE, "5.0"),
                new ParamSpec("knockup_strength", "originsx.creator.param.knockup_strength", Kind.DOUBLE, "1.2"))),
        TELEPORT_STRIKE("teleport_strike", "originsx.creator.type.teleport_strike", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "8"),
                new ParamSpec("range", "originsx.creator.param.range", Kind.DOUBLE, "16.0"),
                new ParamSpec("damage", "originsx.creator.param.damage", Kind.DOUBLE, "6.0"))),
        EARTHQUAKE("earthquake", "originsx.creator.type.earthquake", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "10"),
                new ParamSpec("radius", "originsx.creator.param.radius", Kind.DOUBLE, "5.0"),
                new ParamSpec("damage", "originsx.creator.param.damage", Kind.DOUBLE, "8.0"),
                new ParamSpec("self_damage", "originsx.creator.param.self_damage", Kind.DOUBLE, "2.0"),
                new ParamSpec("knockup_strength", "originsx.creator.param.knockup_strength", Kind.DOUBLE, "0.8"),
                new ParamSpec("slowness_duration", "originsx.creator.param.slowness_duration", Kind.INT, "2"),
                new ParamSpec("slowness_amplifier", "originsx.creator.param.slowness_amplifier", Kind.INT, "0"))),
        SHADOW_STEP("shadow_step", "originsx.creator.type.shadow_step", true, List.of(
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "6"),
                new ParamSpec("range", "originsx.creator.param.range", Kind.DOUBLE, "12.0"),
                new ParamSpec("damage", "originsx.creator.param.damage", Kind.DOUBLE, "4.0"),
                new ParamSpec("darkness_duration", "originsx.creator.param.darkness_duration", Kind.INT, "0"),
                new ParamSpec("weakness_duration", "originsx.creator.param.weakness_duration", Kind.INT, "3"))),
        SIZE_CONTROL("size_control", "originsx.creator.type.size_control", true, List.of(
                new ParamSpec("scale", "originsx.creator.param.scale", Kind.DOUBLE, "1.5"),
                new ParamSpec("permanent", "originsx.creator.param.permanent", Kind.TOGGLE, "true",
                        new String[]{"duration", "cooldown"}),
                new ParamSpec("duration", "originsx.creator.param.duration", Kind.INT, "10"),
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "2"))),
        REACH("reach", "originsx.creator.type.reach", false, List.of(
                new ParamSpec("reach_multiplier", "originsx.creator.param.reach_multiplier", Kind.DOUBLE, "1.0"))),
        RESOURCE("resource", "originsx.creator.type.resource", false, List.of(
                new ParamSpec("max", "originsx.creator.param.max", Kind.DOUBLE, "100.0"),
                new ParamSpec("regen", "originsx.creator.param.regen", Kind.DOUBLE, "1.0"),
                new ParamSpec("resource_kind", "originsx.creator.param.resource_kind", Kind.OPTION,
                        "mana", "mana", "stamina"),
                new ParamSpec("consume_sprint", "originsx.creator.param.consume_sprint", Kind.TOGGLE, "true"),
                new ParamSpec("sprint_cost", "originsx.creator.param.sprint_cost", Kind.DOUBLE, "5.0"),
                new ParamSpec("consume_attack", "originsx.creator.param.consume_attack", Kind.TOGGLE, "true"),
                new ParamSpec("attack_cost", "originsx.creator.param.attack_cost", Kind.DOUBLE, "10.0"))),
        KILL_REWARD("on_kill", "originsx.creator.type.on_kill", false, List.of(
                new ParamSpec("heal", "originsx.creator.param.heal", Kind.DOUBLE, "4.0"),
                new ParamSpec("resource_gain", "originsx.creator.param.resource_gain", Kind.DOUBLE, "20.0"),
                new ParamSpec("strength_seconds", "originsx.creator.param.strength_seconds", Kind.INT, "5"))),
        EAT_REWARD("on_eat", "originsx.creator.type.on_eat", false, List.of(
                new ParamSpec("heal", "originsx.creator.param.heal", Kind.DOUBLE, "2.0"),
                new ParamSpec("resource_gain", "originsx.creator.param.resource_gain", Kind.DOUBLE, "10.0"))),
        DAMAGE_IMMUNITY("damage_immunity", "originsx.creator.type.damage_immunity", false, List.of(
                new ParamSpec("damage_type", "originsx.creator.param.damage_type", Kind.OPTION, "fire",
                        "fire", "fall", "drowning", "explosion", "projectile",
                        "lightning", "freeze", "starvation", "suffocation",
                        "void", "magic", "wither", "cactus", "generic",
                        "player_attack", "sonic_boom", "dragon_breath"))),
        GRANT_ITEM("grant_item", "originsx.creator.type.grant_item", false, List.of(
                new ParamSpec("item", "originsx.creator.param.item", Kind.REGISTRY, "minecraft:stone",
                        "item"),
                new ParamSpec("count", "originsx.creator.param.count", Kind.INT, "1"))),
        SUMMON("summon", "originsx.creator.type.summon", true, List.of(
                new ParamSpec("entity", "originsx.creator.param.entity", Kind.REGISTRY, "minecraft:wolf",
                        "entity"),
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "10"),
                new ParamSpec("count", "originsx.creator.param.count", Kind.INT, "1"))),
        ON_INTERACT("on_interact", "originsx.creator.type.on_interact", false, List.of(
                new ParamSpec("trigger_mode", "originsx.creator.param.trigger_mode", Kind.OPTION, "any",
                        "any", "block", "entity", "block_with_item", "entity_with_item",
                        "specific_entity", "specific_block"),
                new ParamSpec("trigger_item", "originsx.creator.param.trigger_item", Kind.REGISTRY, "minecraft:diamond",
                        "item"),
                new ParamSpec("trigger_entity", "originsx.creator.param.trigger_entity", Kind.REGISTRY, "minecraft:wolf",
                        "entity"),
                new ParamSpec("trigger_block", "originsx.creator.param.trigger_block", Kind.OPTION, "minecraft:obsidian",
                        "minecraft:obsidian", "minecraft:diamond_ore", "minecraft:gold_ore",
                        "minecraft:iron_ore", "minecraft:coal_ore", "minecraft:emerald_ore",
                        "minecraft:lapis_ore", "minecraft:redstone_ore",
                        "minecraft:oak_log", "minecraft:stone", "minecraft:grass_block",
                        "minecraft:end_portal_frame"),
                new ParamSpec("action", "originsx.creator.param.action", Kind.OPTION, "effect",
                        "effect", "heal", "damage", "damage_target", "heal_target",
                        "summon", "teleport"),
                new ParamSpec("effect", "originsx.creator.param.effect", Kind.OPTION, "minecraft:speed",
                        "minecraft:speed", "minecraft:jump_boost", "minecraft:haste", "minecraft:regeneration",
                        "minecraft:strength", "minecraft:resistance", "minecraft:fire_resistance",
                        "minecraft:night_vision", "minecraft:water_breathing", "minecraft:absorption",
                        "minecraft:conduit_power", "minecraft:glowing"),
                new ParamSpec("effect_duration", "originsx.creator.param.effect_duration", Kind.INT, "5"),
                new ParamSpec("effect_amplifier", "originsx.creator.param.amplifier", Kind.INT, "1"),
                new ParamSpec("heal_amount", "originsx.creator.param.heal_amount", Kind.DOUBLE, "2.0"),
                new ParamSpec("damage_amount", "originsx.creator.param.damage_amount", Kind.DOUBLE, "2.0"),
                new ParamSpec("summon_entity", "originsx.creator.param.summon_entity", Kind.OPTION, "minecraft:wolf",
                        "minecraft:wolf", "minecraft:cat", "minecraft:parrot",
                        "minecraft:iron_golem", "minecraft:snow_golem", "minecraft:fox",
                        "minecraft:bee", "minecraft:axolotl", "minecraft:zombie",
                        "minecraft:skeleton", "minecraft:creeper"),
                new ParamSpec("summon_count", "originsx.creator.param.summon_count", Kind.INT, "1"),
                new ParamSpec("cooldown", "originsx.creator.param.cooldown", Kind.INT, "0")));

        private final String type;
        private final String labelKey;
        private final boolean bound;
        private final List<ParamSpec> params;
        private final String descKey;

        /** Dropdown groups shown in the power type picker. */
        private enum Category {
            ACTIVE("originsx.gui.powers.active"),
            ATTRS("originsx.creator.cat.attributes"),
            SPECIAL("originsx.creator.cat.special"),
            COMBAT("originsx.creator.cat.combat"),
            MOVE("originsx.creator.cat.movement"),
            WORLD("originsx.creator.cat.world");

            final String key;

            Category(String key) {
                this.key = key;
            }
        }

        private Category category() {
            if (bound) {
                return Category.ACTIVE;
            }
            return switch (this) {
                case ATTRIBUTE, STATUS_EFFECT, EFFECT_REMOVAL -> Category.ATTRS;
                case CONDITIONAL, ON_INTERACT, GRANT_ITEM, DAMAGE_IMMUNITY, REACH,
                        RESOURCE, EAT_REWARD -> Category.SPECIAL;
                case SAFE_LANDING, TOUGHNESS, LIFESTEAL, THORNS, HEAVY_HITTER, FROST_TOUCH,
                        VENOM_TOUCH, FIRE_AURA, FROST_AURA, DETECTOR, AIRBORNE_FRAGILITY,
                        DIRECTIONAL_EXPOSURE, KILL_REWARD -> Category.COMBAT;
                case STEP_HEIGHT, WALL_JUMP, SPIDER_CLIMB, SPRINT_JUMP, BOUNCY, MAGNET,
                        HYPER_INERTIA, DENSITY_ANCHOR -> Category.MOVE;
                default -> Category.WORLD;
            };
        }

        PowerTypeSpec(String type, String labelKey, boolean bound, List<ParamSpec> params) {
            this.type = type;
            this.labelKey = labelKey;
            this.bound = bound;
            this.params = params;
            this.descKey = switch (type) {
                case "attribute" -> "originsx.creator.desc.attribute";
                case "status_effect" -> "originsx.creator.desc.status_effect";
                case "conditional" -> "originsx.creator.desc.conditional";
                case "effect_removal" -> "originsx.creator.desc.effect_removal";
                case "resource" -> "originsx.creator.desc.resource";
                case "on_kill" -> "originsx.creator.desc.on_kill";
                case "on_eat" -> "originsx.creator.desc.on_eat";
                case "teleport_strike" -> "originsx.creator.desc.teleport_strike";
                case "earthquake" -> "originsx.creator.desc.earthquake";
                case "shadow_step" -> "originsx.creator.desc.shadow_step";
                case "damage_immunity" -> "originsx.creator.desc.damage_immunity";
                case "grant_item" -> "originsx.creator.desc.grant_item";
                case "summon" -> "originsx.creator.desc.summon";
                case "on_interact" -> "originsx.creator.desc.on_interact";
                default -> "power.raceapi." + type + ".desc";
            };
        }

        private static PowerTypeSpec byType(String type) {
            PowerTypeSpec spec = byTypeOrNull(type);
            return spec != null ? spec : ATTRIBUTE;
        }

        private static @Nullable PowerTypeSpec byTypeOrNull(String type) {
            for (PowerTypeSpec spec : values()) {
                if (spec.type.equals(type)) {
                    return spec;
                }
            }
            return null;
        }
    }
}
