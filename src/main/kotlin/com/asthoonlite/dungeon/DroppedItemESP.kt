package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.qol.ItemRarityOverlay
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.item.ItemEntity

object DroppedItemESP {

    fun register() {
        LevelRenderEvents.END_EXTRACTION.register {
            if (!Config.droppedItemEsp) return@register
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@register
            val player = mc.player ?: return@register

            val entities = level.getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(64.0))
            for (entity in entities) {
                if (entity.distanceToSqr(player) > 64.0 * 64.0) continue

                val stack = entity.item
                if (stack.isEmpty) continue

                val isMobDrop = ItemRarityOverlay.isRedundantMobDrop(stack)
                if (!isMobDrop) continue

                val quality = ItemRarityOverlay.getDungeonQuality(stack)
                val rarity = ItemRarityOverlay.getRarity(stack)

                val (r, g, b) = Triple(0.85f, 0.08f, 0.15f) // Deep crimson red

                val bb = entity.boundingBox
                val exp = 0.05
                val minX = bb.minX - exp
                val minY = bb.minY
                val minZ = bb.minZ - exp
                val maxX = bb.maxX + exp
                val maxY = bb.maxY + exp
                val maxZ = bb.maxZ + exp

                WorldBoxRenderer.queueFilled(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 0.35f, throughWalls = true)
                WorldBoxRenderer.queueOutline(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 1.0f, thickness = 0.02, throughWalls = true)
            }
        }
    }
}
