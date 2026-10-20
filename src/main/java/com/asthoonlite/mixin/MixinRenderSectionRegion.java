package com.asthoonlite.mixin;

import com.asthoonlite.dungeon.DoorSeeThrough;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderSectionRegion.class)
public abstract class MixinRenderSectionRegion {

    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState asthoonlite_replaceDoorRenderState(BlockState original, BlockPos pos) {
        return DoorSeeThrough.INSTANCE.getRenderState(pos.getX(), pos.getY(), pos.getZ(), original);
    }
}
