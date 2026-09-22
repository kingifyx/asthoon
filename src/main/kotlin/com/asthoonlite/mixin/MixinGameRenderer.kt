package com.asthoonlite.mixin

import com.asthoonlite.render.EtherwarpOverlay
import net.minecraft.client.renderer.GameRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

// Required per Fabric's "Rendering in the World" docs for 26.1.2: custom
// render pipelines must release their GPU buffers when GameRenderer closes.
@Mixin(GameRenderer::class)
abstract class MixinGameRenderer {

    @Inject(method = ["close"], at = [At("RETURN")])
    private fun asthoonlite_onClose(ci: CallbackInfo) {
        EtherwarpOverlay.close()
    }
}
