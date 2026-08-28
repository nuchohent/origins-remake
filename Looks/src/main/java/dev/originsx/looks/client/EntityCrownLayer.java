package dev.originsx.looks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Draws the cosmetics of the local player's entity form on the mob's model.
 * <p>
 * The Minecraft render pipeline (both the player avatar and generic mob
 * renderers) works in <b>block units</b> (1.0 = 1 block) at the point layers
 * are drawn, so slot positions are expressed in blocks relative to the mob's
 * feet. Because a non-humanoid mob has no player bones (head/arms/legs slots),
 * each slot is fixed at a proportional offset on the mob's body:
 * <ul>
 *   <li>HEAD -> above the head (crown)</li>
 *   <li>BODY -> middle of the body</li>
 *   <li>LEFT_ARM / RIGHT_ARM -> upper flanks</li>
 *   <li>LEFT_LEG / RIGHT_LEG -> lower flanks</li>
 * </ul>
 * <p>
 * Only renders for the ghost mob: gated by {@link EntityForm#currentType()}
 * and by matching the rendered mob's position to the local player.
 */
@OnlyIn(Dist.CLIENT)
public final class EntityCrownLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** Overall item size multiplier so real-world items read at a sane size. */
    private static final float MOB_ITEM_SCALE = getFloat("looks.mobItemScale", 0.3f);

    /** Extra lift in +Y (blocks) applied to every cosmetic (0 = off). */
    private static final float DEBUG_MOB_OFFSET = getFloat("looks.mobOffset", 0f);

    private final EntityType<?> type;

    @SuppressWarnings("unused")
    public EntityCrownLayer(RenderLayerParent<S, M> parent, EntityType<?> type) {
        super(parent);
        this.type = type;
    }

    private static float getFloat(String key, float def) {
        try {
            return Float.parseFloat(System.getProperty(key, Float.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
                       S state, float yRot, float xRot) {
        // Editor preview: the viewport bakes the current entries into RENDER_DATA
        // on the state, so render those directly (no world/player gating).
        var previewed = state.getRenderData(LooksClient.RENDER_DATA);
        if (previewed != null && !previewed.isEmpty()) {
            renderItems(poseStack, collector, light, state.outlineColor, previewed,
                    EntityForm.currentHeight());
            return;
        }
        EntityType<?> current = EntityForm.currentType();
        // only while the local player is this exact mob
        if (current == null || current != type) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // only the ghost: it sits at the local player's feet, so reject any other
        // mob of this type elsewhere in the world
        float dx = (float) state.x - (float) mc.player.getX();
        float dy = (float) state.y - (float) mc.player.getY();
        float dz = (float) state.z - (float) mc.player.getZ();
        if (dx * dx + dy * dy + dz * dz > 0.25f) {
            return;
        }
        var extracted = CosmeticsStateModifier.extract(mc.player);
        if (extracted == null || extracted.isEmpty()) {
            return;
        }
        renderItems(poseStack, collector, light, state.outlineColor, extracted,
                EntityForm.currentHeight());
    }

    private static void renderItems(PoseStack poseStack, SubmitNodeCollector collector,
                                    int light, int outlineColor,
                                    List<CosmeticsStateModifier.Extracted> items, float h) {
        float w = h * 0.5f;
        for (CosmeticsStateModifier.Extracted data : items) {
            Cosmetics.Entry entry = data.entry();
            float[] offset = offset(entry.part(), h, w);
            poseStack.pushPose();
            poseStack.translate(offset[0], offset[1] + DEBUG_MOB_OFFSET, offset[2]);
            float[] rot = entry.rot();
            poseStack.mulPose(Axis.XP.rotationDegrees(rot[0]));
            poseStack.mulPose(Axis.YP.rotationDegrees(rot[1]));
            poseStack.mulPose(Axis.ZP.rotationDegrees(rot[2]));
            float scale = entry.scale() <= 0f ? 1f : entry.scale();
            poseStack.scale(scale * MOB_ITEM_SCALE, scale * MOB_ITEM_SCALE, scale * MOB_ITEM_SCALE);
            data.itemState().submit(poseStack, collector, light,
                    OverlayTexture.NO_OVERLAY, outlineColor);
            poseStack.popPose();
        }
    }

    private static float[] offset(Cosmetics.Part part, float h, float w) {
        return switch (part) {
            case HEAD -> new float[]{0f, h * 0.66f, 0f};
            case BODY -> new float[]{0f, h * 0.38f, 0f};
            case LEFT_ARM -> new float[]{-w * 0.85f, h * 0.5f, 0f};
            case RIGHT_ARM -> new float[]{w * 0.85f, h * 0.5f, 0f};
            case LEFT_LEG -> new float[]{-w * 0.7f, h * 0.14f, 0f};
            case RIGHT_LEG -> new float[]{w * 0.7f, h * 0.14f, 0f};
            case CAPE -> new float[]{0f, h * 0.45f, 0f};
        };
    }
}
