package com.asthoonlite.mixin;

import com.asthoonlite.config.Config;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EffectsInInventory.class)
public abstract class MixinEffectsInInventory {

    @Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onCanSeeEffects(CallbackInfoReturnable<Boolean> cir) {
        if (Config.INSTANCE.getHideInventoryEffects()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void asthoonlite_onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (Config.INSTANCE.getHideInventoryEffects()) {
            ci.cancel();
        }
    }
}
