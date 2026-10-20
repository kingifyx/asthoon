package com.asthoonlite.mixin;

import com.asthoonlite.config.Config;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Lightmap.class)
public abstract class MixinLightmap {

    @Unique
    private static final Vector3fc ASTHOONLITE$WHITE = new Vector3f(1.0f, 1.0f, 1.0f);

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private LightmapRenderState asthoonlite_onRenderLightmap(LightmapRenderState renderState) {
        if (Config.INSTANCE.getDisableNightVision()) {
            renderState.nightVisionEffectIntensity = 0.0f;
        }

        if (Config.INSTANCE.getFullbrightEnabled()) {
            renderState.skyFactor = 1.0f;
            renderState.blockFactor = 1.0f;
            renderState.brightness = 1.0f;
            renderState.darknessEffectScale = 0.0f;
            renderState.nightVisionEffectIntensity = 0.0f;
            renderState.bossOverlayWorldDarkening = 0.0f;
            renderState.blockLightTint = ASTHOONLITE$WHITE;
            renderState.skyLightColor = ASTHOONLITE$WHITE;
            renderState.ambientColor = ASTHOONLITE$WHITE;
            renderState.nightVisionColor = ASTHOONLITE$WHITE;
        }

        return renderState;
    }
}
