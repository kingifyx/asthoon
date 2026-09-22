package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

/**
 * Full-tile lever/button secret hitboxes.
 *
 * The important distinction from the old implementation is that the enlarged
 * hitbox is a 2D surface on the block face the control is attached to. It does
 * not become a one-block-deep cube, so aiming slightly beside a button on a
 * wall can select the button without making the whole block volumetrically
 * clickable.
 */
object SecretHitboxes {
    private val pressedUntil = HashMap<BlockPos, Long>()
    private val previousPowered = HashMap<BlockPos, Boolean>()

    fun markPressed(pos: BlockPos) {
        if (Config.pressedHitboxEnabled) pressedUntil[pos] = System.currentTimeMillis() + Config.pressedHitboxDuration
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { render() }
    }

    private fun tick() {
        if (!DungeonContext.inDungeon) {
            pressedUntil.clear(); previousPowered.clear(); return
        }
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        val radius = 8
        val live = HashSet<BlockPos>()
        for (x in (player.x - radius).toInt()..(player.x + radius).toInt())
            for (y in (player.y - radius).toInt()..(player.y + radius).toInt())
                for (z in (player.z - radius).toInt()..(player.z + radius).toInt()) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)
                    if (!isTracked(state.block)) continue
                    live += pos
                    val powered = powered(state)
                    val old = previousPowered.put(pos, powered)
                    if (Config.pressedHitboxEnabled && old == false && powered) {
                        pressedUntil[pos] = System.currentTimeMillis() + Config.pressedHitboxDuration
                    }
                }
        previousPowered.keys.removeIf { it !in live }
        pressedUntil.entries.removeIf { it.value < System.currentTimeMillis() }

    }

    /**
     * Noamm-style interaction shape.  The important part is that this is the
     * block shape used by Minecraft's normal raycast, rather than replacing
     * Minecraft.hitResult after the raycast has already happened.
     */
    @JvmStatic
    fun getButtonRelativeBounds(state: net.minecraft.world.level.block.state.BlockState, sizePercent: Int): DoubleArray {
        val size = (sizePercent / 100.0).coerceIn(0.1, 1.0)
        val half = size / 2.0
        val face = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE)
        val dir = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING)
        val powered = state.getValue(ButtonBlock.POWERED)
        val f2 = (if (powered) 1 else 2) / 16.0

        return when (face) {
            net.minecraft.world.level.block.state.properties.AttachFace.FLOOR -> doubleArrayOf(
                0.5 - half, 0.0, 0.5 - half,
                0.5 + half, f2, 0.5 + half
            )
            net.minecraft.world.level.block.state.properties.AttachFace.CEILING -> doubleArrayOf(
                0.5 - half, 1.0 - f2, 0.5 - half,
                0.5 + half, 1.0, 0.5 + half
            )
            else -> when (dir) {
                net.minecraft.core.Direction.EAST -> doubleArrayOf(
                    0.0, 0.5 - half, 0.5 - half,
                    f2, 0.5 + half, 0.5 + half
                )
                net.minecraft.core.Direction.WEST -> doubleArrayOf(
                    1.0 - f2, 0.5 - half, 0.5 - half,
                    1.0, 0.5 + half, 0.5 + half
                )
                net.minecraft.core.Direction.SOUTH -> doubleArrayOf(
                    0.5 - half, 0.5 - half, 0.0,
                    0.5 + half, 0.5 + half, f2
                )
                net.minecraft.core.Direction.NORTH -> doubleArrayOf(
                    0.5 - half, 0.5 - half, 1.0 - f2,
                    0.5 + half, 0.5 + half, 1.0
                )
                else -> doubleArrayOf(
                    0.5 - half, 0.0, 0.5 - half,
                    0.5 + half, f2, 0.5 + half
                )
            }
        }
    }

    @JvmStatic
    fun buttonShape(state: net.minecraft.world.level.block.state.BlockState): net.minecraft.world.phys.shapes.VoxelShape {
        val b = getButtonRelativeBounds(state, Config.secretHitboxSize)
        return net.minecraft.world.phys.shapes.Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5])
    }

    /** Full interaction area of the attached wall/floor/ceiling face, with only a
     * tiny depth so the hitbox never extrudes through the backing block. */
    @JvmStatic
    fun getAttachedFaceShape(state: net.minecraft.world.level.block.state.BlockState): net.minecraft.world.phys.shapes.VoxelShape {
        val face = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE)
        val dir = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING)
        val d = 0.002
        return when (face) {
            net.minecraft.world.level.block.state.properties.AttachFace.FLOOR -> net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, d, 1.0)
            net.minecraft.world.level.block.state.properties.AttachFace.CEILING -> net.minecraft.world.phys.shapes.Shapes.box(0.0, 1.0-d, 0.0, 1.0, 1.0, 1.0)
            else -> when (dir) {
                net.minecraft.core.Direction.EAST -> net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, d, 1.0, 1.0)
                net.minecraft.core.Direction.WEST -> net.minecraft.world.phys.shapes.Shapes.box(1.0-d, 0.0, 0.0, 1.0, 1.0, 1.0)
                net.minecraft.core.Direction.SOUTH -> net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, d)
                net.minecraft.core.Direction.NORTH -> net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 1.0-d, 1.0, 1.0, 1.0)
                else -> net.minecraft.world.phys.shapes.Shapes.block()
            }
        }
    }

    private val blackListedLevers = setOf(
        BlockPos(61, 136, 142), BlockPos(60, 136, 142), BlockPos(59, 136, 142),
        BlockPos(62, 135, 142), BlockPos(61, 135, 142), BlockPos(59, 135, 142),
        BlockPos(58, 135, 142), BlockPos(62, 134, 142), BlockPos(61, 134, 142),
        BlockPos(59, 134, 142), BlockPos(58, 134, 142), BlockPos(61, 133, 142),
        BlockPos(60, 133, 142), BlockPos(59, 133, 142)
    )

    @JvmStatic
    fun isValidLever(pos: BlockPos): Boolean =
        DungeonContext.inDungeon && Config.secretHitboxesEnabled && Config.leverHitboxEnabled && pos !in blackListedLevers

    @JvmStatic
    fun isButtonHitboxEnabled(): Boolean =
        DungeonContext.inDungeon && Config.secretHitboxesEnabled && Config.buttonHitboxEnabled

    @JvmStatic
    fun isSkullHitboxEnabled(): Boolean =
        DungeonContext.inDungeon && Config.secretHitboxesEnabled && Config.skullHitboxEnabled

    @JvmStatic
    fun isMushroomHitboxEnabled(): Boolean =
        DungeonContext.inDungeon && Config.secretHitboxesEnabled && Config.mushroomHitboxEnabled

    @JvmStatic
    fun isLeverHitboxEnabled(pos: BlockPos): Boolean = isValidLever(pos)

    @JvmStatic
    fun shouldOverrideOutline(state: net.minecraft.world.level.block.state.BlockState, pos: BlockPos): Boolean {
        if (!DungeonContext.inDungeon || !Config.secretHitboxesEnabled) return false
        if (state.block is LeverBlock && isValidLever(pos)) return true
        if (state.block is ButtonBlock && isButtonHitboxEnabled()) return true
        return false
    }

    @JvmStatic
    fun getLeverRelativeBounds(state: net.minecraft.world.level.block.state.BlockState, sizePercent: Int): DoubleArray {
        val size = (sizePercent / 100.0).coerceIn(0.1, 1.0)
        val half = size / 2.0
        val face = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE)
        val dir = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING)

        return when (face) {
            net.minecraft.world.level.block.state.properties.AttachFace.FLOOR -> {
                doubleArrayOf(
                    0.5 - half, 0.0, 0.5 - half,
                    0.5 + half, size, 0.5 + half
                )
            }
            net.minecraft.world.level.block.state.properties.AttachFace.CEILING -> {
                doubleArrayOf(
                    0.5 - half, 1.0 - size, 0.5 - half,
                    0.5 + half, 1.0, 0.5 + half
                )
            }
            else -> {
                when (dir) {
                    net.minecraft.core.Direction.NORTH -> doubleArrayOf(
                        0.5 - half, 0.5 - half, 1.0 - size,
                        0.5 + half, 0.5 + half, 1.0
                    )
                    net.minecraft.core.Direction.SOUTH -> doubleArrayOf(
                        0.5 - half, 0.5 - half, 0.0,
                        0.5 + half, 0.5 + half, size
                    )
                    net.minecraft.core.Direction.WEST -> doubleArrayOf(
                        1.0 - size, 0.5 - half, 0.5 - half,
                        1.0, 0.5 + half, 0.5 + half
                    )
                    net.minecraft.core.Direction.EAST -> doubleArrayOf(
                        0.0, 0.5 - half, 0.5 - half,
                        size, 0.5 + half, 0.5 + half
                    )
                    else -> doubleArrayOf(0.5 - half, 0.0, 0.5 - half, 0.5 + half, size, 0.5 + half)
                }
            }
        }
    }

    @JvmStatic
    fun getLeverShape(state: net.minecraft.world.level.block.state.BlockState, pos: BlockPos): net.minecraft.world.phys.shapes.VoxelShape {
        if (!isValidLever(pos)) return vanillaShape(state)
        val b = getLeverRelativeBounds(state, Config.secretHitboxSize)
        return net.minecraft.world.phys.shapes.Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5])
    }

    @JvmStatic
    fun getOutlineShape(
        state: net.minecraft.world.level.block.state.BlockState,
        pos: BlockPos,
        original: net.minecraft.world.phys.shapes.VoxelShape
    ): net.minecraft.world.phys.shapes.VoxelShape {
        if (Config.secretHitboxHideOutline) {
            return net.minecraft.world.phys.shapes.Shapes.empty()
        }
        if (Config.secretHitboxVanillaOutline) {
            return vanillaShape(state)
        }
        if (state.block is LeverBlock && isValidLever(pos)) {
            return getLeverShape(state, pos)
        }
        if (state.block is ButtonBlock && isButtonHitboxEnabled()) {
            return buttonShape(state)
        }
        return original
    }

    private data class SurfaceTarget(val pos: BlockPos, val point: Vec3, val face: net.minecraft.core.Direction, val distanceSq: Double)
    private data class PlaneHit(val point: Vec3, val face: net.minecraft.core.Direction)
    private data class Surface(val minX: Double, val minY: Double, val minZ: Double, val maxX: Double, val maxY: Double, val maxZ: Double, val face: net.minecraft.core.Direction)

    private fun intersectRayPlane(origin: Vec3, dir: Vec3, s: Surface): PlaneHit? {
        val axis: Int
        val plane: Double
        when (s.face) {
            net.minecraft.core.Direction.WEST -> { axis = 0; plane = s.minX }
            net.minecraft.core.Direction.EAST -> { axis = 0; plane = s.maxX }
            net.minecraft.core.Direction.DOWN -> { axis = 1; plane = s.minY }
            net.minecraft.core.Direction.UP -> { axis = 1; plane = s.maxY }
            net.minecraft.core.Direction.NORTH -> { axis = 2; plane = s.minZ }
            net.minecraft.core.Direction.SOUTH -> { axis = 2; plane = s.maxZ }
        }
        val d = when (axis) { 0 -> dir.x; 1 -> dir.y; else -> dir.z }
        if (kotlin.math.abs(d) < 1.0e-7) return null
        val o = when (axis) { 0 -> origin.x; 1 -> origin.y; else -> origin.z }
        val t = (plane - o) / d
        if (t < 0.0) return null
        val p = origin.add(dir.scale(t))
        if (p.x < s.minX || p.x > s.maxX || p.y < s.minY || p.y > s.maxY || p.z < s.minZ || p.z > s.maxZ) return null
        return PlaneHit(p, s.face)
    }

    private fun surfaceFor(pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState, size: Double): Surface? {
        val sx = size.coerceIn(0.1, 1.0)
        val pad = (1.0 - sx) / 2.0
        val face = when {
            state.hasProperty(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE) &&
                state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE) == net.minecraft.world.level.block.state.properties.AttachFace.FLOOR -> net.minecraft.core.Direction.DOWN
            state.hasProperty(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE) &&
                state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE) == net.minecraft.world.level.block.state.properties.AttachFace.CEILING -> net.minecraft.core.Direction.UP
            state.hasProperty(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE) -> state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING).opposite
            else -> return null
        }
        val minX = pos.x + pad; val maxX = pos.x + 1.0 - pad
        val minY = pos.y + pad; val maxY = pos.y + 1.0 - pad
        val minZ = pos.z + pad; val maxZ = pos.z + 1.0 - pad
        return when (face) {
            net.minecraft.core.Direction.WEST -> Surface(pos.x.toDouble(), minY, minZ, pos.x.toDouble() + 0.002, maxY, maxZ, face)
            net.minecraft.core.Direction.EAST -> Surface(pos.x + 0.998, minY, minZ, pos.x + 1.0, maxY, maxZ, face)
            net.minecraft.core.Direction.DOWN -> Surface(minX, pos.y.toDouble(), minZ, maxX, pos.y.toDouble() + 0.002, maxZ, face)
            net.minecraft.core.Direction.UP -> Surface(minX, pos.y + 0.998, minZ, maxX, pos.y + 1.0, maxZ, face)
            net.minecraft.core.Direction.NORTH -> Surface(minX, minY, pos.z.toDouble(), maxX, maxY, pos.z.toDouble() + 0.002, face)
            net.minecraft.core.Direction.SOUTH -> Surface(minX, minY, pos.z + 0.998, maxX, maxY, pos.z + 1.0, face)
        }
    }

    @JvmStatic
    fun vanillaShape(state: net.minecraft.world.level.block.state.BlockState): net.minecraft.world.phys.shapes.VoxelShape {
        val face = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE)
        val dir = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING)
        val isButton = state.block is ButtonBlock
        if (isButton) {
            val t = if (state.getValue(ButtonBlock.POWERED)) 2.0 / 16.0 else 4.0 / 16.0
            val w0 = 5.0 / 16.0; val w1 = 11.0 / 16.0
            val h0 = 5.0 / 16.0; val h1 = 11.0 / 16.0
            return when (face) {
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR -> net.minecraft.world.phys.shapes.Shapes.box(w0, 0.0, w0, w1, t, w1)
                net.minecraft.world.level.block.state.properties.AttachFace.CEILING -> net.minecraft.world.phys.shapes.Shapes.box(w0, 1.0-t, w0, w1, 1.0, w1)
                else -> when (dir) {
                    net.minecraft.core.Direction.EAST -> net.minecraft.world.phys.shapes.Shapes.box(0.0, h0, w0, t, h1, w1)
                    net.minecraft.core.Direction.WEST -> net.minecraft.world.phys.shapes.Shapes.box(1.0-t, h0, w0, 1.0, h1, w1)
                    net.minecraft.core.Direction.SOUTH -> net.minecraft.world.phys.shapes.Shapes.box(w0, h0, 0.0, w1, h1, t)
                    net.minecraft.core.Direction.NORTH -> net.minecraft.world.phys.shapes.Shapes.box(w0, h0, 1.0-t, w1, h1, 1.0)
                    else -> net.minecraft.world.phys.shapes.Shapes.box(w0, 0.0, w0, w1, t, w1)
                }
            }
        }
        // Vanilla lever is a small centered control rather than the whole tile.
        val w0 = 5.0 / 16.0; val w1 = 11.0 / 16.0
        return when (face) {
            net.minecraft.world.level.block.state.properties.AttachFace.FLOOR -> net.minecraft.world.phys.shapes.Shapes.box(w0, 0.0, w0, w1, 0.5, w1)
            net.minecraft.world.level.block.state.properties.AttachFace.CEILING -> net.minecraft.world.phys.shapes.Shapes.box(w0, 0.5, w0, w1, 1.0, w1)
            else -> when (dir) {
                net.minecraft.core.Direction.EAST -> net.minecraft.world.phys.shapes.Shapes.box(0.0, w0, w0, 0.5, w1, w1)
                net.minecraft.core.Direction.WEST -> net.minecraft.world.phys.shapes.Shapes.box(0.5, w0, w0, 1.0, w1, w1)
                net.minecraft.core.Direction.SOUTH -> net.minecraft.world.phys.shapes.Shapes.box(w0, w0, 0.0, w1, w1, 0.5)
                net.minecraft.core.Direction.NORTH -> net.minecraft.world.phys.shapes.Shapes.box(w0, w0, 0.5, w1, w1, 1.0)
                else -> net.minecraft.world.phys.shapes.Shapes.box(w0, 0.0, w0, w1, 0.5, w1)
            }
        }
    }

    private fun render() {
        if (!Config.secretHitboxesEnabled || !DungeonContext.inDungeon) return
        val mc = Minecraft.getInstance(); val level = mc.level ?: return; val player = mc.player ?: return
        val radius = 8
        if (Config.moddedHitboxDisplayEnabled) {
            val size = (Config.secretHitboxSize / 100.0).coerceIn(0.1, 1.0)
            val pad = (1.0 - size) / 2.0
            for (x in (player.x - radius).toInt()..(player.x + radius).toInt())
                for (y in (player.y - radius).toInt()..(player.y + radius).toInt())
                    for (z in (player.z - radius).toInt()..(player.z + radius).toInt()) {
                        val pos = BlockPos(x, y, z)
                        val state = level.getBlockState(pos)
                        val block = state.block

                        val isLever = block is LeverBlock && isValidLever(pos)
                        val isButton = block is ButtonBlock && isButtonHitboxEnabled()
                        val isSkull = (block == net.minecraft.world.level.block.Blocks.PLAYER_HEAD || block == net.minecraft.world.level.block.Blocks.PLAYER_WALL_HEAD) && isSkullHitboxEnabled()
                        val isMushroom = (block == net.minecraft.world.level.block.Blocks.RED_MUSHROOM || block == net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM) && isMushroomHitboxEnabled()

                        if (!isLever && !isButton && !isSkull && !isMushroom) continue

                        val minX: Double
                        val minY: Double
                        val minZ: Double
                        val maxX: Double
                        val maxY: Double
                        val maxZ: Double

                        if (isLever) {
                            val b = getLeverRelativeBounds(state, Config.secretHitboxSize)
                            minX = pos.x + b[0]
                            minY = pos.y + b[1]
                            minZ = pos.z + b[2]
                            maxX = pos.x + b[3]
                            maxY = pos.y + b[4]
                            maxZ = pos.z + b[5]
                        } else if (isButton) {
                            val b = getButtonRelativeBounds(state, Config.secretHitboxSize)
                            minX = pos.x + b[0]
                            minY = pos.y + b[1]
                            minZ = pos.z + b[2]
                            maxX = pos.x + b[3]
                            maxY = pos.y + b[4]
                            maxZ = pos.z + b[5]
                        } else {
                            // Skull, Mushroom: Full 3D block box (scaled by size)
                            minX = pos.x + pad
                            minY = pos.y + pad
                            minZ = pos.z + pad
                            maxX = pos.x + 1.0 - pad
                            maxY = pos.y + 1.0 - pad
                            maxZ = pos.z + 1.0 - pad
                        }

                        // Render the actual 3D box of the hitbox
                        WorldBoxRenderer.queueOutline(minX, minY, minZ, maxX, maxY, maxZ, 0.12f, 0.70f, 1f, 1f, thickness = 0.025)
                        WorldBoxRenderer.queueFilled(minX, minY, minZ, maxX, maxY, maxZ, 0.12f, 0.70f, 1f, 0.08f)
                    }
        }

        if (!Config.pressedHitboxEnabled) return
        for ((pos, until) in pressedUntil) {
            if (until < System.currentTimeMillis()) continue
            val state = level.getBlockState(pos)
            if (!isTracked(state.block)) continue
            val shape = vanillaShape(state).bounds()
            WorldBoxRenderer.queueOutline(
                pos.x + shape.minX, pos.y + shape.minY, pos.z + shape.minZ,
                pos.x + shape.maxX, pos.y + shape.maxY, pos.z + shape.maxZ,
                1f, 0.82f, 0.1f, 1f
            )
        }
    }

    private fun isTracked(block: net.minecraft.world.level.block.Block) = block is LeverBlock || block is ButtonBlock
    private fun powered(state: net.minecraft.world.level.block.state.BlockState): Boolean = when {
        state.hasProperty(LeverBlock.POWERED) -> state.getValue(LeverBlock.POWERED)
        state.hasProperty(ButtonBlock.POWERED) -> state.getValue(ButtonBlock.POWERED)
        else -> false
    }
}
