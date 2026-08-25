package dev.originsx.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPass.class)
public class RenderPassMixin {

    @Inject(method = "enableScissor(IIII)V", at = @At("HEAD"), cancellable = true)
    private void originsx$skipZeroScissor(int x, int y, int width, int height, CallbackInfo ci) {
        if (width <= 0 || height <= 0) {
            ci.cancel();
        }
    }
}
