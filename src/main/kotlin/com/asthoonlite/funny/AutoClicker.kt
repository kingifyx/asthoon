package com.asthoonlite.funny

import com.asthoonlite.config.Config
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft

/**
 * Plain, fixed-interval right-click autoclicker with an adjustable CPS
 * slider. Deliberately does NOT add randomized/"humanized" timing —
 * that's specifically an anti-cheat evasion technique and was left out on
 * purpose (see conversation). This is a straightforward macro: toggle it
 * on, it right-clicks at a fixed rate, toggle it off. Use is your call.
 */
object AutoClicker {

    // Fractional click accumulator instead of a plain tick counter: at 20
    // client ticks/sec, a counter that only fires "once every N ticks" can
    // never exceed 20 clicks/sec no matter what N is. To support rates up
    // to 500/sec (i.e. many clicks per tick) this instead accumulates
    // "clicks owed" every tick (cps / 20.0) and fires off however many
    // whole clicks have banked up, carrying the leftover fraction forward.
    private var clicksOwed = 0.0
    private var previousKeyDown = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            val mc = Minecraft.getInstance()
            handleKeybind(mc)
            if (!Config.autoClickerEnabled) { clicksOwed = 0.0; return@register }
            tick()
        }
    }

    private fun handleKeybind(mc: Minecraft) {
        val key = Config.autoClickerKey
        if (key < 0) {
            previousKeyDown = false
            return
        }

        val down = InputConstants.isKeyDown(mc.window, key)
        if (down && !previousKeyDown && mc.screen == null) {
            Config.autoClickerEnabled = !Config.autoClickerEnabled
        }
        previousKeyDown = down
    }

    private fun tick() {
        val cps = Config.autoClickerCps.coerceIn(1, 500)
        clicksOwed += cps / 20.0

        val clicksThisTick = clicksOwed.toInt()
        if (clicksThisTick <= 0) return
        clicksOwed -= clicksThisTick

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.screen != null) return // don't click while a GUI is open

        repeat(clicksThisTick) {
            // Right-click = "use item / interact", the same action vanilla
            // performs on a right-click, triggered directly rather than
            // faking a mouse event.
            mc.gameMode?.let { gameMode ->
                val hitResult = mc.hitResult
                when {
                    hitResult != null && hitResult.type == net.minecraft.world.phys.HitResult.Type.BLOCK -> {
                        val blockHit = hitResult as net.minecraft.world.phys.BlockHitResult
                        gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND, blockHit)
                    }
                    else -> gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND)
                }
            }
        }
    }
}
