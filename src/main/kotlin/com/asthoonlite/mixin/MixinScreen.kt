package com.asthoonlite.mixin

import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.AutoTerminal
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Screen::class)
abstract class MixinScreen {
    @Inject(method = ["keyPressed"], at = [At("HEAD")])
    private fun asthoonlite_terminalEscape(event: KeyEvent, cir: CallbackInfoReturnable<Boolean>) {
        if (!Config.autoTerminalEnabled || event.key() != GLFW.GLFW_KEY_ESCAPE) return
        if ((this as? AbstractContainerScreen<*>)?.title?.string?.let { AutoTerminal.isTerminalTitle(it) } == true) {
            AutoTerminal.onEscape()
        }
    }
}
