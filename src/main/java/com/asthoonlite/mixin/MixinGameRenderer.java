package com.asthoonlite.mixin;

import com.asthoonlite.pathfinding.PathExecutor;
import com.asthoonlite.render.EtherwarpOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Inject(method = "close()V", at = @At("RETURN"))
    private void asthoonlite_onClose(CallbackInfo ci) {
        EtherwarpOverlay.INSTANCE.close();
    }

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void asthoonlite_onUpdate(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        PathExecutor.INSTANCE.onRenderFrame(deltaTracker);
    }

    @Inject(method = "getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F", at = @At("HEAD"), cancellable = true)
    private static void asthoonlite_onGetNightVisionScale(LivingEntity entity, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (com.asthoonlite.config.Config.INSTANCE.getDisableNightVision()) {
            cir.setReturnValue(0.0f);
        }
    }
}
