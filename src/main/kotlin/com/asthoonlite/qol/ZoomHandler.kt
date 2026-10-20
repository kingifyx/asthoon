package com.asthoonlite.qol

import com.asthoonlite.config.Config
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object ZoomHandler {

    var currentFactor = 1.0f
    private var isZooming = false

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { mc ->
            if (mc.player == null) {
                isZooming = false
                currentFactor = 1.0f
                return@register
            }

            val target = if (mc.screen == null && isZoomKeyDown(mc)) {
                Config.zoomMultiplier.coerceIn(0.1f, 1.0f)
            } else {
                1.0f
            }

            isZooming = target < 0.99f
            // Smoothly ease FOV
            currentFactor += (target - currentFactor) * 0.35f
            if (kotlin.math.abs(currentFactor - target) < 0.005f) {
                currentFactor = target
            }
        }
    }

    private fun isZoomKeyDown(mc: Minecraft): Boolean {
        val window = mc.window
        val key = Config.zoomKey
        if (key <= 0) return false
        return try {
            InputConstants.isKeyDown(window, key)
        } catch (_: Throwable) {
            false
        }
    }

    fun isZooming(): Boolean = isZooming || currentFactor < 0.99f

    fun getFovMultiplier(): Float = currentFactor
}
