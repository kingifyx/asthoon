package com.asthoonlite.mixin;

import com.asthoonlite.qol.ItemProtection;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onDropItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        // In Dungeons, dropping triggers the class ultimate ability and Hypixel blocks item drops.
        if (com.asthoonlite.dungeon.DungeonContext.INSTANCE.getInDungeon()) {
            return;
        }
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (ItemProtection.INSTANCE.isProtected(self.getInventory().getSelectedItem())) {
            ItemProtection.INSTANCE.notify("§cAction Blocked: This item is protected!");
            cir.setReturnValue(false);
        }
    }
}
