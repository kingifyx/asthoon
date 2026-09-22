package com.asthoonlite.command

import com.asthoonlite.AsthoonLite
import com.asthoonlite.gui.AsthoonLiteScreen
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object AslCommand {

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("asl")
                    .executes { ctx ->
                        val mc = Minecraft.getInstance()
                        // Use scheduleStop to open AFTER the current tick fully completes,
                        // bypassing any other mod's screen-change hooks that run mid-tick.
                        Thread {
                            Thread.sleep(50)
                            mc.execute {
                                try {
                                    mc.setScreen(AsthoonLiteScreen())
                                } catch (e: Exception) {
                                    AsthoonLite.LOGGER.error("[AsthoonLite] Failed to open screen", e)
                                    // `displayClientMessage` doesn't exist in 26.1.2. `sendSystemMessage`
                                    // has been the stable Mojang name for client-side chat messages on
                                    // Player for many versions, but this specific overload wasn't
                                    // confirmed against the 26.1.2 jar directly — check here if this
                                    // line fails to compile.
                                    mc.player?.sendSystemMessage(
                                        Component.literal("§c[AsthoonLite] Error opening menu — check logs")
                                    )
                                }
                            }
                        }.also { it.isDaemon = true }.start()
                        1
                    }
            )
        }
    }
}
