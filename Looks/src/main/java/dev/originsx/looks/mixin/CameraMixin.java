package dev.originsx.looks.mixin;

import dev.originsx.looks.LooksMod;
import dev.originsx.looks.client.EntityForm;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the camera eye-point adapt to the mob the player is transformed into.
 * Vanilla {@code Camera.tick} lerps {@code eyeHeight} toward
 * {@code entity.getEyeHeight()}; we redirect that call to the mob's natural eye
 * height while the local player is an entity form, so the camera sits at the
 * mob's head instead of a normal player's. Hitbox/aim are untouched.
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Redirect(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
    private float looks$redirectMobEyeHeight(Entity entity) {
        if (entity instanceof AbstractClientPlayer player
                && entity == Minecraft.getInstance().player
                && EntityForm.isTransformed(player)) {
            float mobEye = EntityForm.currentEyeHeight();
            if (mobEye > 0.0F) {
                return mobEye;
            }
        }
        return entity.getEyeHeight();
    }
}

