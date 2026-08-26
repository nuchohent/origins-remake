package dev.originsx.looks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the cosmetics of the rendered avatar: each entry is an item attached
 * to a player-model bone (head/body/arms/legs) with pos/rot/scale.
 * <p>
 * Runs on every player renderer (and mannequins), so cosmetics are visible to
 * everyone — other players' races arrive via the race broadcast payload.
 */
@OnlyIn(Dist.CLIENT)
public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float yRot, float xRot) {
        var extracted = state.getRenderData(LooksClient.RENDER_DATA);
        if (extracted == null || extracted.isEmpty() || state.isInvisible) {
            return;
        }
        PlayerModel model = getParentModel();
        for (CosmeticsStateModifier.Extracted data : extracted) {
            ModelPart bone = bone(model, data.entry().part());
            if (bone == null) {
                continue;
            }
            poseStack.pushPose();
            // follow the bone, including head pitch/yaw and walk animation
            bone.translateAndRotate(poseStack);
            applyTransform(poseStack, data.entry());
            data.itemState().submit(poseStack, collector, light,
                    OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
    }

    /**
     * Entry transform in model space:
     * pos is in blocks from the bone anchor (x16), rot are XYZ degrees
     * applied X then Y then Z, scale multiplies afterwards.
     */
    private static void applyTransform(PoseStack poseStack, Cosmetics.Entry entry) {
        float[] pos = entry.pos();
        poseStack.translate(pos[0] * 16f, -pos[1] * 16f, pos[2] * 16f);
        float[] rot = entry.rot();
        poseStack.mulPose(Axis.XP.rotationDegrees(rot[0]));
        poseStack.mulPose(Axis.YP.rotationDegrees(rot[1]));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rot[2]));
        float scale = entry.scale() <= 0f ? 1f : entry.scale();
        poseStack.scale(scale, scale, scale);
    }

    private static ModelPart bone(PlayerModel model, Cosmetics.Part part) {
        return switch (part) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }
}
