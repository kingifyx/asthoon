package com.asthoonlite.mixin;

import com.asthoonlite.qol.ZoomHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void asthoonlite_onTurnPlayer(double timeDelta, CallbackInfo ci) {
        if (ZoomHandler.INSTANCE.isZooming()) {
            double mult = ZoomHandler.INSTANCE.getFovMultiplier();
            this.accumulatedDX *= mult;
            this.accumulatedDY *= mult;
        }
    }
}
