package com.asthoonlite.mixin;

import com.asthoonlite.render.ItemModelCustomizer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onGetCurrentSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if (ItemModelCustomizer.INSTANCE.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && (Object) this == mc.player) {
                cir.setReturnValue(ItemModelCustomizer.INSTANCE.getSwingDuration());
            }
        }
    }
}
