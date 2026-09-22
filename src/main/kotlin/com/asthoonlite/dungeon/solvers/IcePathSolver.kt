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
import net.minecraft.core.Direction
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

object IcePathSolver {

    private data class PathSegment(val start: Vec3, val end: Vec3)
    private data class GridPos(val x: Int, val z: Int)

    private var inPath = false
    private var roomCenter: BlockPos? = null
    private var roomRotation: Int = 0
    private var grid: Array<IntArray>? = null

    private val currentSolution = ConcurrentLinkedQueue<PathSegment>()
    private var silverfish: Silverfish? = null
    private var lastGridPos: GridPos? = null

    private const val GRID_SIZE = 19
    private const val MIN_OFFSET = -8

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { queueLines() }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    private fun tick() {
        if (!Config.icePathSolverEnabled || !DungeonContext.inDungeon) {
            if (inPath) reset()
            return
        }

        val room = DungeonScanner.currentRoom
        if (room == null || room.name != "Ice Path") {
            if (inPath) reset()
            return
        }

        if (!inPath) {
            inPath = true
            roomCenter = PuzzleUtils.getRoomCenter(room)
            roomRotation = if (room.rotation >= 0) (360 - room.rotation) % 360 else 0
            grid = roomCenter?.let { buildGrid(it) }
        }

        val center = roomCenter ?: return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        var fish = silverfish

        if (fish == null || fish.isRemoved) {
            val aabb = AABB(
                center.x - 10.0, 67.0, center.z - 10.0,
                center.x + 10.0, 68.0, center.z + 10.0
            )
            silverfish = level.getEntitiesOfClass(Silverfish::class.java, aabb).firstOrNull() ?: return
            fish = silverfish
        }

        val fishGridPos = fish?.let { worldToGrid(it.blockPosition(), center) } ?: return
        if (fishGridPos == lastGridPos) return
        lastGridPos = fishGridPos

        if (currentSolution.isNotEmpty()) {
            val currentSegment = currentSolution.peek()
            val dx = abs(fish.x - currentSegment.end.x)
            val dz = abs(fish.z - currentSegment.end.z)
            if (dx + dz <= 0.8) {
                currentSolution.poll()
                return
            }
            if (isOnSegment(fish.x, fish.z, currentSegment)) return
        }

        recalculatePath(fishGridPos, center)
    }

    private fun queueLines() {
        if (!inPath || currentSolution.isEmpty() || silverfish?.isRemoved == true) return

        currentSolution.forEachIndexed { index, segment ->
            val (r, g, b) = if (index == 0) Triple(0f, 1f, 0f) else Triple(1f, 0.2f, 0.2f)
            WorldBoxRenderer.queueLine(
                segment.start.x, segment.start.y, segment.start.z,
                segment.end.x, segment.end.y, segment.end.z,
                r, g, b, 0.95f,
                thickness = 0.08,
                throughWalls = true
            )
        }
    }

    private fun isOnSegment(x: Double, z: Double, seg: PathSegment): Boolean {
        val padding = 0.8
        val minX = minOf(seg.start.x, seg.end.x) - padding
        val maxX = maxOf(seg.start.x, seg.end.x) + padding
        val minZ = minOf(seg.start.z, seg.end.z) - padding
        val maxZ = maxOf(seg.start.z, seg.end.z) + padding
        return x in minX..maxX && z in minZ..maxZ
    }

    private fun recalculatePath(fishPos: GridPos, center: BlockPos) {
        val currentGrid = grid ?: buildGrid(center).also { grid = it }
        val path = solveBFS(currentGrid, fishPos).takeIf { it.size >= 2 } ?: return

        currentSolution.clear()
        for (i in 1 until path.size) {
            val pos1 = gridToWorld(path[i - 1], center)
            val pos2 = gridToWorld(path[i], center)
            currentSolution.add(PathSegment(pos1, pos2))
        }
    }

    private fun buildGrid(center: BlockPos): Array<IntArray> {
        val level = Minecraft.getInstance().level ?: return Array(GRID_SIZE) { IntArray(GRID_SIZE) }
        val newGrid = Array(GRID_SIZE) { IntArray(GRID_SIZE) }

        for (z in 0 until GRID_SIZE) for (x in 0 until GRID_SIZE) {
            val relPos = BlockPos(MIN_OFFSET + x, 67, MIN_OFFSET + z)
            val worldPos = PuzzleUtils.getRealCoord(relPos, center, roomRotation)
            newGrid[z][x] = if (level.getBlockState(worldPos).isAir) 0 else 1
        }
        return newGrid
    }

    private fun worldToGrid(pos: BlockPos, center: BlockPos): GridPos? {
        val dx = pos.x - center.x
        val dz = pos.z - center.z

        val rotated = PuzzleUtils.rotate(BlockPos(dx, 0, dz), -roomRotation)
        val gridX = rotated.x - MIN_OFFSET
        val gridZ = rotated.z - MIN_OFFSET

        if (gridX !in 0 until GRID_SIZE || gridZ !in 0 until GRID_SIZE) return null
        return GridPos(gridX, gridZ)
    }

    private fun gridToWorld(point: GridPos, center: BlockPos): Vec3 {
        val relPos = BlockPos(MIN_OFFSET + point.x, 66, MIN_OFFSET + point.z)
        val worldPos = PuzzleUtils.getRealCoord(relPos, center, roomRotation)
        return Vec3(worldPos.x + 0.5, 67.5, worldPos.z + 0.5)
    }

    private fun solveBFS(grid: Array<IntArray>, start: GridPos): List<GridPos> {
        val queue = ArrayDeque<GridPos>()
        val parentMap = Array(GRID_SIZE) { arrayOfNulls<GridPos>(GRID_SIZE) }

        queue.addLast(start)
        parentMap[start.z][start.x] = start

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()

            for (dir in Direction.Plane.HORIZONTAL) {
                val next = slide(grid, parentMap, curr, dir) ?: continue

                queue.addLast(next)
                parentMap[next.z][next.x] = curr

                if (next.x == 7 && next.z == 18) {
                    val path = ArrayDeque<GridPos>()
                    var tmp: GridPos? = next

                    while (tmp != null && tmp != start) {
                        path.addFirst(tmp)
                        tmp = parentMap[tmp.z][tmp.x]
                    }
                    path.addFirst(start)
                    return path.toList()
                }
            }
        }
        return emptyList()
    }

    private fun slide(grid: Array<IntArray>, parent: Array<Array<GridPos?>>, from: GridPos, dir: Direction): GridPos? {
        val dx = dir.stepX
        val dz = dir.stepZ
        var steps = 1

        while (true) {
            val nx = from.x + steps * dx
            val nz = from.z + steps * dz

            if (nx !in 0 until GRID_SIZE || nz !in 0 until GRID_SIZE || grid[nz][nx] == 1) break
            steps++
        }

        steps--
        if (steps == 0) return null

        val finalX = from.x + steps * dx
        val finalZ = from.z + steps * dz

        return if (parent[finalZ][finalX] != null) null else GridPos(finalX, finalZ)
    }

    fun reset() {
        inPath = false
        roomCenter = null
        grid = null
        silverfish = null
        lastGridPos = null
        currentSolution.clear()
    }
}
