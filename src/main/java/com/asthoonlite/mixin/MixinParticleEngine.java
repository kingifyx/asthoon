package com.asthoonlite.mixin;

import com.asthoonlite.config.Config;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void asthoonlite_onAddParticle(Particle particle, CallbackInfo ci) {
        if (particle == null) return;

        if (Config.INSTANCE.getHideExplosionParticles()) {
            if (particle instanceof HugeExplosionParticle || particle.getClass().getSimpleName().contains("Explosion")) {
                ci.cancel();
                return;
            }
        }

        if (Config.INSTANCE.getHideBlockBreakParticles()) {
            if (particle instanceof TerrainParticle) {
                ci.cancel();
            }
        }
    }
}
