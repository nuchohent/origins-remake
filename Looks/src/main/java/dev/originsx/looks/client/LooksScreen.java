package dev.originsx.looks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
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
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

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

    /** Viewport modes: whose model the 3D preview shows. */
    private static final int MODE_PLAYER = 0;
    private static final int MODE_SLIM = 1;
    private static final int MODE_ENTITY = 2;
    private static final int VIEWPORT_BG = 0xFF14141B;
    private static final float VP_YAW_MIN = -180f;
    private static final float VP_YAW_MAX = 180f;
    private static final float VP_PITCH_LIMIT = 60f;

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
    @Nullable
    private RegistryPicker fEntityPicker;
    private Selector<String> fPart;
    private TextField fPx;
    private TextField fPy;
    private TextField fPz;
    private TextField fRx;
    private TextField fRy;
    private TextField fRz;
    private TextField fScale;
    private boolean loadingFields;

    // 3D viewport state (left panel)
    private int viewMode = MODE_PLAYER;
    private float vpYaw = 25f;
    private float vpPitch = -10f;
    private float vpZoom = 30f;
    private boolean vpDragging;
    private float vpLastX;
    private float vpLastY;
    @Nullable
    private String previewEntityId;
    @Nullable
    private LivingEntity previewEntity;
    private Button modePlayerBtn;
    private Button modeSlimBtn;
    private Button modeEntityBtn;
    private UIElement entityPickRow;

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
        // publish BEFORE the first edit: without this the preview override is
        // empty until the player touches a field, so a race that differs from
        // the locally selected one shows NO cosmetics at all on open
        publishPreview();
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

        // content row: 3D viewport on the left, list + editor on the right
        var content = new UIElement().layout(l -> l.flex(1).widthPercent(100)
                .flexDirection(FlexDirection.ROW).gapAll(4));

        content.addChild(buildViewportColumn());

        var right = new UIElement().layout(l -> l.flex(1).width(400)
                .flexDirection(FlexDirection.COLUMN).gapAll(4));
        // cosmetics list
        var listPanel = new UIElement().layout(l -> l.flex(1).widthPercent(100));
        listPanel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        listScroller = new ScrollerView();
        listScroller.layout(l -> l.flex(1).widthPercent(100));
        listScroller.viewContainer(view -> view.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)));
        listPanel.addChild(listScroller);
        right.addChild(listPanel);

        right.addChild(buildEditor());
        content.addChild(right);
        root.addChild(content);

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

    // ------------------------------------------------------------------
    //  3D viewport
    // ------------------------------------------------------------------

    /**
     * Left column: model selector (player/slim/entity) on top and the 3D
     * preview panel below. The panel renders the local player through the
     * vanilla inventory-entity pipeline, so the cosmetics layer shows up live.
     */
    private UIElement buildViewportColumn() {
        var col = new UIElement().layout(l -> l.flex(1)
                .flexDirection(FlexDirection.COLUMN).gapAll(3));

        var modes = row();
        modePlayerBtn = modeButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".viewport.player", MODE_PLAYER);
        modeSlimBtn = modeButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".viewport.slim", MODE_SLIM);
        modeEntityBtn = modeButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".viewport.entity", MODE_ENTITY);
        updateModeButtons();
        modes.addChild(modePlayerBtn);
        modes.addChild(modeSlimBtn);
        modes.addChild(modeEntityBtn);
        col.addChild(modes);

        entityPickRow = new UIElement().layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(1));
        fEntityPicker = new RegistryPicker(RegistryPicker.Kind.ENTITY);
        fEntityPicker.layout(l -> l.widthPercent(100).height(16));
        fEntityPicker.setOnValueChanged(v -> {
            previewEntityId = v == null || v.isEmpty() ? null : v;
            previewEntity = null;
        });
        entityPickRow.addChild(fEntityPicker);
        Label hint = fieldLabel("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".viewport.model_hint");
        hint.textStyle(s -> s.adaptiveWidth(true));
        entityPickRow.addChild(hint);
        entityPickRow.setDisplay(viewMode == MODE_ENTITY);
        col.addChild(entityPickRow);

        var viewport = new UIElement().layout(l -> l.flex(1).widthPercent(100));
        viewport.style(s -> s.background(new ViewportTexture()));
        viewport.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                vpDragging = true;
                vpLastX = e.x;
                vpLastY = e.y;
            }
        });
        viewport.addEventListener(UIEvents.MOUSE_MOVE, e -> {
            if (!vpDragging) {
                return;
            }
            float dx = e.x - vpLastX;
            float dy = e.y - vpLastY;
            vpLastX = e.x;
            vpLastY = e.y;
            vpYaw = wrapYaw(vpYaw + dx * 2f);
            vpPitch = Mth.clamp(vpPitch - dy * 2f, -VP_PITCH_LIMIT, VP_PITCH_LIMIT);
        });
        viewport.addEventListener(UIEvents.MOUSE_UP, e -> {
            if (e.button == 0) {
                vpDragging = false;
            }
        });
        viewport.addEventListener(UIEvents.MOUSE_LEAVE, e -> vpDragging = false);
        viewport.addEventListener(UIEvents.MOUSE_WHEEL, e -> {
            vpZoom = Mth.clamp(e.deltaY > 0 ? vpZoom * 1.1f : vpZoom / 1.1f, 15f, 80f);
            e.stopPropagation();
        });
        col.addChild(viewport);
        return col;
    }

    private Button modeButton(String key, int mode) {
        Button btn = new Button();
        btn.setText(key);
        btn.textStyle(s -> s.fontSize(8));
        btn.layout(l -> l.flex(1).height(14));
        btn.setOnClick(e -> setViewMode(mode));
        return btn;
    }

    private void setViewMode(int mode) {
        viewMode = mode;
        if (entityPickRow != null) {
            entityPickRow.setDisplay(mode == MODE_ENTITY);
        }
        updateModeButtons();
    }

    private void updateModeButtons() {
        modePlayerBtn.style(s -> s.background(new ColorRectTexture(
                viewMode == MODE_PLAYER ? ROW_SELECTED : ROW_BG)));
        modeSlimBtn.style(s -> s.background(new ColorRectTexture(
                viewMode == MODE_SLIM ? ROW_SELECTED : ROW_BG)));
        modeEntityBtn.style(s -> s.background(new ColorRectTexture(
                viewMode == MODE_ENTITY ? ROW_SELECTED : ROW_BG)));
    }

    private static float wrapYaw(float yaw) {
        float wrapped = yaw % 360f;
        if (wrapped > VP_YAW_MAX) {
            wrapped -= 360f;
        } else if (wrapped < VP_YAW_MIN) {
            wrapped += 360f;
        }
        return wrapped;
    }

    /** Who the viewport renders right now: player or the picked entity. */
    @Nullable
    private LivingEntity viewportTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (viewMode == MODE_ENTITY && previewEntityId != null && mc.level != null) {
            if (previewEntity == null || !previewEntityId.equals(entityIdOf(previewEntity))) {
                recreatePreviewEntity(mc);
            }
            return previewEntity;
        }
        return mc.player;
    }

    private static String entityIdOf(LivingEntity entity) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key == null ? "" : key.toString();
    }

    /**
     * Creates a detached (never added to the world) living entity of the
     * picked type purely for rendering; ignoreChecks bypasses the peaceful-
     * difficulty spawn gate so monsters can be previewed too.
     */
    private static final java.util.concurrent.atomic.AtomicInteger ENTITY_ID_COUNTER =
            new java.util.concurrent.atomic.AtomicInteger(2_000_000);

    private void recreatePreviewEntity(Minecraft mc) {
        LivingEntity created = null;
        try {
            Identifier id = Identifier.tryParse(previewEntityId);
            var holder = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
            EntityType<?> type = holder != null && holder.isPresent()
                    ? holder.get().value() : null;
            if (type != null && mc.level != null) {
                Entity spawned = type.create(mc.level,
                        new EntitySpawnRequest(EntitySpawnReason.COMMAND, true));
                if (spawned != null) {
                    spawned.setId(ENTITY_ID_COUNTER.getAndIncrement());
                }
                if (spawned instanceof LivingEntity living) {
                    created = living;
                }
            }
        } catch (Exception e) {
            dev.originsx.looks.LooksMod.LOGGER.warn("Failed to create preview entity {}",
                    previewEntityId, e);
        }
        previewEntity = created;
    }

    /**
     * Dark panel + the model drawn with explicit yaw/pitch/zoom through the
     * vanilla {@code InventoryScreen} entity-in-inventory math (extract state,
     * override angles, submit as pictures-in-picture clipped to this rect).
     * SLIM forces a slim copy of the player's skin onto the render state —
     * the render dispatcher picks the narrow-armed renderer from it, keeping
     * every layer (cosmetics included) alive.
     */
    private final class ViewportTexture implements GuiTexture {
        private int frameCount;

        @Override
        public void draw(GUIContext context, float x, float y, float width, float height) {
            var graphics = context.graphics;
            int x0 = Mth.floor(x);
            int y0 = Mth.floor(y);
            int x1 = Mth.ceil(x + width);
            int y1 = Mth.ceil(y + height);
            graphics.fill(x0, y0, x1, y1, VIEWPORT_BG);

            Minecraft mc = Minecraft.getInstance();
            LivingEntity target = viewportTarget();
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            if (target == null || dispatcher.getRenderer(target) == null) {
                return;
            }
            try {
                float xAngle = vpYaw / 20f;
                float yAngle = vpPitch / 20f;
                Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
                Quaternionf xRotation = new Quaternionf()
                        .rotateX(yAngle * 20.0F * (float) (Math.PI / 180.0));
                rotation.mul(xRotation);

                EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(target);
                EntityRenderState renderState = renderer.createRenderState(target, 1.0F);
                if (renderState instanceof AvatarRenderState avatarState
                        && viewMode == MODE_SLIM && mc.player != null) {
                    PlayerSkin skin = mc.player.getSkin();
                    avatarState.skin = new PlayerSkin(skin.body(), skin.cape(),
                            skin.elytra(), PlayerModelType.SLIM, skin.secure());
                }
                if (renderState instanceof LivingEntityRenderState livingState) {
                    livingState.bodyRot = 180.0F + xAngle * 20.0F;
                    livingState.yRot = 0.0F;
                    livingState.xRot = 0.0F;
                    livingState.boundingBoxWidth =
                            livingState.boundingBoxWidth / livingState.scale;
                    livingState.boundingBoxHeight =
                            livingState.boundingBoxHeight / livingState.scale;
                    livingState.scale = 1.0F;
                }
                renderState.shadowRadius = 0.0F;
                renderState.shadowPieces.clear();

                frameCount++;
                if (renderState instanceof AvatarRenderState avatarState
                        && target instanceof net.minecraft.world.entity.Avatar avatar) {
                    var extracted = CosmeticsStateModifier.extract(avatar);
                    avatarState.setRenderData(LooksClient.RENDER_DATA, extracted);
                    if (frameCount % 300 == 1) {
                        dev.originsx.looks.LooksMod.LOGGER.info(
                                "[Looks] viewport frame #{}: renderer={}, extracted={}, entries={}, targetClass={}",
                                frameCount,
                                renderer.getClass().getSimpleName(),
                                extracted.size(),
                                entries.size(),
                                target.getClass().getSimpleName());
                    }
                } else if (frameCount % 120 == 1) {
                    dev.originsx.looks.LooksMod.LOGGER.info(
                            "[Looks] viewport frame #{}: isAvatar={}, isAvatarRTS={}, targetClass={}",
                            frameCount,
                            target instanceof net.minecraft.world.entity.Avatar,
                            renderState instanceof AvatarRenderState,
                            target.getClass().getSimpleName());
                }

                Vector3f translation = new Vector3f(0.0F,
                        renderState.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
                graphics.enableScissor(x0, y0, x1, y1);
                graphics.entity(renderState, vpZoom, translation, rotation,
                        xRotation, x0, y0, x1, y1);
                graphics.disableScissor();
            } catch (Exception e) {
                dev.originsx.looks.LooksMod.LOGGER.warn("Viewport preview failed", e);
            }
        }
    }

    private UIElement header() {
        var headerRow = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .gapAll(6).alignItems(AlignItems.CENTER));
        Label title = new Label();
        title.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".title"));
        title.textStyle(style -> style.fontSize(14).adaptiveWidth(true));
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
        rPos.addChild(labeledAxisField(fPx, "X"));
        rPos.addChild(labeledAxisField(fPy, "Y"));
        rPos.addChild(labeledAxisField(fPz, "Z"));
        detailGroup.addChild(rPos);

        var rRot = row();
        rRot.addChild(fieldLabelFixed("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.rot"));
        rRot.addChild(labeledAxisField(fRx, "X"));
        rRot.addChild(labeledAxisField(fRy, "Y"));
        rRot.addChild(labeledAxisField(fRz, "Z"));
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
        tf.layout(l -> l.height(18));
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

    /** Row-leading caption sized to its full text (no fixed width). */
    private static Label fieldLabelFixed(String key) {
        var lb = fieldLabel(key);
        lb.textStyle(s -> s.adaptiveWidth(true));
        return lb;
    }

    private static UIElement labeledAxisField(TextField tf, String axis) {
        var col = new UIElement().layout(l -> l.flex(1).minWidth(1)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)
                .alignItems(AlignItems.STRETCH));
        var lb = new Label();
        lb.setText(Component.literal(axis));
        lb.textStyle(s -> s.fontSize(7).textColor(0xFF9A9AA0));
        col.addChild(lb);
        col.addChild(tf);
        return col;
    }

    private static UIElement labeledFieldFlex(TextField tf) {        var col = new UIElement().layout(l -> l.flex(1).minWidth(1)
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
