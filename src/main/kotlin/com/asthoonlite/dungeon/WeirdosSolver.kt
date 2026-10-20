package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.map.DungeonScanner
import com.asthoonlite.dungeon.solvers.PuzzleUtils
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.ChestBlock
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.floor

/**
 * Three Weirdos dungeon puzzle solver matching NoammAddons' ThreeWeirdosSolver.
 * Identifies the true statement from NPC dialogue lines, computes the chest position,
 * highlights the correct chest in green (with tracer) and wrong chests in red,
 * plays a notification pling, and auto-resets on chest click or room clear.
 */
object WeirdosSolver {

    private val npcRegex = Regex("\\[NPC] (\\w+): (.+)")
    private val wrongPositions = CopyOnWriteArraySet<BlockPos>()
    private var correctPos: BlockPos? = null

    private val solutions = listOf(
        Regex("The reward is not in my chest!"),
        Regex("At least one of them is lying, and the reward is not in .+'s chest.?"),
        Regex("My chest doesn't have the reward. We are all telling the truth.?"),
        Regex("My chest has the reward and I'm telling the truth!"),
        Regex("The reward isn't in any of our chests.?"),
        Regex("Both of them are telling the truth. Also, .+ has the reward in their chest.?"),
    )

    private val wrong = listOf(
        Regex("One of us is telling the truth!"),
        Regex("They are both telling the truth. The reward isn't in .+'s chest."),
        Regex("We are all telling the truth!"),
        Regex(".+ is telling the truth and the reward is in his chest."),
        Regex("My chest doesn't have the reward. At least one of the others is telling the truth!"),
        Regex("One of the others is lying."),
        Regex("They are both telling the truth, the reward is in .+'s chest."),
        Regex("They are both lying, the reward is in my chest!"),
        Regex("The reward is in my chest."),
        Regex("The reward is not in my chest. They are both lying."),
        Regex(".+ is telling the truth."),
        Regex("My chest has the reward.")
    )

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay) onChat(text)
            true
        }
        LevelRenderEvents.END_EXTRACTION.register { queueRender() }
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (Config.weirdosSolverEnabled && (correctPos != null || wrongPositions.isNotEmpty())) {
                if (hitResult.blockPos == correctPos) {
                    reset()
                }
            }
            InteractionResult.PASS
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    private fun onChat(component: Component) {
        if (!Config.weirdosSolverEnabled || !DungeonContext.inDungeon) return
        val currentRoom = DungeonScanner.currentRoom
        if (currentRoom != null && currentRoom.name != "Three Weirdos") return

        val raw = ChatFormatting.stripFormatting(component.string) ?: return
        val match = npcRegex.find(raw) ?: return
        val npcName = match.groupValues[1]
        val text = match.groupValues[2]

        val isSolution = solutions.any { it.matches(text) }
        val isWrong = wrong.any { it.matches(text) }
        if (!isSolution && !isWrong) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val correctNPC = level.entitiesForRendering()
            .filterIsInstance<ArmorStand>()
            .find { it.name.string.contains(npcName) } ?: return

        val npcBlockPos = BlockPos(floor(correctNPC.x).toInt(), 69, floor(correctNPC.z).toInt())
        val rot = if (currentRoom != null && currentRoom.rotation >= 0) (360 - currentRoom.rotation) % 360 else 0
        var chestPos = npcBlockPos.offset(PuzzleUtils.rotate(BlockPos(1, 0, 0), rot))

        // If block at chestPos is not a chest, locate the chest nearest to this NPC at Y=69
        if (level.getBlockState(chestPos).block !is ChestBlock) {
            val nearbyChests = listOf(
                BlockPos(1, 0, 0), BlockPos(-1, 0, 0), BlockPos(0, 0, 1), BlockPos(0, 0, -1),
                BlockPos(2, 0, 0), BlockPos(-2, 0, 0), BlockPos(0, 0, 2), BlockPos(0, 0, -2)
            ).map { npcBlockPos.offset(it) }
            val found = nearbyChests.firstOrNull { level.getBlockState(it).block is ChestBlock }
            if (found != null) chestPos = found
        }

        if (isSolution) {
            mc.player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 2f, 1f)
            correctPos = chestPos
            mc.player?.sendSystemMessage(
                Component.literal("§a[AsthoonLite] §fCorrect Weirdo: §b$npcName")
            )
        } else {
            wrongPositions.add(chestPos)
        }
    }

    private fun queueRender() {
        if (!Config.weirdosSolverEnabled || !DungeonContext.inDungeon) return
        val correct = correctPos
        val player = Minecraft.getInstance().player

        if (correct != null) {
            // Correct chest: bright green highlight and tracer (matching Noamm)
            WorldBoxRenderer.queueFilled(
                correct.x.toDouble(), correct.y.toDouble(), correct.z.toDouble(),
                correct.x + 1.0, correct.y + 1.0, correct.z + 1.0,
                0f, 1f, 0f, 0.45f,
                throughWalls = true
            )
            WorldBoxRenderer.queueOutline(
                correct.x.toDouble(), correct.y.toDouble(), correct.z.toDouble(),
                correct.x + 1.0, correct.y + 1.0, correct.z + 1.0,
                0f, 1f, 0f, 1f,
                thickness = 0.035,
                throughWalls = true
            )

            // Tracer pointing to the correct chest
            if (player != null) {
                val eye = player.eyePosition
                WorldBoxRenderer.queueLine(
                    eye.x, eye.y - 0.2, eye.z,
                    correct.x + 0.5, correct.y + 0.5, correct.z + 0.5,
                    0f, 1f, 0f, 0.85f,
                    thickness = 0.04,
                    throughWalls = true
                )
            }
        }

        // Wrong chests: red outline and fill (matching Noamm)
        for (wrongPos in wrongPositions) {
            if (wrongPos == correct) continue
            WorldBoxRenderer.queueFilled(
                wrongPos.x.toDouble(), wrongPos.y.toDouble(), wrongPos.z.toDouble(),
                wrongPos.x + 1.0, wrongPos.y + 1.0, wrongPos.z + 1.0,
                1f, 0.2f, 0.2f, 0.35f,
                throughWalls = true
            )
            WorldBoxRenderer.queueOutline(
                wrongPos.x.toDouble(), wrongPos.y.toDouble(), wrongPos.z.toDouble(),
                wrongPos.x + 1.0, wrongPos.y + 1.0, wrongPos.z + 1.0,
                1f, 0.2f, 0.2f, 0.9f,
                thickness = 0.03,
                throughWalls = true
            )
        }
    }

    fun reset() {
        correctPos = null
        wrongPositions.clear()
    }

    fun resetRun() {
        reset()
    }
}
