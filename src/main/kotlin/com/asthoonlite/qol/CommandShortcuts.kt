package com.asthoonlite.qol

import com.asthoonlite.config.Config
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object CommandShortcuts {

    fun processCommand(command: String): String {
        val trimmed = command.trim()
        val parts = trimmed.split(" ", limit = 2)
        val trigger = parts[0].removePrefix("/").lowercase()
        val args = if (parts.size > 1) " " + parts[1] else ""

        val shortcuts = Config.commandShortcuts
        val target = shortcuts[trigger] ?: shortcuts["/$trigger"] ?: return command

        val cleanTarget = target.removePrefix("/")
        return cleanTarget + args
    }

    fun addShortcut(trigger: String, target: String) {
        val cleanTrigger = trigger.trim().removePrefix("/").lowercase()
        val cleanTarget = target.trim().removePrefix("/")
        if (cleanTrigger.isEmpty() || cleanTarget.isEmpty()) return

        val map = Config.commandShortcuts.toMutableMap()
        map[cleanTrigger] = cleanTarget
        Config.commandShortcuts = map
        notify("§a[AsthoonLite] Shortcut added: §e/$cleanTrigger §7-> §a/$cleanTarget")
    }

    fun removeShortcut(trigger: String) {
        val cleanTrigger = trigger.trim().removePrefix("/").lowercase()
        val map = Config.commandShortcuts.toMutableMap()
        if (map.remove(cleanTrigger) != null) {
            Config.commandShortcuts = map
            notify("§e[AsthoonLite] Shortcut removed: §c/$cleanTrigger")
        } else {
            notify("§c[AsthoonLite] Shortcut not found: §e/$cleanTrigger")
        }
    }

    fun notify(message: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(message))
    }
}
