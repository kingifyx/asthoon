package com.asthoonlite.dungeon

import com.asthoonlite.QuietMode
import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.api.mapEnums.DoorTypes
import com.asthoonlite.dungeon.map.DungeonScanner
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents

object DoorESP {

    fun register() {
        LevelRenderEvents.END_EXTRACTION.register { _ ->
            if (!DungeonContext.inDungeon) return@register
            if (QuietMode.suppressing() && !Config.doorStreamProof) return@register

            val doors = DungeonScanner.doors
            for (door in doors) {
                if (door == null || door.opened) continue

                val isWither = door.type == DoorTypes.WITHER && Config.witherDoorEsp
                val isBlood = door.type == DoorTypes.BLOOD && Config.bloodDoorEsp
                if (!isWither && !isBlood) continue

                val wx = door.comp.wx + 0.5
                val wz = door.comp.wz + 0.5
                val y1 = 69.0
                val y2 = 73.0

                val isZOriented = (door.comp.cx and 1) == 1
                val x1 = if (isZOriented) wx - 0.5 else wx - 1.5
                val x2 = if (isZOriented) wx + 0.5 else wx + 1.5
                val z1 = if (isZOriented) wz - 1.5 else wz - 0.5
                val z2 = if (isZOriented) wz + 1.5 else wz + 0.5

                val (r, g, b) = if (isBlood) {
                    Triple(1.0f, 0.1f, 0.1f) // Crimson Red for Blood Door
                } else {
                    Triple(1.0f, 0.65f, 0.0f) // Gold/Amber for Wither Door
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
