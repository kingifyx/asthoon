package com.asthoonlite.mixin;

import com.asthoonlite.qol.ZoomHandler;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void asthoonlite_onCalculateFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (ZoomHandler.INSTANCE.isZooming()) {
            cir.setReturnValue(cir.getReturnValue() * ZoomHandler.INSTANCE.getFovMultiplier());
        }
    }
}
