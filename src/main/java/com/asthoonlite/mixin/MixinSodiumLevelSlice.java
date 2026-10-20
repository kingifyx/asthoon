package com.asthoonlite.mixin;

import com.asthoonlite.dungeon.DoorSeeThrough;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class MixinSodiumLevelSlice {

    @ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), require = 0)
    private BlockState asthoonlite_replaceSodiumDoorRenderState(BlockState original, int x, int y, int z) {
        return DoorSeeThrough.INSTANCE.getRenderState(x, y, z, original);
    }
}
