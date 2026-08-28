package dev.originsx.looks.mixin;

import dev.originsx.looks.client.EntityForm;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the block-pick / interaction ray originate from the mob's head instead
 * of the normal player eye, so what you aim and what the camera shows agree.
 * {@code Entity.getEyePosition(float)} builds the ray origin as the entity's
 * feet plus {@code getEyeHeight()}; we redirect that call so a transformed
 * local player aims from the mob's natural eye height.
 */
@Mixin(Entity.class)
public class EntityAimMixin {

    @Redirect(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
    private float looks$aimFromMobHead(Entity entity) {
        float mobEye = EntityForm.formEyeHeightFor(entity);
        return mobEye >= 0.0F ? mobEye : entity.getEyeHeight();
    }
}
