package com.asthoonlite.mixin;

import com.asthoonlite.dungeon.SecretHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LeverBlock.class)
public class MixinLeverBlock {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void asthoonlite$fullTileShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (SecretHitboxes.isValidLever(pos)) {
            cir.setReturnValue(SecretHitboxes.getLeverShape(state, pos));
        }
    }
}
