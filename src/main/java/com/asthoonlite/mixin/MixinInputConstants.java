package com.asthoonlite.mixin;

import com.asthoonlite.funny.InventoryAutoClicker;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
public abstract class MixinInputConstants {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void asthoonlite$isKeyDown(Window window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            if (InventoryAutoClicker.INSTANCE.isControlSimulated()) {
                cir.setReturnValue(true);
            }
        }
    }
}
