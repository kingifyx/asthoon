package com.asthoonlite.mixin;

import com.asthoonlite.config.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class MixinScreenEffectRenderer {

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void asthoonlite_onRenderFire(
        PoseStack poseStack,
        MultiBufferSource multiBufferSource,
        TextureAtlasSprite textureAtlasSprite,
        CallbackInfo ci
    ) {
        if (Config.INSTANCE.getHideFireOverlay()) {
            ci.cancel();
        }
    }
}
