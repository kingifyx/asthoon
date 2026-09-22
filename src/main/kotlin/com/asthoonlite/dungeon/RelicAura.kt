package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand

/** M7 relic assistance using the supplied Noamm relic coordinates. */
object RelicAura {
    private data class Relic(val name: String, val cauldron: BlockPos, val spawn: BlockPos, val color: FloatArray)
    private val relics = listOf(
        Relic("RED", BlockPos(51, 7, 42), BlockPos(20, 7, 59), floatArrayOf(1f, 0.1f, 0.1f)),
        Relic("ORANGE", BlockPos(57, 7, 42), BlockPos(26, 7, 59), floatArrayOf(1f, 0.45f, 0.05f)),
        Relic("GREEN", BlockPos(49, 7, 44), BlockPos(20, 7, 94), floatArrayOf(0.1f, 1f, 0.1f)),
        Relic("BLUE", BlockPos(59, 7, 44), BlockPos(91, 7, 94), floatArrayOf(0.1f, 0.55f, 1f)),
        Relic("PURPLE", BlockPos(54, 7, 41), BlockPos(56, 9, 132), floatArrayOf(0.65f, 0.1f, 0.7f)),
    )

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { render() }
    }

    private fun tick() {
        if (!Config.relicAuraEnabled || !DungeonContext.inDungeon) return
        // Noamm's relic feature identifies the relic by the item name. Keep a
        // small local helper here so the render can show the matching cauldron.
    }

    private fun render() {
        if (!Config.relicAuraEnabled || !DungeonContext.inDungeon) return
        val mc = Minecraft.getInstance(); val player = mc.player ?: return
        val held = player.inventory.getItem(8).hoverName.string.uppercase()
        val relic = relics.firstOrNull { held.contains(it.name) } ?: return
        val c = relic.color
        fun box(p: BlockPos) {
            WorldBoxRenderer.queueOutline(p.x.toDouble(), p.y.toDouble(), p.z.toDouble(), p.x + 1.0, p.y + 1.0, p.z + 1.0, c[0], c[1], c[2], 1f, throughWalls = true)
            WorldBoxRenderer.queueFilled(p.x.toDouble(), p.y.toDouble(), p.z.toDouble(), p.x + 1.0, p.y + 1.0, p.z + 1.0, c[0], c[1], c[2], 0.12f, throughWalls = true)
        }
        box(relic.cauldron)
        box(relic.spawn)

        // Also box a currently spawned relic armor stand if one is loaded.
        mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>()?.filter {
            it.getItemBySlot(EquipmentSlot.HEAD).hoverName.string.contains("Relic", true)
        }?.forEach {
            val b = it.boundingBox
            WorldBoxRenderer.queueOutline(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, c[0], c[1], c[2], 1f, throughWalls = true)
        }
    }
}
