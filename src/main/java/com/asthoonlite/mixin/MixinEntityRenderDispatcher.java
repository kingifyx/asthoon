package com.asthoonlite.mixin;

import com.asthoonlite.config.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("HEAD")
    )
    private <S extends EntityRenderState> void asthoonlite_onBeforeSubmit(
        S entityRenderState,
        CameraRenderState cameraRenderState,
        double x,
        double y,
        double z,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        CallbackInfo ci
    ) {
        if (Config.INSTANCE.getHideEntityFire()) {
            entityRenderState.displayFireAnimation = false;
        }
    }
}
