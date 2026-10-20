package com.asthoonlite.dungeon

import com.asthoonlite.QuietMode
import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.api.mapEnums.DoorTypes
import com.asthoonlite.dungeon.map.DungeonScanner
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

object DoorESP {

    fun register() {
        LevelRenderEvents.END_EXTRACTION.register { _ ->
            if (!DungeonContext.inDungeon) return@register
            if (QuietMode.suppressing() && !Config.doorStreamProof) return@register

            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@register
            val player = mc.player ?: return@register

            val doors = DungeonScanner.doors

            val playerPos = player.position()
            val playerComp = com.asthoonlite.dungeon.api.WorldPosition(playerPos.x.toInt(), playerPos.z.toInt()).toComponent()
            val playerRoom = DungeonScanner.currentRoom
                ?: (if (playerComp.isInBounds()) DungeonScanner.rooms.getOrNull(playerComp.getRoomIdx()) else null)

            // Find closest / in-room unopened wither door
            val targetWitherDoor = doors.filter { it != null && !it.opened && it.type == DoorTypes.WITHER }
                .minByOrNull { d ->
                    val dx = (d!!.comp.wx + 0.5) - player.x
                    val dz = (d.comp.wz + 0.5) - player.z
                    val roomBonus = if (playerRoom != null && d.rooms.contains(playerRoom)) 0.0 else 1000.0
                    roomBonus + (dx * dx + dz * dz)
                }

            for (door in doors) {
                if (door == null || door.opened) continue

                val isWither = door.type == DoorTypes.WITHER && Config.witherDoorEsp
                val isBlood = door.type == DoorTypes.BLOOD && Config.bloodDoorEsp
                if (!isWither && !isBlood) continue

                // Check physical world blocks so doors with no blocks (e.g. fairy room entrance) don't flicker
                val bpos = BlockPos(door.comp.wx, 69, door.comp.wz)
                if (level.isLoaded(bpos)) {
                    val bState = level.getBlockState(bpos)
                    val hasDoorBlock = if (isBlood) bState.block == Blocks.RED_TERRACOTTA else bState.block == Blocks.COAL_BLOCK
                    if (!hasDoorBlock) {
                        door.opened = true
                        continue
                    }
                }

                val wx = door.comp.wx + 0.5
                val wz = door.comp.wz + 0.5
                val y1 = 69.0
                val y2 = 73.0

                val isZOriented = (door.comp.cx and 1) == 1
                val x1 = if (isZOriented) wx - 0.5 else wx - 1.5
                val x2 = if (isZOriented) wx + 0.5 else wx + 1.5
                val z1 = if (isZOriented) wz - 1.5 else wz - 0.5
                val z2 = if (isZOriented) wz + 1.5 else wz + 0.5

                val hasKey = if (isBlood) {
                    KeyPickupTracker.hasBloodKey
                } else {
                    (door === targetWitherDoor) && KeyPickupTracker.hasWitherKey
                }

                val (r, g, b) = if (hasKey) {
                    Triple(0.0f, 1.0f, 0.4f) // Bright emerald green when key is obtained
                } else {
                    Triple(1.0f, 0.15f, 0.15f) // Red when key is not grabbed
                }

                val throughWalls = Config.doorSeeThrough

                if (Config.doorHighlightFill) {
                    WorldBoxRenderer.queueFilled(x1, y1, z1, x2, y2, z2, r, g, b, 0.25f, throughWalls)
                }
                WorldBoxRenderer.queueOutline(x1, y1, z1, x2, y2, z2, r, g, b, 1.0f, throughWalls = throughWalls)
            }
        }
    }
}
