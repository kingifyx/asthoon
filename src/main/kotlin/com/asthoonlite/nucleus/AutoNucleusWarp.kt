package com.asthoonlite.nucleus

import com.asthoonlite.config.Config
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import java.util.Timer
import java.util.TimerTask
import java.util.regex.Pattern
import kotlin.random.Random

/**
 * Ported from SkyMyce's MiningFeatures.kt `autoNucleusWarp` — when the
 * "✦ CRYSTAL FOUND (x/y)" chat line appears (Crystal Nucleus spawned),
 * automatically sends `/warp nuc` after a short randomized delay, matching
 * SkyMyce's own min/max delay window so this doesn't fire instantly.
 */
object AutoNucleusWarp {

    private val crystalPattern = Pattern.compile("^\\s*✦ CRYSTAL FOUND \\((\\d+)/(\\d+)\\)$")
    private val timer = Timer("asthoonlite-nucleus-warp", true)

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay) onChat(ChatFormatting.stripFormatting(text.string) ?: "")
            true
        }
    }

    private fun onChat(text: String) {
        if (!Config.autoNucleusWarpEnabled) return
        if (!crystalPattern.matcher(text).matches()) return

        val min = Config.autoNucleusWarpMinSec.coerceAtLeast(0.0)
        val max = Config.autoNucleusWarpMaxSec.coerceAtLeast(min)
        val delaySeconds = if (max > min) Random.nextDouble(min, max) else min
        val delayMs = (delaySeconds * 1000).toLong()

        timer.schedule(object : TimerTask() {
            override fun run() {
                val mc = Minecraft.getInstance()
                mc.execute {
                    // NOTE: `ClientPacketListener#sendCommand(String)` is the
                    // long-stable Mojang name for "run this as a client
                    // command/chat message" — best-effort against 26.1.2,
                    // check here first if it doesn't compile.
                    mc.player?.connection?.sendCommand("warp nuc")
                }
            }
        }, delayMs)
    }
}
