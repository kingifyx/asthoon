package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.api.FloorType
import com.asthoonlite.hud.AlertHud
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.sounds.SoundEvents

object WatcherKillAlert {

    private var countdownTicks = -1
    private var alertTriggered = false

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay && DungeonContext.inDungeon) {
                val msg = ChatFormatting.stripFormatting(text.string) ?: ""
                if (msg == "[BOSS] The Watcher: Let's see how you can handle this." || msg.startsWith("[BOSS] The Watcher:")) {
                    if (countdownTicks < 0 && !alertTriggered) {
                        // F7/M7 is 20.5s (410 ticks), other floors 20.0s (400 ticks)
                        val isF7 = DungeonContext.floor == FloorType.F7 || DungeonContext.floor == FloorType.M7
                        countdownTicks = if (isF7) 410 else 400
                        alertTriggered = false
                    }
                } else if (msg.contains("The Watcher: You have proven yourself", ignoreCase = true) ||
                           msg.contains("The BLOOD DOOR has been opened!", ignoreCase = true)) {
                    reset()
                }
            }
            true
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (!Config.watcherKillAlert || !DungeonContext.inDungeon) {
                if (countdownTicks > 0) reset()
                return@register
            }

            if (countdownTicks > 0) {
                countdownTicks--
                if (countdownTicks == 0) {
                    alertTriggered = true
                    AlertHud.show("§c[Watcher] Kill Now", 0xFFFF5555.toInt(), SoundEvents.NOTE_BLOCK_PLING, 1.2f)
                }
            }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    fun reset() {
        countdownTicks = -1
        alertTriggered = false
    }
}
