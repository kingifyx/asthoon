package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.level.block.Blocks
import java.util.regex.Pattern

/**
 * Higher/Lower dungeon puzzle ("Blaze Puzzle"): automatically detects whether
 * the current room is Higher Blaze or Lower Blaze and highlights the blazes in
 * the correct order.
 *
 * Same detection technique as NoammAddons' BlazeSolver — each blaze has an
 * armor-stand nametag directly above it showing its HP
 * ("[Lv15✯] Blaze 1,234/5,000❤"); scan those every tick, find the actual
 * Blaze entity just below each stand, and sort by max HP.
 *
 * Rendering differs from noamm's version on purpose, per what was asked
 * for here: instead of a thin glowing outline (which needs vanilla's
 * private per-entity glow-color hook — see WeirdosSolver's note on why
 * that wasn't added), this fills each of the next three blazes' hitboxes
 * almost solid so the blaze itself is barely visible under the color:
 * green = click this one now, white = next, red = third. As the correct
 * blaze dies the whole order shifts up automatically (index 0 is always
 * "next correct click").
 *
 * Room detection uses the same compact room-core hashes used by RSM for its
 * Higher Blaze / Lower Blaze room definitions, so there is no manual reverse
 * toggle anymore.
 */
object HigherLowerSolver {

    private val blazeHpPattern = Pattern.compile("Blaze [\\d,]+/([\\d,]+)❤")

    private const val GREEN_R = 0x00 / 255f; private const val GREEN_G = 0xC8 / 255f; private const val GREEN_B = 0x53 / 255f
    private const val WHITE_R = 1f;           private const val WHITE_G = 1f;           private const val WHITE_B = 1f
    private const val RED_R   = 1f;           private const val RED_G   = 0x3D / 255f;  private const val RED_B   = 0x3D / 255f
    private const val FILL_A = 0.85f
    private const val OUTLINE_A = 1f

    // Ordered list of blaze entity ids, rebuilt every tick.
    private var orderedBlazeIds: List<Int> = emptyList()

    private var detectedRoomCenter: Pair<Int, Int>? = null
    private var detectedLowerBlaze = false
    private var roomDetectionKnown = false
    private var roomDetectionRetry = 0

    // RSM room-core hashes for the two Higher/Lower Blaze variants.
    private val higherBlazeCores = setOf(-1752027278, -1477567187)
    private val lowerBlazeCores = setOf(-441650545, 911747293)
    private const val DUNGEON_START = -185

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { queueBoxes() }
    }

    private fun tick() {
        if (!Config.higherLowerSolverEnabled) {
            orderedBlazeIds = emptyList()
            return
        }
        val level = Minecraft.getInstance().level
        if (level == null) {
            orderedBlazeIds = emptyList()
            return
        }

        detectRoomOrder(level)

        val hpByBlazeId = LinkedHashMap<Int, Long>()

        for (stand in level.entitiesForRendering().filterIsInstance<ArmorStand>()) {
            val name = ChatFormatting.stripFormatting(stand.name.string) ?: continue
            val m = blazeHpPattern.matcher(name)
            if (!m.find()) continue
            val maxHp = m.group(1).replace(",", "").toLongOrNull() ?: continue

            val blaze = level.getEntitiesOfClass(
                Blaze::class.java,
                stand.boundingBox.expandTowards(0.0, -2.0, 0.0)
            ).firstOrNull() ?: continue

            if (blaze.isRemoved || blaze.isDeadOrDying) continue
            hpByBlazeId[blaze.id] = maxHp
        }

        val sorted = hpByBlazeId.entries.sortedBy { it.value }.map { it.key }
        orderedBlazeIds = if (detectedLowerBlaze) sorted.reversed() else sorted
    }

    private fun queueBoxes() {
        if (!Config.higherLowerSolverEnabled) return
        if (orderedBlazeIds.isEmpty()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player

        val targets = orderedBlazeIds.take(3).mapNotNull { id ->
            val entity = level.getEntity(id)
            if (entity != null && !entity.isRemoved && (entity !is net.minecraft.world.entity.LivingEntity || !entity.isDeadOrDying)) entity else null
        }
        if (targets.isEmpty()) return

        targets.forEachIndexed { index, entity ->
            // Make hitboxes around the blazes more obvious (inflate by 0.18)
            val box = entity.boundingBox.inflate(0.18)
            val (r, g, b) = when (index) {
                0 -> Triple(GREEN_R, GREEN_G, GREEN_B)
                1 -> Triple(WHITE_R, WHITE_G, WHITE_B)
                else -> Triple(RED_R, RED_G, RED_B)
            }
            WorldBoxRenderer.queueFilled(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, 0.55f, throughWalls = true)
            WorldBoxRenderer.queueOutline(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, 0.95f, thickness = 0.035, throughWalls = true)
        }

        // Tracers pointing to the right ones:
        // 1. Player eye to target 0 (current target) in green
        if (player != null) {
            val eye = player.eyePosition
            val t0 = targets[0].position().add(0.0, targets[0].bbHeight / 2.0, 0.0)
            WorldBoxRenderer.queueLine(
                eye.x, eye.y - 0.2, eye.z,
                t0.x, t0.y, t0.z,
                GREEN_R, GREEN_G, GREEN_B, 0.85f,
                thickness = 0.04,
                throughWalls = true
            )
        }

        // 2. Target 0 to target 1 in white
        if (targets.size > 1) {
            val t0 = targets[0].position().add(0.0, targets[0].bbHeight / 2.0, 0.0)
            val t1 = targets[1].position().add(0.0, targets[1].bbHeight / 2.0, 0.0)
            WorldBoxRenderer.queueLine(
                t0.x, t0.y, t0.z,
                t1.x, t1.y, t1.z,
                WHITE_R, WHITE_G, WHITE_B, 0.85f,
                thickness = 0.035,
                throughWalls = true
            )
        }

        // 3. Target 1 to target 2 in red
        if (targets.size > 2) {
            val t1 = targets[1].position().add(0.0, targets[1].bbHeight / 2.0, 0.0)
            val t2 = targets[2].position().add(0.0, targets[2].bbHeight / 2.0, 0.0)
            WorldBoxRenderer.queueLine(
                t1.x, t1.y, t1.z,
                t2.x, t2.y, t2.z,
                RED_R, RED_G, RED_B, 0.85f,
                thickness = 0.035,
                throughWalls = true
            )
        }
    }

    private fun detectRoomOrder(level: net.minecraft.client.multiplayer.ClientLevel) {
        val player = Minecraft.getInstance().player ?: return
        val centerX = Math.round((player.x.toFloat() - DUNGEON_START) / 32f) * 32 + DUNGEON_START
        val centerZ = Math.round((player.z.toFloat() - DUNGEON_START) / 32f) * 32 + DUNGEON_START
        val center = centerX to centerZ

        if (center != detectedRoomCenter) {
            detectedRoomCenter = center
            roomDetectionKnown = false
            roomDetectionRetry = 0
        }

        if (roomDetectionKnown) return
        if (roomDetectionRetry++ % 10 != 0) return

        val core = roomCore(level, centerX, centerZ) ?: return
        when {
            core in lowerBlazeCores -> {
                detectedLowerBlaze = true
                roomDetectionKnown = true
            }
            core in higherBlazeCores -> {
                detectedLowerBlaze = false
                roomDetectionKnown = true
            }
        }
    }

    /** Same room-core hash algorithm used by RSM's ScanUtils#getCore. */
    private fun roomCore(level: net.minecraft.client.multiplayer.ClientLevel, x: Int, z: Int): Int? {
        val pos = BlockPos(x, 67, z)
        if (!level.isLoaded(pos)) return null
        val chunk = level.getChunk(x shr 4, z shr 4)

        var roofHeight = -1
        val mutable = BlockPos.MutableBlockPos(x, 67, z)
        for (y in 160 downTo 12) {
            mutable.set(x, y, z)
            val block = chunk.getBlockState(mutable).block
            if (block != Blocks.AIR) {
                roofHeight = if (block == Blocks.GOLD_BLOCK) y - 1 else y
                break
            }
        }
        if (roofHeight < 0) return null

        val clampedHeight = roofHeight.coerceIn(11, 140)
        val sb = StringBuilder(150)
        repeat(140 - clampedHeight) { sb.append('0') }

        var bedrock = 0
        for (y in clampedHeight downTo 12) {
            mutable.set(x, y, z)
            val block = chunk.getBlockState(mutable).block
            if (block == Blocks.AIR && bedrock >= 2 && y < 69) {
                repeat(y - 11) { sb.append('0') }
                break
            }

            if (block == Blocks.BEDROCK) {
                bedrock++
            } else {
                bedrock = 0
                if (block == Blocks.OAK_PLANKS || block == Blocks.TRAPPED_CHEST || block == Blocks.CHEST) {
                    continue
                }
            }
            sb.append(block)
        }
        return sb.toString().hashCode()
    }

    fun resetRun() {
        orderedBlazeIds = emptyList()
        detectedRoomCenter = null
        detectedLowerBlaze = false
        roomDetectionKnown = false
        roomDetectionRetry = 0
    }
}
