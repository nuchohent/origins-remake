package dev.originsx.skilltree.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.originsx.skilltree.SkillTreeMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry: registers the open-skill-tree keybind and clears the cache on
 * disconnect. Key handling lives in {@link GameEvents#onKeyInput}.
 */
public final class SkillTreeClient {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(net.minecraft.resources.Identifier
                    .fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skilltree"));

    public static final KeyMapping OPEN_SKILL_TREE = new KeyMapping(
            "key.originsx_skilltree.open_tree",
            InputConstants.Type.KEYSYM,
            // J is taken by OriginsX HUD settings; K keeps the tree one key away
            GLFW.GLFW_KEY_K,
            CATEGORY);

    private SkillTreeClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(GameEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(GameEvents::onKeyInput);
        NeoForge.EVENT_BUS.addListener(GameEvents::onLoggingOut);
        SkillTreeTabs.register();
    }

    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        // Ask for a fresh snapshot; the screen renders whatever arrives
        net.neoforged.neoforge.client.network.ClientPacketDistributor
                .sendToServer(new dev.originsx.skilltree.net.RequestSkillStatePayload());
        mc.setScreenAndShow(new SkillTreeScreen());
    }

    /**
     * Opens the tree editor for a race (called from the race creator).
     * Always starts with a blank grid — an existing tree is never loaded.
     */
    public static void openEditor(String raceId) {
        openEditor(raceId, "[]");
    }

    /**
     * @param powersJson JSON {@code [[index, name], ...]} of the draft's powers,
     *                   shown as a picker in the editor's index field.
     */
    public static void openEditor(String raceId, String powersJson) {
        openEditor(raceId, powersJson, null);
    }

    /**
     * @param powersJson JSON {@code [[index, name], ...]} of the draft's powers,
     *                   shown as a picker in the editor's index field.
     * @param editorReturn run by the editor's "back to creator" button
     */
    public static void openEditor(String raceId, String powersJson, Runnable editorReturn) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        mc.setScreenAndShow(new SkillTreeScreen(raceId == null ? "" : raceId, powersJson, editorReturn));
    }

    @EventBusSubscriber(modid = SkillTreeMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_SKILL_TREE);
        }

        public static void onKeyInput(InputEvent.Key event) {
            if (OPEN_SKILL_TREE.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) {
                    openScreen();
                }
            }
        }

        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            SkillTreeClientState.apply(new dev.originsx.skilltree.net.SyncSkillStatePayload("", "", ""));
        }
    }

    /** Shown in the header: how many shards the local player carries. */
    public static Component shardsComponent() {
        var player = Minecraft.getInstance().player;
        int count = player == null ? 0 : dev.originsx.skilltree.tree.Shards.count(player);
        return Component.translatable("gui.originsx_skilltree.shards", count);
    }
}
