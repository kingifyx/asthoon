package com.asthoonlite.dungeon.solvers

import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.DungeonContext
import com.asthoonlite.dungeon.map.DungeonScanner
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.level.block.Blocks
import kotlin.math.*

object TeleportMazeSolver {

    private var minX: Int? = null
    private var minZ: Int? = null
    private var cells: List<Cell>? = null
    private var orderedPads: MutableList<TpPad>? = null
    private var inTpMaze = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { queueRender() }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    fun onPlayerPositionPacket(packet: ClientboundPlayerPositionPacket) {
        if (!inTpMaze || !Config.teleportMazeSolverEnabled || !DungeonContext.inDungeon) return
        val pos = packet.change.position
        val player = Minecraft.getInstance().player ?: return
        if (pos.x % 0.5 != 0.0 || pos.y != 69.5 || pos.z % 0.5 != 0.0) return

        val oldPad = getPadNear(player.x, player.z) ?: return
        val newPad = getPadNear(pos.x, pos.z) ?: return
        if (isPadInStartOrEndCell(newPad)) return

        newPad.blacklisted = true
        oldPad.blacklisted = true

        calcPadAngles(pos.x, pos.z, packet.change.yRot)
    }

    private fun tick() {
        if (!Config.teleportMazeSolverEnabled || !DungeonContext.inDungeon) {
            if (inTpMaze) reset()
            return
        }

        val room = DungeonScanner.currentRoom
        if (room == null || room.name != "Teleport Maze") {
            if (inTpMaze) reset()
            return
        }

        if (!inTpMaze) {
            inTpMaze = true
            val center = PuzzleUtils.getRoomCenter(room)
            scan(center)
        }
    }

    private fun queueRender() {
        if (!inTpMaze || !Config.teleportMazeSolverEnabled || !DungeonContext.inDungeon) return
        val c = cells ?: return
        val top = orderedPads?.takeIf { it.size >= 2 }?.take(2)

        if (top != null && top[0].totalAngle != top[1].totalAngle) {
            val padPos = top[0].pos
            WorldBoxRenderer.queueFilled(
                padPos.x.toDouble(), padPos.y.toDouble(), padPos.z.toDouble(),
                padPos.x + 1.0, padPos.y + 1.0, padPos.z + 1.0,
                0f, 1f, 0f, 0.5f, throughWalls = true
            )
            WorldBoxRenderer.queueOutline(
                padPos.x.toDouble(), padPos.y.toDouble(), padPos.z.toDouble(),
                padPos.x + 1.0, padPos.y + 1.0, padPos.z + 1.0,
                0f, 1f, 0f, 1f, thickness = 0.03, throughWalls = true
            )
        }

        for (cell in c) {
            for (pad in cell.pads) {
                if (!pad.blacklisted) continue
                val padPos = pad.pos
                WorldBoxRenderer.queueFilled(
                    padPos.x.toDouble(), padPos.y.toDouble(), padPos.z.toDouble(),
                    padPos.x + 1.0, padPos.y + 1.0, padPos.z + 1.0,
                    1f, 0.2f, 0.2f, 0.4f, throughWalls = true
                )
                WorldBoxRenderer.queueOutline(
                    padPos.x.toDouble(), padPos.y.toDouble(), padPos.z.toDouble(),
                    padPos.x + 1.0, padPos.y + 1.0, padPos.z + 1.0,
                    1f, 0.2f, 0.2f, 0.9f, thickness = 0.03, throughWalls = true
                )
            }
        }
    }

    private fun scan(center: BlockPos) {
        val level = Minecraft.getInstance().level ?: return
        val pads = mutableListOf<TpPad>()
        for (dx in 0..31) for (dz in 0..31) {
            val pos = BlockPos(center.x + dx - 16, 69, center.z + dz - 16)
            if (level.getBlockState(pos).block != Blocks.END_PORTAL_FRAME) continue
            pads += TpPad(pos)
        }

        if (pads.isEmpty()) return

        minX = pads.minOf { it.pos.x }
        minZ = pads.minOf { it.pos.z }
        cells = List(9) { i -> Cell(i / 3, i % 3) }

        val mX = minX!!
        val mZ = minZ!!

        for (pad in pads) {
            pad.cellX = (pad.pos.x - mX) / 8
            pad.cellZ = (pad.pos.z - mZ) / 8

            val index = (pad.cellX * 3 + pad.cellZ).coerceIn(0, 8)
            cells!![index].pads += pad
        }
    }

    private fun calcPadAngles(px: Double, pz: Double, yaw: Float) {
        val currentPads = mutableListOf<TpPad>()
        val c = cells ?: return

        for (cell in c) {
            for (pad in cell.pads) {
                if (isPadInStartOrEndCell(pad) || pad.blacklisted) continue

                val dx = pad.pos.x + 0.5 - px
                val dz = pad.pos.z + 0.5 - pz
                val angle = getAngleDiff(dx, dz, yaw)

                pad.totalAngle += angle
                currentPads.add(pad)
            }
        }

        currentPads.sortBy { it.totalAngle }
        orderedPads = currentPads
    }

    private fun getAngleDiff(dx: Double, dz: Double, yaw: Float): Double {
        val yawRad = Math.toRadians(yaw.toDouble())
        val lookX = -sin(yawRad)
        val lookZ = cos(yawRad)

        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 1e-5) return 0.0
        val targetX = dx / dist
        val targetZ = dz / dist

        val dot = (lookX * targetX + lookZ * targetZ).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(dot))
    }

    private fun getCellAt(x: Double, z: Double): Cell? {
        val mX = minX ?: return null
        val mZ = minZ ?: return null

        val xi = x.toInt()
        val zi = z.toInt()

        if (xi < mX || xi > mX + 23 || zi < mZ || zi > mZ + 23) return null

        val cellX = (xi - mX) / 8
        val cellZ = (zi - mZ) / 8

        return cells?.find { it.xIndex == cellX && it.zIndex == cellZ }
    }

    private fun getPadNear(x: Double, z: Double): TpPad? {
        val cell = getCellAt(x, z) ?: return null
        return cell.pads.find {
            (abs(x - (it.pos.x + 0.5)) + abs(z - (it.pos.z + 0.5))) <= 3.0
        }
    }

    private fun isPadInStartOrEndCell(pad: TpPad): Boolean {
        val c = cells ?: return false
        if (c.getOrNull(4)?.pads?.contains(pad) == true) return true

        for (cell in c) {
            if (cell != c[4] && cell.pads.size == 1 && pad in cell.pads) {
                return true
            }
        }
        return false
    }

    fun reset() {
        inTpMaze = false
        minX = null
        minZ = null
        cells = null
        orderedPads = null
    }

    private class Cell(val xIndex: Int, val zIndex: Int) {
        val pads = mutableSetOf<TpPad>()
    }

    private class TpPad(
        val pos: BlockPos,
        var cellX: Int = 0,
        var cellZ: Int = 0,
        var totalAngle: Double = 0.0,
        var blacklisted: Boolean = false
    )
}
