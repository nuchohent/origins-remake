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

import java.util.Comparator;
import java.util.List;

/**
 * Draws the cosmetics of the rendered avatar: each entry is an item attached
 * to a player-model bone (head/body/arms/legs/cape) with pos/rot/scale + animation.
 * <p>
 * Runs on every player renderer (and mannequins), so cosmetics are visible to
 * everyone — other players' races arrive via the race broadcast payload.
 */
@OnlyIn(Dist.CLIENT)
public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** Blocks to lift every cosmetic in +Y when debugging visibility (0 = off). */
    private static final float DEBUG_RENDER_OFFSET =
            net.minecraft.util.Mth.clamp(getDebugOffset(), 0f, 8f);

    /**
     * Model-space units per editor unit. The player model uses 16 px per block;
     * the editor pos fields were previously in whole blocks (x16), so a value
     * of just 1 (or even 0.1) threw a long cosmetic thousands of px into the
     * geometry and buried it. 16/8 = 2 keeps the same 1/8-block granularity the
     * editor roughly implied while staying in a sane range.
     */
    private static final float PX_PER_UNIT = 2f;

    /** Full block+sky light (0xF000F0) used for glowing cosmetics. */
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    /** Model units the head bone pivot is below the crown (skull is ~8 px tall). */
    private static final float HEAD_BASE_LIFT = 6f;

    /** Cape offset from body bone (back, slightly above waist). */
    private static final float CAPE_OFFSET_X = 0f;
    private static final float CAPE_OFFSET_Y = -0.25f;
    private static final float CAPE_OFFSET_Z = 0.1f;

    private static float getDebugOffset() {
        try {
            return Float.parseFloat(System.getProperty("looks.debugRenderOffset", "0"));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float yRot, float xRot) {
        var extracted = state.getRenderData(LooksClient.RENDER_DATA);
        if (extracted == null || extracted.isEmpty()) {
            return;
        }
        if (state.isInvisible) {
            return;
        }
        PlayerModel model = getParentModel();
        if (model == null) {
            return;
        }
        // Sort by zIndex so lower layers render first (underneath)
        List<CosmeticsStateModifier.Extracted> sorted = extracted.stream()
                .sorted(Comparator.comparingInt(e -> e.entry().zIndex()))
                .toList();
        for (CosmeticsStateModifier.Extracted data : sorted) {
            int entryLight = data.entry().glow() ? FULL_BRIGHT_LIGHT : light;
            int tint = state.outlineColor != 0 ? state.outlineColor : data.entry().tint();
            if (data.entry().part() == Cosmetics.Part.CAPE) {
                renderCape(poseStack, collector, light, tint, data, state);
                continue;
            }
            ModelPart bone = bone(model, data.entry().part());
            if (bone == null) {
                continue;
            }
            poseStack.pushPose();
            bone.translateAndRotate(poseStack);
            applyTransform(poseStack, data.entry());
            data.itemState().submit(poseStack, collector, light,
                    OverlayTexture.NO_OVERLAY, tint);
            poseStack.popPose();
        }
    }

    private void renderCape(PoseStack poseStack, SubmitNodeCollector collector, int light, int tint,
                             CosmeticsStateModifier.Extracted data, AvatarRenderState state) {
        poseStack.pushPose();
        // Cape attaches to body bone - translate to the back
        poseStack.translate(CAPE_OFFSET_X * PX_PER_UNIT, CAPE_OFFSET_Y * PX_PER_UNIT, CAPE_OFFSET_Z * PX_PER_UNIT);
        applyTransform(poseStack, data.entry());
        data.itemState().submit(poseStack, collector, light,
                OverlayTexture.NO_OVERLAY, tint);
        poseStack.popPose();
    }

    /**
     * Entry transform in model space:
     * pos is in blocks from the bone anchor (x16), rot are XYZ degrees
     * applied X then Y then Z, scale multiplies afterwards.
     * Animation (bob/rotate/pulse) applied on top.
     */
    private static void applyTransform(PoseStack poseStack, Cosmetics.Entry entry) {
        float[] pos = entry.pos();
        float lift = DEBUG_RENDER_OFFSET
                + (entry.part() == Cosmetics.Part.HEAD ? HEAD_BASE_LIFT : 0f);
        poseStack.translate(pos[0] * PX_PER_UNIT, -pos[1] * PX_PER_UNIT + lift,
                pos[2] * PX_PER_UNIT);
        float[] rot = entry.rot();
        poseStack.mulPose(Axis.XP.rotationDegrees(rot[0]));
        poseStack.mulPose(Axis.YP.rotationDegrees(rot[1]));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rot[2]));

        // Animation transforms
        Cosmetics.AnimConfig anim = entry.anim();
        if (anim.isAnimated()) {
            float time = System.currentTimeMillis() / 1000.0f;

            // Rotate around Y
            if (anim.rotateSpeed() != 0f) {
                poseStack.mulPose(Axis.YP.rotationDegrees(time * anim.rotateSpeed()));
            }

            // Bob up/down
            if (anim.bobAmplitude() != 0f && anim.bobSpeed() != 0f) {
                float bobY = (float)(Math.sin(time * anim.bobSpeed()) * anim.bobAmplitude());
                poseStack.translate(0, bobY, 0);
            }

            // Pulse scale (subtle size oscillation)
            if (anim.pulseAmplitude() != 0f) {
                float pulse = 1.0f + (float)(Math.sin(time * anim.bobSpeed()) * anim.pulseAmplitude()) * 0.05f;
                poseStack.scale(pulse, pulse, pulse);
            }
        }

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
            case CAPE -> null; // cape has no bone, renders via offset
        };
    }
}