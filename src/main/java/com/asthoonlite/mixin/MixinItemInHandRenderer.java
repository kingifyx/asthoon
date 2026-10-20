package com.asthoonlite.mixin;

import com.asthoonlite.config.Config;
import com.asthoonlite.render.ItemModelCustomizer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onShouldSkipEquipAnimation(ItemStack prev, ItemStack current, CallbackInfoReturnable<Boolean> cir) {
        if (Config.INSTANCE.getInstantEquip()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void asthoonlite_onTickHeldItems(CallbackInfo ci) {
        if (Config.INSTANCE.getInstantEquip()) {
            this.mainHandHeight = 1.0f;
            this.oMainHandHeight = 1.0f;
            this.offHandHeight = 1.0f;
            this.oOffHandHeight = 1.0f;
        }
    }

    @Inject(
        method = "renderArmWithItem",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void asthoonlite_onBeforeRenderItem(
        AbstractClientPlayer player,
        float f,
        float g,
        InteractionHand hand,
        float attack,
        ItemStack itemStack,
        float inverseArmHeight,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        CallbackInfo ci
    ) {
        if (!ItemModelCustomizer.INSTANCE.isEnabled() || itemStack.isEmpty()) return;

        float sign = (hand == InteractionHand.MAIN_HAND) ? 1.0f : -1.0f;
        poseStack.translate(
            ItemModelCustomizer.INSTANCE.getMainX() * sign,
            ItemModelCustomizer.INSTANCE.getMainY(),
            ItemModelCustomizer.INSTANCE.getMainZ()
        );
    }

    @Inject(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
        )
    )
    private void asthoonlite_onScaleItem(
        AbstractClientPlayer player,
        float frameInterp,
        float xRot,
        InteractionHand hand,
        float attack,
        ItemStack itemStack,
        float inverseArmHeight,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        CallbackInfo ci
    ) {
        if (!ItemModelCustomizer.INSTANCE.isEnabled()) return;

        float s = (hand == InteractionHand.MAIN_HAND)
            ? ItemModelCustomizer.INSTANCE.getMainScale()
            : ItemModelCustomizer.INSTANCE.getOffScale();
        poseStack.scale(s, s, s);
    }
}
