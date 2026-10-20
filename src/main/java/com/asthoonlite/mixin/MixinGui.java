package com.asthoonlite.mixin;

import com.asthoonlite.config.Config;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    @Inject(
        method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
        at = @At("HEAD")
    )
    private void asthoonlite_onExtractSlotHead(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        DeltaTracker deltaTracker,
        Player player,
        ItemStack itemStack,
        int seed,
        CallbackInfo ci
    ) {
        com.asthoonlite.qol.ItemRarityOverlay.INSTANCE.drawItemBackground(graphics, itemStack, x, y);
    }

    @Inject(
        method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
        at = @At("TAIL")
    )
    private void asthoonlite_onExtractSlotTail(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        DeltaTracker deltaTracker,
        Player player,
        ItemStack itemStack,
        int seed,
        CallbackInfo ci
    ) {
        com.asthoonlite.qol.ItemRarityOverlay.INSTANCE.drawItemOverlays(graphics, itemStack, x, y);
    }
}
