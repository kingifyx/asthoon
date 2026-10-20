package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * Floor 7 Phase 3 Lights / Lamps Device Solver & Automation:
 * Controls the 14 device levers around the light panel at Section 2 (EE2):
 * Coordinates: X=58..62, Y=133..136, Z=142
 * Clicking the levers until all target lamps/lights are illuminated, or automatically
 * cycling levers in sequence with human click pacing.
 */
object LampsDeviceSolver {

    val deviceLevers = listOf(
        BlockPos(61, 136, 142), BlockPos(60, 136, 142),
        BlockPos(59, 136, 142), BlockPos(62, 135, 142),
        BlockPos(61, 135, 142), BlockPos(59, 135, 142),
        BlockPos(58, 135, 142), BlockPos(62, 134, 142),
        BlockPos(61, 134, 142), BlockPos(59, 134, 142),
        BlockPos(58, 134, 142), BlockPos(61, 133, 142),
        BlockPos(60, 133, 142), BlockPos(59, 133, 142)
    )

    private val deviceCenter = Vec3(60.0, 134.5, 142.0)
    private var lastClickTime = 0L
    private var currentLeverIdx = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
    }

    fun isNearDevice(playerPos: Vec3): Boolean =
        playerPos.distanceToSqr(deviceCenter) <= 64.0

    /** Checks whether all redstone lamps near the device panel are lit, or device is completed. */
    fun isSolved(): Boolean {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return false
        val box = net.minecraft.world.phys.AABB(57.0, 132.0, 140.0, 63.0, 137.0, 144.0)

        // Check for "Completed" hologram
        val stands = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand::class.java, box)
        for (stand in stands) {
            val name = stand.customName?.string ?: continue
            if (name.contains("Completed", ignoreCase = true)) return true
        }

        // Check unlit redstone lamps
        var unlitLamps = 0
        var foundLamps = 0
        for (x in 58..62) {
            for (y in 133..136) {
                for (z in 141..143) {
                    val p = BlockPos(x, y, z)
                    val st = level.getBlockState(p)
                    if (st.`is`(Blocks.REDSTONE_LAMP)) {
                        foundLamps++
                        val lit = st.getValue(BlockStateProperties.LIT)
                        if (!lit) unlitLamps++
                    }
                }
            }
        }
        return foundLamps > 0 && unlitLamps == 0
    }

    private fun tick() {
        if (!DungeonContext.inDungeon) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (!Config.autoLampsEnabled) return
        if (!isNearDevice(player.position())) return
        if (isSolved()) return

        val now = System.currentTimeMillis()
        if (now - lastClickTime < 160) return // human cadence (~6 cps)

        val leverPos = deviceLevers[currentLeverIdx % deviceLevers.size]
        currentLeverIdx++

        val state = level.getBlockState(leverPos)
        if (state.`is`(Blocks.LEVER)) {
            val hitVec = Vec3.atCenterOf(leverPos)
            if (player.eyePosition.distanceTo(hitVec) <= 4.5) {
                lastClickTime = now
                val hitResult = BlockHitResult(hitVec, Direction.SOUTH, leverPos, false)
                mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
                player.swing(InteractionHand.MAIN_HAND)
            }
        }
    }
}
