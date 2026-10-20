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
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Inject(method = "releaseMouse()V", at = @At("TAIL"))
    private void asthoonlite_onReleaseMouse(CallbackInfo ci) {
        if (com.asthoonlite.storage.StorageSystem.INSTANCE.shouldPreserveCursor()) {
            double[] pos = com.asthoonlite.storage.StorageSystem.INSTANCE.getPreservedCursor();
            if (pos != null) {
                this.xpos = pos[0];
                this.ypos = pos[1];
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(
                    net.minecraft.client.Minecraft.getInstance().getWindow().handle(),
                    pos[0],
                    pos[1]
                );
            }
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void asthoonlite_onTurnPlayer(double timeDelta, CallbackInfo ci) {
        if (ZoomHandler.INSTANCE.isZooming()) {
            double mult = ZoomHandler.INSTANCE.getFovMultiplier();
            this.accumulatedDX *= mult;
            this.accumulatedDY *= mult;
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onScroll(long window, double xoffset, double yoffset, CallbackInfo ci) {
        if (ZoomHandler.INSTANCE.onMouseScroll(yoffset)) {
            ci.cancel();
        }
    }
}
