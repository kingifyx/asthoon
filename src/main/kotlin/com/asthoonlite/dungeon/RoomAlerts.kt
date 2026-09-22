package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.api.DungeonRoom
import com.asthoonlite.dungeon.api.mapEnums.CheckmarkTypes
import com.asthoonlite.dungeon.api.mapEnums.DoorTypes
import com.asthoonlite.dungeon.api.mapEnums.RoomTypes
import com.asthoonlite.dungeon.map.DungeonScanner
import com.asthoonlite.hud.AlertHud
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.item.ItemEntity
import java.util.regex.Pattern

/**
 * Room-clear and secret feedback.
 *
 * Implements Devonian's CurrentRoomCleared logic: tracks DungeonScanner.currentRoom
 * checkmark transitions. When a room gains a WHITE or GREEN checkmark, "Room Cleared!"
 * is emitted (with configurable filters for blood room and blood rush).
 * When checkmark transitions from WHITE to GREEN, "Secrets Done!" is emitted.
 */
object RoomAlerts {

    private val secretsPattern = Pattern.compile("(\\d+)/(\\d+) Secrets")

    private val dungeonItemDrops = listOf(
        "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion",
        "Healing Potion VIII Splash Potion", "Healing VIII Splash Potion",
        "Healing 8 Splash Potion", "Decoy", "Inflatable Jerry", "Spirit Leap",
        "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key",
        "Treasure Talisman", "Revive Stone", "Architect's First Draft",
        "Secret Dye", "Candycomb"
    )

    private var lastSecrets = -1
    private var lastTotal = -1
    private var clientTick = 0L
    private val visibleSecretItems = HashSet<Int>()
    private var recentItemPickupTick = Long.MIN_VALUE

    private var lastRoom: DungeonRoom? = null
    private var wasCleared = false
    private var lastCheckmark: CheckmarkTypes? = null
    private var wasInDungeon = false

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            val message = ChatFormatting.stripFormatting(text.string) ?: ""
            if (overlay) onActionBar(message)
            else if (message == "[NPC] Mort: Here, I found this map when I first entered the dungeon.") {
                resetDungeonState()
            }
            true
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> resetRun() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> resetRun() }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (!DungeonContext.inDungeon) {
                if (wasInDungeon) resetDungeonState()
                wasInDungeon = false
                visibleSecretItems.clear()
                return@register
            }

            wasInDungeon = true
            clientTick++
            scanSecretItems()
            checkRoomClearTransitions()
            SecretSounds.tick()
        }
    }

    private fun checkRoomClearTransitions() {
        val room = DungeonScanner.currentRoom
        if (room != null) {
            val isCleared = room.checkmark === CheckmarkTypes.WHITE || room.checkmark === CheckmarkTypes.GREEN
            val shouldTrigger = if (Config.roomClearNoBlood) room.type !== RoomTypes.BLOOD else true

            if (lastRoom === room && !wasCleared && isCleared && room.type !== RoomTypes.FAIRY && shouldTrigger) {
                if (!Config.roomClearOnlyKey || room.doors.any { it.type === DoorTypes.WITHER || it.type === DoorTypes.BLOOD }) {
                    if (Config.roomClearAlertEnabled) {
                        val msg = (if (room.totalSecrets == 0) "§a" else "") + "Cleared"
                        AlertHud.show(
                            msg,
                            0xFFFFFFFF.toInt(),
                            SoundEvents.NOTE_BLOCK_PLING,
                            1.0f
                        )
                    }
                }
            }

            // Secrets Done alert: when current room transitions from WHITE to GREEN checkmark (Noamm style)
            if (Config.roomSecretAlertEnabled && lastRoom === room &&
                lastCheckmark === CheckmarkTypes.WHITE &&
                room.checkmark === CheckmarkTypes.GREEN &&
                room.totalSecrets > 0) {
                AlertHud.show(
                    "§aSecrets Done!",
                    0xFF55FF55.toInt(),
                    SoundEvents.NOTE_BLOCK_PLING,
                    1.0f
                )
            }

            lastRoom = room
            wasCleared = isCleared
            lastCheckmark = room.checkmark
        } else {
            lastRoom = null
            wasCleared = false
            lastCheckmark = null
        }
    }

    private fun onActionBar(text: String) {
        if (!DungeonContext.inDungeon) return
        val m = secretsPattern.matcher(text)
        if (!m.find()) return

        val found = m.group(1).toIntOrNull() ?: return
        val total = m.group(2).toIntOrNull() ?: return

        if (lastSecrets >= 0 && found > lastSecrets) {
            // Floor secret item pickup chime
            if (Config.secretItemPickupSoundEnabled && recentItemPickupTick >= clientTick - 8) {
                playSecretItemSound()
            }
            SecretSounds.onSecretItem()
        }

        lastSecrets = found
        lastTotal = total
    }

    private fun scanSecretItems() {
        val mc = net.minecraft.client.Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        val current = HashSet<Int>()
        for (item in level.entitiesForRendering().filterIsInstance<ItemEntity>()) {
            if (item.distanceToSqr(player) > 36.0) continue
            val name = ChatFormatting.stripFormatting(item.item.hoverName.string) ?: continue
            if (dungeonItemDrops.any { name.contains(it, ignoreCase = true) }) {
                current += item.id
            }
        }

        if (visibleSecretItems.any { it !in current }) {
            recentItemPickupTick = clientTick
        }

        visibleSecretItems.clear()
        visibleSecretItems.addAll(current)
    }

    private fun playSecretItemSound() {
        AlertHud.playSound(SoundEvents.NOTE_BLOCK_PLING, 1.15f)
    }

    private fun resetDungeonState() {
        lastSecrets = -1
        lastTotal = -1
        clientTick = 0L
        visibleSecretItems.clear()
        recentItemPickupTick = Long.MIN_VALUE
        lastRoom = null
        wasCleared = false
        lastCheckmark = null
        DungeonMap.resetRun()
        StarMobESP.resetRun()
        HigherLowerSolver.resetRun()
        SecretAura.resetRun()
        SecretSounds.reset()
    }

    fun resetRun() {
        resetDungeonState()
        wasInDungeon = false
    }
}
