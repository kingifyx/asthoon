package com.asthoonlite.fishing

import com.asthoonlite.config.Config
import com.asthoonlite.hud.AlertHud
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvents

/**
 * Bite alert, NOT an auto-reel/recast macro (see conversation — automating
 * the actual right-click reel/recast was intentionally left out since it's
 * simulated input on a trigger, i.e. a macro).
 *
 * This only watches your own fishing bobber entity's vertical motion each
 * tick and pings you the moment it dips like a bite, the same signal every
 * "fish bite" sound-alert mod uses — you still reel and recast yourself.
 *
 * NOTE: `Player#fishing` / the equivalent 26.1.2 Mojang-mapped field or
 * accessor for "this player's active FishingHook entity" is a best-effort
 * guess here (it's historically been a plain `fishing` field on
 * `net.minecraft.world.entity.player.Player`) — check here first if it
 * doesn't compile.
 */
object FishBiteAlert {

    private const val BITE_VELOCITY_THRESHOLD = -0.08

    private var wasHookPresent = false
    private var alertedThisCast = false
    private var lastHookY = Double.NaN

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!Config.fishBiteAlertEnabled) return@register
            tick()
        }
    }

    private fun tick() {
        val player = Minecraft.getInstance().player ?: return
        val hook = player.fishing

        if (hook == null) {
            wasHookPresent = false
            alertedThisCast = false
            lastHookY = Double.NaN
            return
        }

        if (!wasHookPresent) {
            // Bobber was just cast — reset bite tracking for this cast.
            wasHookPresent = true
            alertedThisCast = false
            lastHookY = hook.y
            return
        }

        val deltaY = hook.y - lastHookY
        lastHookY = hook.y

        if (!alertedThisCast && deltaY < BITE_VELOCITY_THRESHOLD) {
            alertedThisCast = true
            AlertHud.show("Fish On!", 0xFF00C853.toInt(), SoundEvents.NOTE_BLOCK_PLING)
        }
    }
}
