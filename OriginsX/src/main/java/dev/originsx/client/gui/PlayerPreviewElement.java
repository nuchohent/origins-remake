package dev.originsx.client.gui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import dev.raceapi.race.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders a detached, never-ticked ghost copy of the player inside the selection
 * GUI, scaled by the race's {@link Race#getScale()}. Because the ghost is not in
 * the world it never animates, so the preview stays stable while the game keeps
 * running behind the screen.
 */
@OnlyIn(Dist.CLIENT)
public class PlayerPreviewElement extends UIElement {

    private final Race race;
    private final float yaw;
    private GhostPlayer ghost;

    public PlayerPreviewElement(Race race, float yaw) {
        this.race = race;
        this.yaw = yaw;
    }

    @Override
    public void drawBackgroundAdditional(IGUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player local = mc.player;
        if (local == null || level == null) {
            return;
        }
        if (ghost == null) {
            ghost = GhostPlayer.ofLocal(level);
        }
        syncAppearance(ghost, local);

        float x = getContentX();
        float y = getContentY();
        float w = getContentWidth();
        float h = getContentHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        // Base size renders a 1.8-block-tall player at ~88% of the panel height.
        // The race scale is applied but clamped so large races never overflow the panel.
        float baseSize = (float) (h * 0.88 / 1.8);
        float maxSize = (float) (h * 0.96 / 1.8);
        float size = Math.min(baseSize * race.getScale(), maxSize);
        size = Math.max(size, baseSize * 0.3f);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super net.minecraft.world.entity.LivingEntity, ?> renderer = dispatcher.getRenderer(ghost);
        EntityRenderState renderState = renderer.createRenderState(ghost, 1.0F);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        if (renderState instanceof LivingEntityRenderState livingRenderState) {
            livingRenderState.bodyRot = 180.0F + yaw;
            livingRenderState.yRot = yaw;
            livingRenderState.xRot = 0.0F;
            livingRenderState.boundingBoxWidth = livingRenderState.boundingBoxWidth / livingRenderState.scale;
            livingRenderState.boundingBoxHeight = livingRenderState.boundingBoxHeight / livingRenderState.scale;
            livingRenderState.scale = 1.0F;
        }

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F, 0.0F);
        ((GUIContext) guiContext).graphics.entity(renderState, size, translation, rotation, null,
                (int) x, (int) y, (int) (x + w), (int) (y + h));
    }

    /** Copies equipment so the preview shows the player's current look. */
    private void syncAppearance(GhostPlayer ghost, Player local) {
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                local.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                local.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND));
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                local.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                local.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,
                local.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
        ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
                local.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
    }
}
