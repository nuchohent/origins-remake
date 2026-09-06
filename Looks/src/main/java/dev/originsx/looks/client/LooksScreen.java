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
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Slider;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.raceapi.race.Race;
import dev.raceapi.race.RaceRegistry;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final int ACCENT = 0xFF2F6FA8;
    private static final int ROW_SELECTED = 0xFF2F6FA8;
    private static final int HEADER_BG = 0xFF3A3A44;
    private static final int SECTION_BG = 0xFF2B2B34;
    private static final int LINE_LOCKED = 0xFF666666;

    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();

    /** Viewport modes: whose model the 3D preview shows. */
    private static final int MODE_PLAYER = 0;
    private static final int MODE_SLIM = 1;
    private static final int VIEWPORT_BG = 0xFF14141B;
    private static final float VP_YAW_MIN = -180f;
    private static final float VP_YAW_MAX = 180f;
    private static final float VP_PITCH_LIMIT = 60f;

    /** Passes the root element from the super() call into the constructor body. */
    private static final ThreadLocal<UIElement> ROOT_HOLDER = new ThreadLocal<>();

    private final String raceId;
    /** Top-level UI container, used to mount the save-target race picker. */
    private UIElement rootEl;
    /** Race-picker overlay opened by the Save button, or null while closed. */
    private UIElement saveRaceOverlay;
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
    private TextField fLayer;
    private ColorSelector fColor;
    private UIElement colorPickerHost;
    private Button colorBtn;
    private Switch fGlow;
    private boolean loadingFields;

    // viewport camera controls (right panel, always visible)
    private Slider.Horizontal camYawS;
    private Slider.Horizontal camPitchS;
    private Slider.Horizontal camZoomS;

    // 3D viewport state (left panel)
    private int viewMode = MODE_PLAYER;
    private float vpYaw = 25f;
    private float vpPitch = -10f;
    private float vpZoom = 30f;
    private boolean vpDragging;
    private float vpLastX;
    private float vpLastY;
    private Button modePlayerBtn;
    private Button modeSlimBtn;
    private Button autoRotateBtn;
    private boolean autoRotate = false;
    private Label viewStatus;
    private LayoutEditor layoutEditor;

    // Search & filter
    private TextField fSearch;
    private String searchFilter = "";

    // Visibility toggles per part
    private Set<Cosmetics.Part> visibleParts = new java.util.HashSet<>(java.util.Arrays.asList(Cosmetics.Part.values()));

    // Clipboard for copy/paste between races
    private static List<Cosmetics.Entry> clipboard;

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
        LooksClient.looksEditorOpen = true;
        loadExisting();
        UIElement root = ROOT_HOLDER.get();
        ROOT_HOLDER.remove();
        rootEl = root;
        populateRoot(root);
        layoutEditor = LayoutEditor.attach(root);
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
    public void onClose() {
        LooksClient.looksEditorOpen = false;
        super.onClose();
    }

    @Override
    public void removed() {
        LooksClient.looksEditorOpen = false;
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (toastVisible && System.currentTimeMillis() > toastHideAtMs) {
            toastVisible = false;
            toastHost.setDisplay(false);
        }
        if (autoRotate && !vpDragging) {
            vpYaw = wrapYaw(vpYaw + 0.6f);
        }
        updateAutoRotateButton();
        if (viewStatus != null) {
            viewStatus.setText(Component.literal(String.format("Yaw %.0f  Pitch %.0f  Zoom %.0f",
                    vpYaw, vpPitch, vpZoom)));
        }
        if (layoutEditor != null) {
            layoutEditor.tick();
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (keyEvent.input() == org.lwjgl.glfw.GLFW.GLFW_KEY_L
                && (keyEvent.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            if (layoutEditor != null) {
                layoutEditor.toggle();
                return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    private void updateAutoRotateButton() {
        if (autoRotateBtn != null) {
            autoRotateBtn.style(s -> s.background(new ColorRectTexture(
                    autoRotate ? ROW_SELECTED : ROW_BG)));
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
        // per-edit trace: confirms the override snapshot actually updates after
        // each field change (the position-change disappearance is either a data
        // flow bug here or a render-placement bug downstream)
        Cosmetics.Entry e = selectedEntry();
        String pos = e == null ? "-" : String.format("%.2f %.2f %.2f",
                e.pos()[0], e.pos()[1], e.pos()[2]);
        String rot = e == null ? "-" : String.format("%.1f %.1f %.1f",
                e.rot()[0], e.rot()[1], e.rot()[2]);
        dev.originsx.looks.LooksMod.LOGGER.info(
                "[Looks] publishPreview entries={} selected={} part={} item={} pos=[{}] rot=[{}] scale={}",
                entries.size(), selected,
                e == null ? "-" : e.part().jsonName,
                e == null ? "-" : BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString(),
                pos, rot, e == null ? "-" : String.format("%.2f", e.scale()));
    }

    // ------------------------------------------------------------------
    //  UI construction
    // ------------------------------------------------------------------

    private void populateRoot(UIElement root) {
        root.addChild(header());
        root.addChild(separator());

        // Blender-style workspace: outliner (list) on the left, the 3D
        // viewport centered and the properties editor on the right
        var content = new UIElement().layout(l -> l.flex(1).widthPercent(100)
                .flexDirection(FlexDirection.ROW).gapAll(4));

        content.addChild(buildListPanel());
        content.addChild(buildViewportColumn());
        content.addChild(buildEditorPanel());
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
    private UIElement buildListPanel() {
        var panel = new UIElement().layout(l -> l.width(170)
                .flexDirection(FlexDirection.COLUMN).gapAll(2));
        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));

        // Blender outliner header: title + item count
        var head = new UIElement().layout(l -> l.widthPercent(100).height(16)
                .flexDirection(FlexDirection.ROW).gapAll(4).paddingAll(2)
                .alignItems(AlignItems.CENTER));
        head.style(s -> s.background(new ColorRectTexture(HEADER_BG)));
        var headTitle = new Label();
        headTitle.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".list"));
        headTitle.textStyle(style -> style.fontSize(8).textColor(0xFFDDDDDD).adaptiveWidth(true));
        head.addChild(headTitle);
        countLabel = new Label();
        countLabel.textStyle(style -> style.fontSize(8).textColor(0xFF9A9AA0).adaptiveWidth(true));
        countLabel.layout(l -> l.flex(1));
        head.addChild(countLabel);
        panel.addChild(head);

        fSearch = new TextField();
        fSearch.layout(l -> l.widthPercent(100).height(16));
        fSearch.setTextResponder(v -> {
            searchFilter = v != null ? v.toLowerCase() : "";
            rebuildList();
        });
        fSearch.textFieldStyle(s -> s.placeholder(Component.translatable("gui.search")));
        panel.addChild(fSearch);

        listScroller = new ScrollerView();
        listScroller.layout(l -> l.flex(1).widthPercent(100));
        listScroller.viewContainer(view -> view.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(1)));
        panel.addChild(listScroller);
        return panel;
    }

    /**
     * Center editor: the 3D viewport with a Blender-style header strip
     * (mode tabs + auto-rotate toggle) above it and a tiny status bar below.
     */
    private UIElement buildViewportColumn() {
        var col = new UIElement().layout(l -> l.flex(1).minWidth(120)
                .flexDirection(FlexDirection.COLUMN).gapAll(3));

        var vpHeader = new UIElement().layout(l -> l.widthPercent(100).height(18)
                .flexDirection(FlexDirection.ROW).gapAll(4).paddingAll(2)
                .alignItems(AlignItems.CENTER));
        vpHeader.style(s -> s.background(new ColorRectTexture(HEADER_BG)));

        modePlayerBtn = modeButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".viewport.player", MODE_PLAYER);
        modeSlimBtn = modeButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".viewport.slim", MODE_SLIM);
        updateModeButtons();
        vpHeader.addChild(modePlayerBtn);
        vpHeader.addChild(modeSlimBtn);

        autoRotateBtn = new Button();
        autoRotateBtn.setText("gui.auto_rotate");
        autoRotateBtn.textStyle(s -> s.fontSize(6));
        autoRotateBtn.layout(l -> l.width(62).height(13));
        autoRotateBtn.setOnClick(e -> {
            autoRotate = !autoRotate;
            updateAutoRotateButton();
        });
        vpHeader.addChild(autoRotateBtn);
        updateAutoRotateButton();

        col.addChild(vpHeader);

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

        // Blender viewport status bar: live camera readout
        var status = new UIElement().layout(l -> l.widthPercent(100).height(14)
                .flexDirection(FlexDirection.ROW).gapAll(6).paddingAll(1)
                .alignItems(AlignItems.CENTER));
        status.style(s -> s.background(new ColorRectTexture(HEADER_BG)));
        viewStatus = new Label();
        viewStatus.textStyle(style -> style.fontSize(7).textColor(0xFF9A9AA0).adaptiveWidth(true));
        status.addChild(viewStatus);
        col.addChild(status);

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
        updateModeButtons();
    }

    private void updateModeButtons() {
        modePlayerBtn.style(s -> s.background(new ColorRectTexture(
                viewMode == MODE_PLAYER ? ROW_SELECTED : ROW_BG)));
        modeSlimBtn.style(s -> s.background(new ColorRectTexture(
                viewMode == MODE_SLIM ? ROW_SELECTED : ROW_BG)));
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

    /** Who the viewport renders right now: the local player. */
    private LivingEntity viewportTarget() {
        return Minecraft.getInstance().player;
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

                if (renderState instanceof AvatarRenderState avatarState
                        && target instanceof net.minecraft.world.entity.Avatar avatar) {
                    var extracted = CosmeticsStateModifier.extract(avatar);
                    avatarState.setRenderData(LooksClient.RENDER_DATA, extracted);
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
                        .gapAll(6).paddingAll(2).alignItems(AlignItems.CENTER));
        headerRow.style(s -> s.background(new ColorRectTexture(HEADER_BG)));

        // Blender-style logo tile
        UIElement logo = new UIElement().layout(l -> l.width(10).height(10));
        logo.style(s -> s.background(new ColorRectTexture(ACCENT)));
        headerRow.addChild(logo);

        Label title = new Label();
        title.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".title"));
        title.textStyle(style -> style.fontSize(13).adaptiveWidth(true));
        headerRow.addChild(title);

        raceLabel = new Label();
        raceLabel.textStyle(style -> style.fontSize(11)
                .textColor(0xFF55CCFF).adaptiveWidth(true));
        raceLabel.setText(LooksClient.raceName(raceId));
        headerRow.addChild(raceLabel);

        var spacer = new UIElement().layout(l -> l.flex(1));
        headerRow.addChild(spacer);

        Button closeBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".close",
                this::closeWithoutSaving);
        closeBtn.layout(l -> l.width(52).height(15));
        headerRow.addChild(closeBtn);
        return headerRow;
    }

    private UIElement buildEditorPanel() {
        var panel = new UIElement().layout(l -> l.width(340)
                .flexDirection(FlexDirection.COLUMN).gapAll(3).paddingAll(4)
                .alignItems(AlignItems.STRETCH));
        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        panel.addChild(blenderHeader("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".editor"));

        // The right column is taller than any reasonable window, so every
        // section lives inside a scroller (previously the presets/quick/camera
        // panels were simply cut off below the window edge).
        var scroller = new ScrollerView();
        scroller.layout(l -> l.flex(1).widthPercent(100));
        var body = new UIElement().layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(3));
        scroller.addScrollViewChild(body);
        panel.addChild(scroller);

        emptyDetailHint = adaptiveLabel(9);
        emptyDetailHint.setText(Component.translatable(
                "gui." + dev.originsx.looks.LooksMod.MOD_ID + ".pick_hint"));
        body.addChild(emptyDetailHint);

        detailGroup = new UIElement().layout(l ->
                l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));

        // Visibility toggles for each part
        var visRow = row();
        visRow.addChild(fieldLabelFixed("gui.visible_parts"));
        for (Cosmetics.Part part : Cosmetics.Part.values()) {
            Button toggle = new Button();
            toggle.setText(part.translationKey()).textStyle(s -> s.fontSize(6));
            toggle.layout(l -> l.flex(1).height(13));
            boolean visible = visibleParts.contains(part);
            toggle.style(s -> s.background(new ColorRectTexture(
                    visible ? ROW_SELECTED : ROW_BG)));
            toggle.setOnClick(e -> {
                if (visibleParts.contains(part)) {
                    visibleParts.remove(part);
                    toggle.style(s -> s.background(new ColorRectTexture(ROW_BG)));
                } else {
                    visibleParts.add(part);
                    toggle.style(s -> s.background(new ColorRectTexture(ROW_SELECTED)));
                }
                rebuildList();
            });
            visRow.addChild(toggle);
        }
        detailGroup.addChild(visRow);

        // ITEM section: picker + body part
        detailGroup.addChild(blenderSection("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".section.item"));
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

        // TRANSFORM section: position + rotation + scale
        detailGroup.addChild(blenderSection("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".section.transform"));
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

        // PROPERTIES section: layer (z-order), tint color, glow
        detailGroup.addChild(blenderSection("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".section.properties"));
        var rLayer = row();
        rLayer.addChild(fieldLabelFixed("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.layer"));
        fLayer = numberField("layer", 0);
        fLayer.layout(l -> l.width(70).height(18));
        rLayer.addChild(fLayer);
        detailGroup.addChild(rLayer);

        var rColor = row();
        rColor.addChild(fieldLabelFixed("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.color"));
        colorBtn = new Button();
        colorBtn.layout(l -> l.width(70).height(14));
        colorBtn.textStyle(s -> s.fontSize(6));
        // replace the default "Button" caption with the live hex value
        colorBtn.setText(String.format("#%08X", Cosmetics.Entry.DEFAULT_TINT), false);
        colorBtn.setOnClick(e -> {
            if (colorPickerHost != null) {
                colorPickerHost.setDisplay(!colorPickerHost.isDisplayed());
            }
        });
        rColor.addChild(colorBtn);
        detailGroup.addChild(rColor);

        colorPickerHost = new UIElement().layout(l -> l.widthPercent(100));
        colorPickerHost.setDisplay(false);
        fColor = new ColorSelector();
        fColor.layout(l -> l.width(150));
        fColor.setOnColorChangeListener(v -> {
            // apply live while the user drags the picker instead of closing it
            // on the first click, so the preview can be aimed before releasing
            setSelectedTint(v);
        });
        colorPickerHost.addChild(fColor);
        detailGroup.addChild(colorPickerHost);

        var rGlow = row();
        rGlow.addChild(fieldLabelFixed("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".field.glow"));
        fGlow = new Switch();
        fGlow.layout(l -> l.width(30).height(12));
        fGlow.setOnSwitchChanged(v -> setSelectedGlow(v));
        rGlow.addChild(fGlow);
        detailGroup.addChild(rGlow);

        Label hint = new Label();
        hint.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".edit_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        hint.textStyle(style -> style.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));

        // actions live OUTSIDE detailGroup: with an empty list nothing is
        // selected, and an add button hidden behind "has selection" is a
        // deadlock — the row must stay visible at all times
        body.addChild(detailGroup);
        body.addChild(blenderSection("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".section.actions"));
        var actions = row();
        Button addBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".add",
                this::addEntry);
        addBtn.layout(l -> l.flex(1).height(15));
        actions.addChild(addBtn);
        Button deleteBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".delete",
                this::deleteSelected);
        deleteBtn.layout(l -> l.flex(1).height(15));
        actions.addChild(deleteBtn);
        Button copyBtn = smallButton("gui.copy", this::copySelected);
        copyBtn.layout(l -> l.flex(1).height(15));
        actions.addChild(copyBtn);
        Button pasteBtn = smallButton("gui.paste", this::pasteEntry);
        pasteBtn.layout(l -> l.flex(1).height(15));
        actions.addChild(pasteBtn);
        body.addChild(actions);
        body.addChild(buildCameraPanel());
        body.addChild(buildQuickPanel());
        body.addChild(buildPresetsPanel());
        body.addChild(hint);

        // Pinned bottom bar, always on screen (the sections above live in a
        // scroller and are taller than any window): Save + Clear selection,
        // the Blender-style "get rid of the item's parameters" escape hatch.
        var footer = row();
        Button saveBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save",
                this::openSaveRaceDialog);
        saveBtn.layout(l -> l.flex(1).height(15));
        footer.addChild(saveBtn);
        Button clearBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".clear",
                this::deselect);
        clearBtn.layout(l -> l.flex(1).height(15));
        footer.addChild(clearBtn);
        panel.addChild(footer);
        return panel;
    }

    // ------------------------------------------------------------------
    //  Right column: permanent panels (viewport camera, quick actions, presets)
    // ------------------------------------------------------------------

    private UIElement buildCameraPanel() {
        var panelEl = new UIElement();
        panelEl.addChild(blenderSection("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".section.view"));

        camYawS = camYawS();
        panelEl.addChild(labeledSlider(
                "gui." + dev.originsx.looks.LooksMod.MOD_ID + ".cam.yaw", camYawS));

        camPitchS = camPitchS();
        panelEl.addChild(labeledSlider(
                "gui." + dev.originsx.looks.LooksMod.MOD_ID + ".cam.pitch", camPitchS));

        camZoomS = camZoomS();
        panelEl.addChild(labeledSlider(
                "gui." + dev.originsx.looks.LooksMod.MOD_ID + ".cam.zoom", camZoomS));

        var rReset = row();
        Button resetView = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".reset_view",
                this::resetView);
        resetView.layout(l -> l.flex(1).height(15));
        rReset.addChild(resetView);
        panelEl.addChild(rReset);
        return panelEl;
    }

    private Slider.Horizontal camYawS() {
        var s = new Slider.Horizontal();
        s.setRange(VP_YAW_MIN, VP_YAW_MAX).setValue(vpYaw);
        s.setOnValueChanged(v -> vpYaw = wrapYaw(v));
        return s;
    }

    private Slider.Horizontal camPitchS() {
        var s = new Slider.Horizontal();
        s.setRange(-VP_PITCH_LIMIT, VP_PITCH_LIMIT).setValue(vpPitch);
        s.setOnValueChanged(v -> vpPitch = v);
        return s;
    }

    private Slider.Horizontal camZoomS() {
        var s = new Slider.Horizontal();
        s.setRange(8, 80).setValue(vpZoom);
        s.setOnValueChanged(v -> vpZoom = v);
        return s;
    }

    private void resetView() {
        vpYaw = 25f;
        vpPitch = -10f;
        vpZoom = 30f;
        if (camYawS != null) camYawS.setValue(vpYaw);
        if (camPitchS != null) camPitchS.setValue(vpPitch);
        if (camZoomS != null) camZoomS.setValue(vpZoom);
    }

    private static UIElement labeledSlider(String key, Slider.Horizontal slider) {
        var rowEl = row();
        Label lb = fieldLabelFixed(key);
        lb.layout(l -> l.width(76));
        slider.layout(l -> l.flex(1).height(12));
        slider.sliderStyle(s -> s.trackSize(3).handleSize(6).sliderStep(0.04f));
        rowEl.addChild(lb);
        rowEl.addChild(slider);
        return rowEl;
    }

    private UIElement buildQuickPanel() {
        var panelEl = new UIElement();
        panelEl.addChild(blenderSection("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".section.quick"));
        var top = row();
        Button mirrorBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".mirror",
                this::mirrorSelected);
        mirrorBtn.layout(l -> l.flex(1).height(15));
        top.addChild(mirrorBtn);
        Button centerBtn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".center",
                this::centerSelected);
        centerBtn.layout(l -> l.flex(1).height(15));
        top.addChild(centerBtn);
        Button resetTr = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".reset_transform",
                this::resetSelectedTransform);
        resetTr.layout(l -> l.flex(1).height(15));
        top.addChild(resetTr);
        panelEl.addChild(top);

        var bottom = row();
        Button dup = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".duplicate",
                this::duplicateSelected);
        dup.layout(l -> l.flex(1).height(15));
        bottom.addChild(dup);
        panelEl.addChild(bottom);
        return panelEl;
    }

    private UIElement buildPresetsPanel() {
        var panelEl = new UIElement();
        panelEl.addChild(blenderSection("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".section.presets"));
        var row1 = row();
        var row2 = row();
        int i = 0;
        for (Preset preset : PRESETS) {
            Button btn = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".preset." + preset.key,
                    () -> addPreset(preset));
            btn.layout(l -> l.flex(1).height(15));
            (i++ < 3 ? row1 : row2).addChild(btn);
        }
        panelEl.addChild(row1);
        panelEl.addChild(row2);
        return panelEl;
    }

    /** Blender panel-header strip (editor title). */
    private static UIElement blenderHeader(String titleKey) {
        var head = new UIElement().layout(l -> l.widthPercent(100).height(16)
                .flexDirection(FlexDirection.ROW).gapAll(4).paddingAll(2)
                .alignItems(AlignItems.CENTER));
        head.style(s -> s.background(new ColorRectTexture(HEADER_BG)));
        var title = new Label();
        title.setText(Component.translatable(titleKey));
        title.textStyle(style -> style.fontSize(8).textColor(0xFFDDDDDD).adaptiveWidth(true));
        head.addChild(title);
        return head;
    }

    /** Blender collapsible-panel section divider. */
    private static UIElement blenderSection(String titleKey) {
        var head = new UIElement().layout(l -> l.widthPercent(100).height(12)
                .flexDirection(FlexDirection.ROW).gapAll(4).paddingAll(1)
                .alignItems(AlignItems.CENTER));
        head.style(s -> s.background(new ColorRectTexture(SECTION_BG)));
        var title = new Label();
        title.setText(Component.translatable(titleKey));
        title.textStyle(style -> style.fontSize(7).textColor(0xFF9A9AA0).adaptiveWidth(true));
        head.addChild(title);
        return head;
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
        // Blender axis colors: X red, Y green, Z blue
        int axisColor = switch (axis) {
            case "X" -> 0xFFFF6B6B;
            case "Y" -> 0xFF8BD37A;
            case "Z" -> 0xFF6FA8DC;
            default -> 0xFF9A9AA0;
        };
        lb.textStyle(s -> s.fontSize(7).textColor(axisColor));
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
            Cosmetics.Entry entry = entries.get(i);
            boolean visible = visibleParts.contains(entry.part());
            boolean matchesSearch = searchFilter.isEmpty()
                    || entry.stack().getHoverName().getString().toLowerCase().contains(searchFilter)
                    || entry.part().jsonName.contains(searchFilter)
                    || BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString().toLowerCase().contains(searchFilter);
            if (!visible || !matchesSearch) continue;
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

    /** Clears the current selection so no item parameters are shown at all. */
    private void deselect() {
        selected = null;
        publishPreview();
        rebuildList();
        fillFields();
    }

    private void copySelected() {
        Cosmetics.Entry entry = selectedEntry();
        if (entry == null) return;
        clipboard = List.of(entry);
        showToast("gui.copied");
    }

    /** Paste a cosmetic into the current race via clipboard. */
    private void pasteEntry() {
        if (clipboard == null || clipboard.isEmpty()) {
            showToast("gui.clipboard_empty");
            return;
        }
        Cosmetics.Entry copied = clipboard.get(0);
        // deep copy
        entries.add(new Cosmetics.Entry(
                copied.part(),
                copied.stack().copy(),
                copied.pos().clone(),
                copied.rot().clone(),
                copied.scale(),
                copied.anim(),
                copied.zIndex(),
                copied.tint(),
                copied.glow()));
        selected = entries.size() - 1;
        publishPreview();
        rebuildList();
        fillFields();
        showToast("gui.pasted");
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
                Float.isNaN(scale) ? old.scale() : scale,
                old.anim(),
                old.zIndex(),
                old.tint(),
                old.glow()));
        publishPreview();
    }

    private void setSelectedLayer(int z) {
        Cosmetics.Entry old = selectedEntry();
        if (old == null) {
            return;
        }
        entries.set(selected, old.withLayer(z));
        afterChange();
    }

    private void setSelectedTint(int argb) {
        Cosmetics.Entry old = selectedEntry();
        if (old == null) {
            return;
        }
        entries.set(selected, old.withTint(argb));
        afterChange();
    }

    private void setSelectedGlow(boolean glow) {
        Cosmetics.Entry old = selectedEntry();
        if (old == null) {
            return;
        }
        entries.set(selected, old.withGlow(glow));
        afterChange();
    }

    private void mirrorSelected() {
        Cosmetics.Entry old = selectedEntry();
        if (old == null) {
            return;
        }
        float[] p = old.pos().clone();
        float[] r = old.rot().clone();
        p[0] = -p[0];
        r[1] = -r[1];
        r[2] = -r[2];
        entries.set(selected, new Cosmetics.Entry(old.part(), old.stack(), p, r,
                old.scale(), old.anim(), old.zIndex(), old.tint(), old.glow()));
        afterChange();
    }

    private void centerSelected() {
        Cosmetics.Entry old = selectedEntry();
        if (old == null) {
            return;
        }
        entries.set(selected, new Cosmetics.Entry(old.part(), old.stack(),
                new float[]{0f, 0f, 0f}, old.rot().clone(), old.scale(),
                old.anim(), old.zIndex(), old.tint(), old.glow()));
        afterChange();
    }

    private void resetSelectedTransform() {
        Cosmetics.Entry old = selectedEntry();
        if (old == null) {
            return;
        }
        entries.set(selected, new Cosmetics.Entry(old.part(), old.stack(),
                new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 0f}, 1.0f,
                old.anim(), old.zIndex(), old.tint(), old.glow()));
        afterChange();
    }

    private void duplicateSelected() {
        copySelected();
        pasteEntry();
    }

    private void afterChange() {
        publishPreview();
        rebuildList();
        fillFields();
    }

    // ------------------------------------------------------------------
    //  Part presets (quick-add templates)
    // ------------------------------------------------------------------

    private record Preset(String key, String itemId, String part,
                          float[] pos, float[] rot, float scale, int tint, boolean glow) {
    }

    private static final List<Preset> PRESETS = List.of(
            new Preset("crown", "minecraft:gold_ingot", "head",
                    new float[]{0f, 0.55f, 0f}, new float[]{0f, 0f, 0f}, 1.0f,
                    Cosmetics.Entry.DEFAULT_TINT, true),
            new Preset("halo", "minecraft:glowstone", "head",
                    new float[]{0f, 0.85f, 0f}, new float[]{0f, 0f, 0f}, 1.0f,
                    Cosmetics.Entry.DEFAULT_TINT, true),
            new Preset("mask", "minecraft:paper", "head",
                    new float[]{0f, 0.25f, 0.5f}, new float[]{0f, 0f, 0f}, 1.1f,
                    Cosmetics.Entry.DEFAULT_TINT, false),
            new Preset("cape", "minecraft:red_banner", "cape",
                    new float[]{0f, 0f, 0f}, new float[]{90f, 0f, 0f}, 1.6f,
                    Cosmetics.Entry.DEFAULT_TINT, false),
            new Preset("emblem", "minecraft:diamond", "body",
                    new float[]{0f, 0.7f, 0.4f}, new float[]{0f, 0f, 0f}, 0.8f,
                    Cosmetics.Entry.DEFAULT_TINT, true),
            new Preset("pauldron", "minecraft:shield", "left_arm",
                    new float[]{-0.55f, 0.8f, 0f}, new float[]{0f, 0f, 90f}, 1.2f,
                    Cosmetics.Entry.DEFAULT_TINT, false));

    private void addPreset(Preset preset) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("minecraft", preset.itemId)));
        if (stack.isEmpty()) {
            return;
        }
        Cosmetics.Part part = Cosmetics.Part.byName(preset.part);
        if (part == null) {
            part = Cosmetics.Part.HEAD;
        }
        entries.add(new Cosmetics.Entry(part, stack,
                preset.pos.clone(), preset.rot.clone(), preset.scale,
                Cosmetics.AnimConfig.DEFAULT, 0, preset.tint, preset.glow));
        selected = entries.size() - 1;
        afterChange();
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
                    float parsed = Float.parseFloat(value.trim());
                    if (parsed > 0.01f && parsed < 100f) {
                        updateSelected(null, null, null, null, parsed);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            case "layer" -> {
                try {
                    int parsed = Integer.parseInt(value.trim());
                    if (parsed >= -1024 && parsed <= 1024) {
                        setSelectedLayer(parsed);
                    }
                } catch (NumberFormatException ignored) {
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
            fLayer.setText(fmt(entry.zIndex()));
            if (fColor != null) fColor.setColor(entry.tint(), false);
            if (colorBtn != null) {
                colorBtn.setText(String.format("#%08X", entry.tint()), false);
                colorBtn.style(s -> s.background(new ColorRectTexture(entry.tint())));
            }
            if (fGlow != null) fGlow.setOn(entry.glow(), false);
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
    //  Save target picker: ask which race receives the appearance
    // ------------------------------------------------------------------

    private void openSaveRaceDialog() {
        if (rootEl == null || saveRaceOverlay != null) {
            return;
        }

        var overlay = new UIElement();
        overlay.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).heightPercent(100));
        saveRaceOverlay = overlay;

        float w = rootEl.getSizeWidth();
        float h = rootEl.getSizeHeight();
        float panelW = 340;
        float panelH = 270;

        var panel = new UIElement();
        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .width(panelW).height(panelH)
                .left(Math.max(0, (w - panelW) / 2))
                .top(Math.max(0, (h - panelH) / 2))
                .flexDirection(FlexDirection.COLUMN).gapAll(4).paddingAll(6));

        Label title = new Label();
        title.setText(Component.translatable("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.ask_race"));
        title.textStyle(style -> style.fontSize(10).textColor(0xFFDDDDDD).adaptiveWidth(true));
        panel.addChild(title);

        ScrollerView scroller = new ScrollerView();
        scroller.layout(l -> l.flex(1).widthPercent(100));
        List<Race> races = new ArrayList<>(RaceRegistry.playable());
        scroller.viewContainer(view -> {
            view.layout(l -> l.widthPercent(100)
                    .flexDirection(FlexDirection.COLUMN).gapAll(2));
            if (races.isEmpty()) {
                Label empty = new Label();
                empty.setText(Component.translatable(
                        "gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.no_races"));
                empty.textStyle(style -> style.fontSize(9).textColor(0xFF9A9AA0));
                empty.layout(l -> l.widthPercent(100).height(18));
                view.addChild(empty);
            } else {
                for (Race race : races) {
                    view.addChild(saveRaceRow(race));
                }
            }
        });
        panel.addChild(scroller);

        var footer = row();
        Button cancel = smallButton("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".close",
                this::closeSaveRaceDialog);
        cancel.layout(l -> l.flex(1).height(15));
        footer.addChild(cancel);
        panel.addChild(footer);

        overlay.addChild(panel);
        overlay.addEventListener(UIEvents.MOUSE_DOWN, ev -> {
            for (UIElement ancestor = ev.target; ancestor != null;
                 ancestor = ancestor.getParent()) {
                if (ancestor == panel) {
                    return;
                }
            }
            closeSaveRaceDialog();
        }, true);

        rootEl.addChild(overlay);
    }

    private void closeSaveRaceDialog() {
        if (saveRaceOverlay != null) {
            UIElement host = saveRaceOverlay.getParent();
            if (host != null) {
                host.removeChild(saveRaceOverlay);
            }
            saveRaceOverlay = null;
        }
    }

    /** One selectable race row: the destination of the save. */
    private Button saveRaceRow(Race race) {
        Button row = new Button();
        row.noText();
        row.layout(l -> l.widthPercent(100).height(20)
                .flexDirection(FlexDirection.ROW).gapAll(4)
                .alignItems(AlignItems.CENTER));
        boolean current = race.getId().toString().equals(raceId);
        row.style(s -> s.background(new ColorRectTexture(current ? 0xFF3D5C8A : 0xFF2B2B33)));
        UIElement icon = new UIElement();
        icon.layout(l -> l.width(16).height(16));
        icon.style(s -> s.backgroundTexture(new ItemStackTexture(race.getIcon())));
        row.addChild(icon);
        Label label = new Label();
        label.setText(race.getDisplayName());
        label.textStyle(style -> style.fontSize(9).textColor(0xFFDDDDDD));
        label.layout(l -> l.flex(1));
        row.addChild(label);
        row.style(s -> s.tooltips(Component.literal(race.getId().toString())));
        row.setOnClick(e -> {
            closeSaveRaceDialog();
            saveCosmeticsFor(race.getId().toString());
        });
        return row;
    }

    // ------------------------------------------------------------------
    //  Save into the world datapack
    // ------------------------------------------------------------------

    private void saveCosmeticsFor(String targetRaceId) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.singleplayer_only");
            return;
        }
        Identifier id = Identifier.tryParse(targetRaceId);
        if (id == null) {
            showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.failed");
            return;
        }
        Path raceFile = findRaceFile(server, id);
        if (raceFile == null) {
            // Built-in/demo races ship inside the mod jar (read-only). Copy the
            // race JSON into a world datapack — world datapacks load after the
            // jars, so this copy wins and becomes writable.
            try {
                raceFile = materializeRaceFile(server, id);
            } catch (IOException e) {
                dev.originsx.looks.LooksMod.LOGGER.error("Failed to materialize race {} for saving", id, e);
                raceFile = null;
            }
            if (raceFile == null) {
                showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.failed");
                return;
            }
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
            dev.originsx.looks.LooksMod.LOGGER.error("Failed to save cosmetics for {}", targetRaceId, e);
            showToast("gui." + dev.originsx.looks.LooksMod.MOD_ID + ".save.failed");
        }
    }

    /**
     * Copies a race JSON (from the loaded registry) into a writable world
     * datapack so cosmetics can be stored for jar-shipped races.
     */
    private static Path materializeRaceFile(MinecraftServer server, Identifier raceId) throws IOException {
        Race race = RaceRegistry.getOrNull(raceId);
        if (race == null) {
            return null;
        }
        Path packDir = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(
                dev.originsx.looks.LooksMod.MOD_ID);
        Path raceFile = packDir.resolve("data").resolve(raceId.getNamespace())
                .resolve("raceapi").resolve("races").resolve(raceId.getPath() + ".json");
        Files.createDirectories(raceFile.getParent());
        Files.writeString(raceFile, PRETTY.toJson(race.getSourceJson()), StandardCharsets.UTF_8);
        Path meta = packDir.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, "{\n  \"pack\": {\n    \"pack_format\": 90,\n"
                    + "    \"min_format\": 82,\n    \"max_format\": 999,\n"
                    + "    \"description\": \"OriginsX Looks edits\"\n  }\n}\n",
                    StandardCharsets.UTF_8);
        }
        return raceFile;
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
