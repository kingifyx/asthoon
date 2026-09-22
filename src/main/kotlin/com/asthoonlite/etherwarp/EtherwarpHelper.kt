package com.asthoonlite.etherwarp

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object EtherwarpHelper {

    private const val EYE_HEIGHT_STANDING     = 1.62
    /** 1.21 sneak eye height = 1.62 - 0.35 offset, matches the server's ray. */
    private const val EYE_HEIGHT_SNEAK_MODERN = 1.27

    private val mc get() = Minecraft.getInstance()

    data class EtherPos(val succeeded: Boolean, val pos: BlockPos?) {
        companion object {
            val NONE = EtherPos(false, null)
        }
    }

    fun getEtherPos(
        feetPos: Vec3,
        lookVec: Vec3,
        distance: Double,
        sneaking: Boolean
    ): EtherPos {
        val eyeOffset = if (sneaking) EYE_HEIGHT_SNEAK_MODERN else EYE_HEIGHT_STANDING
        val startPos = feetPos.add(0.0, eyeOffset, 0.0)
        val endPos   = startPos.add(lookVec.scale(distance))
        return traverseVoxels(startPos, endPos)
    }

    private fun traverseVoxels(start: Vec3, end: Vec3): EtherPos {
        val world = mc.level ?: return EtherPos.NONE

        var x = floor(start.x).toInt()
        var y = floor(start.y).toInt()
        var z = floor(start.z).toInt()

        val endX = floor(end.x).toInt()
        val endY = floor(end.y).toInt()
        val endZ = floor(end.z).toInt()

        val dirX = end.x - start.x
        val dirY = end.y - start.y
        val dirZ = end.z - start.z

        val stepX = sign(dirX).toInt()
        val stepY = sign(dirY).toInt()
        val stepZ = sign(dirZ).toInt()

        val invDirX = if (dirX != 0.0) 1.0 / dirX else Double.MAX_VALUE
        val invDirY = if (dirY != 0.0) 1.0 / dirY else Double.MAX_VALUE
        val invDirZ = if (dirZ != 0.0) 1.0 / dirZ else Double.MAX_VALUE

        val tDeltaX = abs(invDirX * stepX)
        val tDeltaY = abs(invDirY * stepY)
        val tDeltaZ = abs(invDirZ * stepZ)

        var tMaxX = abs((x + max(stepX, 0) - start.x) * invDirX)
        var tMaxY = abs((y + max(stepY, 0) - start.y) * invDirY)
        var tMaxZ = abs((z + max(stepZ, 0) - start.z) * invDirZ)

        val currentPos = BlockPos.MutableBlockPos()

        repeat(1000) {
            currentPos.set(x, y, z)
            val state = world.getBlockState(currentPos)

            if (isValidEtherwarpBlock(world, currentPos)) return EtherPos(true, currentPos.immutable())
            if (!isPassable(world, currentPos)) return EtherPos(false, currentPos.immutable())
            if (x == endX && y == endY && z == endZ) {
                return if (state.isAir) EtherPos.NONE else EtherPos(false, currentPos.immutable())
            }

            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> { tMaxX += tDeltaX; x += stepX }
                tMaxY <= tMaxZ                   -> { tMaxY += tDeltaY; y += stepY }
                else                             -> { tMaxZ += tDeltaZ; z += stepZ }
            }
        }

        return EtherPos.NONE
    }

    private fun isValidEtherwarpBlock(world: ClientLevel, pos: BlockPos): Boolean {
        if (isPassable(world, pos)) return false
        val state        = world.getBlockState(pos)
        val collisionTop = state.getCollisionShape(world, pos).max(Direction.Axis.Y)
        if (collisionTop <= 0.0) return false
        val feetY = ceil(pos.y + collisionTop).toInt()
        if (!isPassable(world, BlockPos(pos.x, feetY,     pos.z))) return false
        if (!isPassable(world, BlockPos(pos.x, feetY + 1, pos.z))) return false
        return true
    }

    private fun isPassable(world: ClientLevel, pos: BlockPos): Boolean =
        world.getBlockState(pos).getCollisionShape(world, pos).isEmpty
}
