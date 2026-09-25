package com.asthoonlite.mixin;

import com.asthoonlite.funny.InventoryAutoClicker;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseButtonEvent.class)
public abstract class MixinMouseButtonEvent {
    @Inject(method = "modifiers", at = @At("RETURN"), cancellable = true)
    private void asthoonlite$forceModifiersControl(CallbackInfoReturnable<Integer> cir) {
        if (InventoryAutoClicker.INSTANCE.isControlSimulated()) {
            cir.setReturnValue(cir.getReturnValue() | 2); // GLFW_MOD_CONTROL = 2
        }
    }
}
