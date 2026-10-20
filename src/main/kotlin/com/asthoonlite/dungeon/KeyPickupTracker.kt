package com.asthoonlite.dungeon

import com.asthoonlite.QuietMode
import com.asthoonlite.config.Config
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
import java.util.UUID

object KeyPickupTracker {

    private val WITHER_KEY_UUID = UUID.fromString("2865274b-3097-394e-8149-ec629c72d850")
    private val BLOOD_KEY_UUID = UUID.fromString("73f6d1f9-df41-3d1d-b98c-e1442d915885")

    private val witherKeyRegex = Regex("""^.*?(\w+) has obtained Wither Key!$""")
    private val bloodKeyRegex = Regex("""^.*?(\w+) has obtained Blood Key!$""")
    private val witherDoorOpenedRegex = Regex("""^(?:\[.+?\] )?(\w+) opened a WITHER door!$""")
    private val bloodDoorOpenedRegex = Regex("""^The BLOOD DOOR has been opened!$""", RegexOption.IGNORE_CASE)

    var witherKeys = 0
    val hasWitherKey get() = witherKeys > 0
    var hasBloodKey = false

    fun reset() {
        witherKeys = 0
        hasBloodKey = false
    }

    fun onChatMessage(rawMessage: String) {
        val message = net.minecraft.ChatFormatting.stripFormatting(rawMessage) ?: rawMessage

        if (message.contains("A Wither Key was picked up!") || witherKeyRegex.containsMatchIn(message)) {
            witherKeys++
            playKeySound()
            return
        }

        if (message.contains("A Blood Key was picked up!") || bloodKeyRegex.containsMatchIn(message)) {
            hasBloodKey = true
            playKeySound()
            return
        }

        if (message.contains("The BLOOD DOOR has been opened!") || bloodDoorOpenedRegex.containsMatchIn(message)) {
            hasBloodKey = false
            return
        }

        if (witherDoorOpenedRegex.containsMatchIn(message)) {
            witherKeys = maxOf(0, witherKeys - 1)
            return
        }
    }

    fun playKeySound() {
        val mc = Minecraft.getInstance()
        mc.execute {
            mc.player?.playSound(SoundEvents.VAULT_OPEN_SHUTTER, 1.5f, 1.0f)
        }
    }

    fun register() {
        LevelRenderEvents.END_EXTRACTION.register { context ->
            if (!DungeonContext.inDungeon) return@register
            if (QuietMode.suppressing() && !Config.doorStreamProof) return@register

            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@register
            val player = mc.player ?: return@register

            val cam = mc.gameRenderer.mainCamera
            val camPos = cam.position()
            // Tracer origin at chest height below crosshair/camera to avoid head origin and screen clutter
            val startX = camPos.x
            val startY = camPos.y - 0.35
            val startZ = camPos.z
            val partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(true)

            // 1. Scan ArmorStands wearing wither/blood key skulls
            val stands = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(64.0))
            for (stand in stands) {
                if (stand.isDeadOrDying || stand.isRemoved) continue
                val headItem = stand.getItemBySlot(EquipmentSlot.HEAD)
                if (headItem.isEmpty || !headItem.`is`(Items.PLAYER_HEAD)) continue

                // Avoid wither essence (has fractional y 0.53125)
                val fractionalY = stand.y - stand.y.toInt()
                if (kotlin.math.abs(fractionalY - 0.53125) < 0.01) continue

                val profile = headItem.get(DataComponents.PROFILE)
                val profileId = profile?.partialProfile()?.id ?: continue

                val isWither = profileId == WITHER_KEY_UUID
                val isBlood = profileId == BLOOD_KEY_UUID
                if (!isWither && !isBlood) continue

                val (r, g, b) = if (isBlood) Triple(1.0f, 0.1f, 0.1f) else Triple(1.0f, 0.75f, 0.0f)
                val x = net.minecraft.util.Mth.lerp(partialTick.toDouble(), stand.xo, stand.x)
                val y = net.minecraft.util.Mth.lerp(partialTick.toDouble(), stand.yo, stand.y) + 1.2
                val z = net.minecraft.util.Mth.lerp(partialTick.toDouble(), stand.zo, stand.z)
                val half = 0.35

                WorldBoxRenderer.queueFilled(x - half, y, z - half, x + half, y + 0.7, z + half, r, g, b, 0.35f, throughWalls = true)
                WorldBoxRenderer.queueOutline(x - half, y, z - half, x + half, y + 0.7, z + half, r, g, b, 1.0f, thickness = 0.02, throughWalls = true)
                WorldBoxRenderer.queueLine(startX, startY, startZ, x, y + 0.35, z, r, g, b, 0.8f, thickness = 0.03, throughWalls = true)
            }

            // 2. Scan ItemEntities named Wither Key / Blood Key
            val items = level.getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(64.0))
            for (itemEnt in items) {
                if (!itemEnt.isAlive || itemEnt.isRemoved) continue
                val name = itemEnt.item.hoverName.string
                val isWither = name.contains("Wither Key", ignoreCase = true)
                val isBlood = name.contains("Blood Key", ignoreCase = true)
                if (!isWither && !isBlood) continue

                val (r, g, b) = if (isBlood) Triple(1.0f, 0.1f, 0.1f) else Triple(1.0f, 0.75f, 0.0f)
                val x = net.minecraft.util.Mth.lerp(partialTick.toDouble(), itemEnt.xo, itemEnt.x)
                val y = net.minecraft.util.Mth.lerp(partialTick.toDouble(), itemEnt.yo, itemEnt.y)
                val z = net.minecraft.util.Mth.lerp(partialTick.toDouble(), itemEnt.zo, itemEnt.z)
                val half = 0.25

                WorldBoxRenderer.queueFilled(x - half, y, z - half, x + half, y + 0.5, z + half, r, g, b, 0.35f, throughWalls = true)
                WorldBoxRenderer.queueOutline(x - half, y, z - half, x + half, y + 0.5, z + half, r, g, b, 1.0f, thickness = 0.02, throughWalls = true)
                WorldBoxRenderer.queueLine(startX, startY, startZ, x, y + 0.25, z, r, g, b, 0.8f, thickness = 0.03, throughWalls = true)
            }
        }
    }
}
