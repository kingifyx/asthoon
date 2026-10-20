package com.asthoonlite.mixin;

import com.asthoonlite.chat.ChatCopyHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class MixinChatScreen {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == 1) {
            ChatScreen self = (ChatScreen) (Object) this;
            if (ChatCopyHandler.INSTANCE.handleRightClick(self, event.x(), event.y())) {
                cir.setReturnValue(true);
            }
        }
    }
}
