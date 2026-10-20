package com.asthoonlite.qol

import com.asthoonlite.config.Config
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object CommandKeybinds {

    @Volatile var pendingCommand: String? = null
    private val keyStates = HashMap<Int, Boolean>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            if (mc.screen != null) return@register

            val window = mc.window
            for ((key, command) in Config.commandBinds) {
                val isDown = try { InputConstants.isKeyDown(window, key) } catch (_: Throwable) { false }
                val wasDown = keyStates[key] == true
                if (isDown && !wasDown) {
                    executeCommand(command)
                }
                keyStates[key] = isDown
            }
        }
    }

    fun handleKeyInput(key: Int, action: Int): Boolean {
        if (action != GLFW.GLFW_PRESS) return false

        val pending = pendingCommand
        if (pending != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                pendingCommand = null
                sendChat("§c[AsthoonLite] Command bind cancelled.")
                return true
            }

            Config.commandBinds[key] = pending
            Config.save()
            pendingCommand = null
            val keyName = try {
                InputConstants.Type.KEYSYM.getOrCreate(key).name ?: GLFW.glfwGetKeyName(key, 0) ?: "Key $key"
            } catch (_: Throwable) {
                "Key $key"
            }
            sendChat("§a[AsthoonLite] Successfully bound §f$keyName §ato command: §e$pending")
            return true
        }

        return false
    }

    fun executeCommand(command: String) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val trimmed = command.trim()
        if (trimmed.startsWith("/")) {
            player.connection.sendCommand(trimmed.removePrefix("/"))
        } else {
            player.connection.sendChat(trimmed)
        }
    }

    fun startBinding(command: String) {
        pendingCommand = command
        sendChat("§b[AsthoonLite] Press any key to bind to §e\"$command\" §7(Press ESC to cancel)...")
    }

    fun listBinds() {
        if (Config.commandBinds.isEmpty()) {
            sendChat("§7[AsthoonLite] No command keybinds currently set. Use §f/asl bind <command> §7to add one.")
            return
        }
        sendChat("§b[AsthoonLite] Active Command Keybinds:")
        for ((key, cmd) in Config.commandBinds) {
            val keyName = try {
                InputConstants.Type.KEYSYM.getOrCreate(key).name ?: GLFW.glfwGetKeyName(key, 0) ?: "Key $key"
            } catch (_: Throwable) {
                "Key $key"
            }
            sendChat(" §e• §f$keyName §7-> §b$cmd")
        }
    }

    fun clearBinds() {
        Config.commandBinds.clear()
        Config.save()
        sendChat("§a[AsthoonLite] Cleared all command keybinds.")
    }

    fun sendChat(message: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(message))
    }
}
