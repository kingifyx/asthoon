package com.asthoonlite.dungeon.solvers

import com.asthoonlite.dungeon.api.DungeonRoom
import net.minecraft.core.BlockPos

object PuzzleUtils {

    fun rotate(pos: BlockPos, degree: Int): BlockPos {
        return when ((degree % 360 + 360) % 360) {
            0 -> BlockPos(pos.x, pos.y, pos.z)
            90 -> BlockPos(pos.z, pos.y, -pos.x)
            180 -> BlockPos(-pos.x, pos.y, -pos.z)
            270 -> BlockPos(-pos.z, pos.y, pos.x)
            else -> BlockPos(pos.x, pos.y, pos.z)
        }
    }

    fun getRealCoord(pos: BlockPos, roomCenter: BlockPos, rotation: Int): BlockPos {
        val rotated = rotate(pos, rotation)
        return BlockPos(rotated.x + roomCenter.x, pos.y, rotated.z + roomCenter.z)
    }

    fun getRoomCenter(room: DungeonRoom): BlockPos {
        val comp = room.comps.firstOrNull() ?: return BlockPos.ZERO
        return BlockPos(comp.wx, 68, comp.wz)
    }
}
