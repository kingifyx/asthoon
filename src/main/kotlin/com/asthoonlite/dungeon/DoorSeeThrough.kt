package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import net.minecraft.client.Minecraft
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

object DoorSeeThrough {

    private val blackGlassState by lazy { Blocks.BLACK_STAINED_GLASS.defaultBlockState() }
    private val redGlassState by lazy { Blocks.RED_STAINED_GLASS.defaultBlockState() }

    fun getRenderState(x: Int, y: Int, z: Int, original: BlockState): BlockState {
        if (!Config.doorSeeThrough) return original
        if (!DungeonContext.inDungeon || DungeonContext.inBoss) return original
        if (y !in 69..73) return original

        val block = original.block
        if (block == Blocks.COAL_BLOCK) {
            return blackGlassState
        }
        if (block == Blocks.RED_TERRACOTTA) {
            return redGlassState
        }
        return original
    }

    fun refresh() {
        Minecraft.getInstance().execute {
            Minecraft.getInstance().levelRenderer?.allChanged()
        }
    }
}
