package com.asthoonlite.mixin;

import com.asthoonlite.config.Config;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MixinGui {

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onExtractFood(GuiGraphicsExtractor graphics, Player player, int yLineBase, int xRight, CallbackInfo ci) {
        if (Config.INSTANCE.getHideFoodBar()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onExtractPlayerHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (Config.INSTANCE.getHideVanillaHearts() || Config.INSTANCE.getCondensedHealthBar()) {
            ci.cancel();
        }
    }
}
