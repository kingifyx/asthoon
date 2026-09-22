package com.asthoonlite.mixin;

import com.asthoonlite.dungeon.StarMobESP;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_starTeamColor(CallbackInfoReturnable<Integer> cir) {
        int color = StarMobESP.INSTANCE.glowColorFor((Entity)(Object)this);
        if (color != Integer.MIN_VALUE) cir.setReturnValue(color);
    }
}
