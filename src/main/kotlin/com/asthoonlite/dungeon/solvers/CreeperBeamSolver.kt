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
import net.minecraft.world.level.block.Blocks
import java.util.concurrent.CopyOnWriteArrayList

object CreeperBeamSolver {

    private data class BeamPair(
        val start: BlockPos,
        val end: BlockPos,
        val r: Float, val g: Float, val b: Float
    )

    private val rawSolutions = listOf(
        Triple(BlockPos(15, 74, 15), BlockPos(15, 84, 13), Triple(0f, 1f, 1f)),
        Triple(BlockPos(15, 78, 3), BlockPos(15, 76, 27), Triple(0f, 1f, 0f)),
        Triple(BlockPos(22, 80, 22), BlockPos(4, 72, 8), Triple(1f, 0.2f, 0.2f)),
        Triple(BlockPos(24, 77, 7), BlockPos(5, 76, 24), Triple(1f, 0.6f, 0f)),
        Triple(BlockPos(27, 78, 14), BlockPos(2, 75, 16), Triple(1f, 0.2f, 1f)),
        Triple(BlockPos(26, 78, 12), BlockPos(3, 76, 18), Triple(0.2f, 0.5f, 1f)),
        Triple(BlockPos(25, 79, 21), BlockPos(4, 75, 9), Triple(1f, 1f, 0.2f)),
        Triple(BlockPos(25, 76, 23), BlockPos(6, 74, 5), Triple(0.8f, 0.2f, 0.8f)),
        Triple(BlockPos(18, 82, 8), BlockPos(10, 69, 27), Triple(0.2f, 1f, 0.6f)),
        Triple(BlockPos(18, 81, 21), BlockPos(9, 69, 3), Triple(1f, 0.4f, 0.4f)),
        Triple(BlockPos(9, 81, 20), BlockPos(26, 70, 7), Triple(0.6f, 0.6f, 1f))
    )

    private val activePairs = CopyOnWriteArrayList<BeamPair>()
    private var inCreeperBeams = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { queueRender() }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    private fun tick() {
        if (!Config.creeperBeamSolverEnabled || !DungeonContext.inDungeon) {
            if (inCreeperBeams) reset()
            return
        }

        val room = DungeonScanner.currentRoom
        if (room == null || room.name != "Creeper Beams" || !room.hasRotation()) {
            if (inCreeperBeams) reset()
            return
        }

        if (!inCreeperBeams) {
            inCreeperBeams = true
            activePairs.clear()

            for ((p1, p2, color) in rawSolutions) {
                val c1 = room.fromComp(p1.x, p1.z) ?: continue
                val c2 = room.fromComp(p2.x, p2.z) ?: continue
                val pos1 = BlockPos(c1.first, p1.y, c1.second)
                val pos2 = BlockPos(c2.first, p2.y, c2.second)
                activePairs.add(BeamPair(pos1, pos2, color.first, color.second, color.third))
            }
        }
    }

    private fun queueRender() {
        if (!inCreeperBeams || activePairs.isEmpty() || !Config.creeperBeamSolverEnabled || !DungeonContext.inDungeon) return
        val level = Minecraft.getInstance().level ?: return

        for (pair in activePairs) {
            val b1 = level.getBlockState(pair.start).block
            val b2 = level.getBlockState(pair.end).block
            if (b1 != Blocks.SEA_LANTERN || b2 != Blocks.SEA_LANTERN) continue

            // Render block 1 box
            WorldBoxRenderer.queueOutline(
                pair.start.x.toDouble(), pair.start.y.toDouble(), pair.start.z.toDouble(),
                pair.start.x + 1.0, pair.start.y + 1.0, pair.start.z + 1.0,
                pair.r, pair.g, pair.b, 1f, thickness = 0.04, throughWalls = true
            )
            WorldBoxRenderer.queueFilled(
                pair.start.x.toDouble(), pair.start.y.toDouble(), pair.start.z.toDouble(),
                pair.start.x + 1.0, pair.start.y + 1.0, pair.start.z + 1.0,
                pair.r, pair.g, pair.b, 0.4f, throughWalls = true
            )

            // Render block 2 box
            WorldBoxRenderer.queueOutline(
                pair.end.x.toDouble(), pair.end.y.toDouble(), pair.end.z.toDouble(),
                pair.end.x + 1.0, pair.end.y + 1.0, pair.end.z + 1.0,
                pair.r, pair.g, pair.b, 1f, thickness = 0.04, throughWalls = true
            )
            WorldBoxRenderer.queueFilled(
                pair.end.x.toDouble(), pair.end.y.toDouble(), pair.end.z.toDouble(),
                pair.end.x + 1.0, pair.end.y + 1.0, pair.end.z + 1.0,
                pair.r, pair.g, pair.b, 0.4f, throughWalls = true
            )

            // Render connecting beam
            WorldBoxRenderer.queueLine(
                pair.start.x + 0.5, pair.start.y + 0.5, pair.start.z + 0.5,
                pair.end.x + 0.5, pair.end.y + 0.5, pair.end.z + 0.5,
                pair.r, pair.g, pair.b, 0.85f, thickness = 0.06, throughWalls = true
            )
        }
    }

    fun reset() {
        inCreeperBeams = false
        activePairs.clear()
    }
}
