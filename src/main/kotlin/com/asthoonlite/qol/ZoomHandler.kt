package com.asthoonlite.qol

import com.asthoonlite.config.Config
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import kotlin.math.abs
import kotlin.math.exp

object ZoomHandler {

    private var currentFactor = 1.0f
    private var targetFactor = 1.0f
    private var scrollOffset = 0.0f
    private var lastFrameNanos = 0L

    fun register() {
        // No-op tick listener: interpolation is handled per-frame in calculateFov
    }

    fun isZoomKeyDown(mc: Minecraft = Minecraft.getInstance()): Boolean {
        val window = mc.window
        val key = Config.zoomKey
        if (key <= 0 || mc.screen != null) return false
        return try {
            InputConstants.isKeyDown(window, key)
        } catch (_: Throwable) {
            false
        }
    }

    fun onMouseScroll(yoffset: Double): Boolean {
        val mc = Minecraft.getInstance()
        if (!isZoomKeyDown(mc)) return false

        // Scrolling up (yoffset > 0) zooms in; scrolling down (yoffset < 0) zooms out
        val step = (yoffset * 0.06f).toFloat()
        scrollOffset = (scrollOffset + step).coerceIn(-0.85f, 0.88f)
        return true
    }

    fun isZooming(): Boolean = isZoomKeyDown() || currentFactor < 0.999f

    fun getFovMultiplier(): Float {
        val mc = Minecraft.getInstance()
        val keyDown = isZoomKeyDown(mc)

        if (!keyDown) {
            scrollOffset = 0.0f
            targetFactor = 1.0f
        } else {
            val baseTarget = if (Config.zoomScrollOnly) 1.0f else Config.zoomMultiplier.coerceIn(0.1f, 1.0f)
            targetFactor = (baseTarget - scrollOffset).coerceIn(0.06f, 1.0f)
        }

        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val deltaSeconds = ((now - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1).toFloat()
        lastFrameNanos = now

        // Smooth delta-time interpolation (24/sec rate)
        val blendRate = 24.0f
        val blend = 1.0f - exp(-blendRate * deltaSeconds)
        currentFactor += (targetFactor - currentFactor) * blend
        if (abs(currentFactor - targetFactor) < 0.001f) {
            currentFactor = targetFactor
        }

        return currentFactor
    }
}
