package com.asthoonlite.dungeon.solvers

import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.DungeonContext
import com.asthoonlite.dungeon.map.DungeonScanner
import com.asthoonlite.render.WorldBoxRenderer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.WallSignBlock
import java.util.concurrent.CopyOnWriteArrayList

object BoulderSolver {

    private data class BoulderBox(val box: BlockPos, val click: BlockPos)

    private val boulderSolutions: Map<String, List<List<Int>>> by lazy {
        runCatching {
            BoulderSolver::class.java.getResourceAsStream("/assets/asthoonlite/dungeons/puzzles/boulderSolutions.json")
                ?.bufferedReader()
                ?.use { it.readText() }?.let { json ->
                    val type = object : TypeToken<Map<String, List<List<Int>>>>() {}.type
                    Gson().fromJson<Map<String, List<List<Int>>>>(json, type)
                }
        }.getOrNull() ?: emptyMap()
    }

    private val currentSolution = CopyOnWriteArrayList<BoulderBox>()
    private var inBoulder = false
    private var roomCenter = BlockPos.ZERO
    private var rotation = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { queueRender() }
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (inBoulder && Config.boulderSolverEnabled && DungeonContext.inDungeon) {
                val pos = hitResult.blockPos
                val block = world.getBlockState(pos).block
                if (block is ButtonBlock || block is LeverBlock || block is WallSignBlock) {
                    val entry = currentSolution.find { it.click == pos }
                    if (entry != null) {
                        currentSolution.remove(entry)
                    }
                } else if (block is ChestBlock) {
                    reset()
                }
            }
            InteractionResult.PASS
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    private fun tick() {
        if (!Config.boulderSolverEnabled || !DungeonContext.inDungeon) {
            if (inBoulder) reset()
            return
        }

        val room = DungeonScanner.currentRoom
        if (room == null || room.name != "Boulder") {
            if (inBoulder) reset()
            return
        }

        if (!inBoulder) {
            inBoulder = true
            rotation = if (room.rotation >= 0) (360 - room.rotation + 180) % 360 else 0
            roomCenter = PuzzleUtils.getRoomCenter(room)
            solve()
        }
    }

    private fun queueRender() {
        if (!inBoulder || currentSolution.isEmpty() || !Config.boulderSolverEnabled || !DungeonContext.inDungeon) return

        val step = currentSolution.firstOrNull() ?: return
        // Click block highlight in red/orange
        WorldBoxRenderer.queueFilled(
            step.click.x.toDouble(), step.click.y.toDouble(), step.click.z.toDouble(),
            step.click.x + 1.0, step.click.y + 1.0, step.click.z + 1.0,
            1f, 0.2f, 0.2f, 0.5f, throughWalls = true
        )
        WorldBoxRenderer.queueOutline(
            step.click.x.toDouble(), step.click.y.toDouble(), step.click.z.toDouble(),
            step.click.x + 1.0, step.click.y + 1.0, step.click.z + 1.0,
            1f, 0.2f, 0.2f, 1f, thickness = 0.03, throughWalls = true
        )

        // 3x3 Boulder box highlight in cyan/blue
        val bx = step.box.x + 0.5
        val by = step.box.y.toDouble()
        val bz = step.box.z + 0.5
        WorldBoxRenderer.queueFilled(
            bx - 1.5, by - 1.0, bz - 1.5,
            bx + 1.5, by + 2.0, bz + 1.5,
            0f, 0.5f, 1f, 0.35f, throughWalls = true
        )
        WorldBoxRenderer.queueOutline(
            bx - 1.5, by - 1.0, bz - 1.5,
            bx + 1.5, by + 2.0, bz + 1.5,
            0f, 0.5f, 1f, 0.9f, thickness = 0.04, throughWalls = true
        )
    }

    private fun solve() {
        val level = Minecraft.getInstance().level ?: return
        val sx = -9
        val sy = 65
        val sz = -9

        val sb = StringBuilder()
        for (z in 0..5) {
            for (x in 0..6) {
                val pos = PuzzleUtils.getRealCoord(BlockPos(sx + x * 3, sy, sz + z * 3), roomCenter, rotation)
                sb.append(if (level.getBlockState(pos).isAir) "0" else "1")
            }
        }

        val pattern = sb.toString()
        val solutions = boulderSolutions[pattern] ?: return

        currentSolution.clear()
        for (sol in solutions) {
            if (sol.size >= 4) {
                val box = PuzzleUtils.getRealCoord(BlockPos(sol[0], sy, sol[1]), roomCenter, rotation)
                val click = PuzzleUtils.getRealCoord(BlockPos(sol[2], sy, sol[3]), roomCenter, rotation)
                currentSolution.add(BoulderBox(box, click))
            }
        }
    }

    fun reset() {
        inBoulder = false
        roomCenter = BlockPos.ZERO
        rotation = 0
        currentSolution.clear()
    }
}
