package com.asthoonlite.dungeon.solvers

import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.DungeonContext
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks

object LividSolver {

    private val lividMap = mapOf(
        Blocks.GREEN_WOOL to "Frog Livid",
        Blocks.PURPLE_WOOL to "Purple Livid",
        Blocks.GRAY_WOOL to "Doctor Livid",
        Blocks.BLUE_WOOL to "Scream Livid",
        Blocks.LIME_WOOL to "Smile Livid",
        Blocks.RED_WOOL to "Hockey Livid",
        Blocks.MAGENTA_WOOL to "Crossed Livid",
        Blocks.YELLOW_WOOL to "Arcade Livid",
        Blocks.WHITE_WOOL to "Vendetta Livid"
    )

    private val ceilingWoolBlock = BlockPos(5, 108, 40)
    private var targetLividName: String? = null
    private var lividEntityId: Int? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { queueRender() }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    private fun tick() {
        if (!Config.lividSolverEnabled || !DungeonContext.inDungeon || !DungeonContext.inBoss || DungeonContext.floor.floorNum != 5) {
            if (targetLividName != null || lividEntityId != null) reset()
            return
        }

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val ceilingBlock = level.getBlockState(ceilingWoolBlock).block
        val target = lividMap[ceilingBlock] ?: return
        targetLividName = target

        val current = lividEntityId?.let { level.getEntity(it) }
        if (current == null || current.isRemoved || (current is Player && current.gameProfile.name != target)) {
            val matching = level.getEntitiesOfClass(Player::class.java, mc.player!!.boundingBox.inflate(128.0))
                .find { it.gameProfile.name == target && !it.isRemoved && !it.isDeadOrDying }
            lividEntityId = matching?.id
        }
    }

    private fun queueRender() {
        if (!Config.lividSolverEnabled || !DungeonContext.inDungeon || !DungeonContext.inBoss) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        val id = lividEntityId ?: return
        val livid = level.getEntity(id) as? Player ?: return
        if (livid.isRemoved || livid.isDeadOrDying) return

        val box = livid.boundingBox
        // Highlight real Livid in bright green
        WorldBoxRenderer.queueFilled(
            box.minX, box.minY, box.minZ,
            box.maxX, box.maxY, box.maxZ,
            0f, 1f, 0f, 0.5f, throughWalls = true
        )
        WorldBoxRenderer.queueOutline(
            box.minX, box.minY, box.minZ,
            box.maxX, box.maxY, box.maxZ,
            0f, 1f, 0f, 1f, thickness = 0.04, throughWalls = true
        )

        // Line / tracer from player to real Livid
        WorldBoxRenderer.queueLine(
            player.x, player.y + 1.2, player.z,
            livid.x, livid.y + 1.0, livid.z,
            0f, 1f, 0f, 0.8f,
            thickness = 0.05,
            throughWalls = true
        )
    }

    fun reset() {
        targetLividName = null
        lividEntityId = null
    }
}
