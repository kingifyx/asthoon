package com.asthoonlite.mixin;

import com.asthoonlite.dungeon.SecretHitboxes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @WrapOperation(
            method = "extractBlockOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
            )
    )
    private VoxelShape asthoonlite$wrapOutlineShape(BlockState state, BlockGetter level, BlockPos pos,
                                                    CollisionContext context, Operation<VoxelShape> original) {
        if (SecretHitboxes.shouldOverrideOutline(state, pos)) {
            return SecretHitboxes.getOutlineShape(state, pos, original.call(state, level, pos, context));
        }
        return original.call(state, level, pos, context);
    }
}
