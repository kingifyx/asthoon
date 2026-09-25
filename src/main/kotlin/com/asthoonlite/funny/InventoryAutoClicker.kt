package com.asthoonlite.funny

import com.asthoonlite.config.Config
import com.asthoonlite.mixin.AbstractContainerScreenAccessor
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW
import java.util.Random

/**
 * Spams left-clicks in inventories/containers (such as claiming stash items)
 * at a lower, human-simulated CPS when holding down left-click over items.
 */
object InventoryAutoClicker {

    private val random = Random()
    private var baseCpsDrift = 5.0
    private var lastDriftTime = 0L
    private var nextClickTime = 0L
    private var wasMouseDown = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            tick()
        }
    }

    private fun tick() {
        if (!Config.inventoryAutoClickerEnabled) {
            reset()
            return
        }

        val mc = Minecraft.getInstance()
        val screen = mc.screen as? AbstractContainerScreen<*> ?: run {
            reset()
            return
        }
        val window = mc.window

        val isLeftMouseDown = GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS

        if (!isLeftMouseDown) {
            reset()
            return
        }

        val screenAcc = screen as? AbstractContainerScreenAccessor ?: return
        val slot = screenAcc.hoveredSlot
        if (slot == null || !slot.hasItem()) {
            // When hovering over empty slots, don't spam clicks
            reset()
            return
        }

        val now = System.currentTimeMillis()

        // First click is handled by the physical mouse-down event
        if (!wasMouseDown) {
            wasMouseDown = true
            nextClickTime = now + calculateNextInterval(now, Config.inventoryAutoClickerCps.toDouble())
            return
        }

        if (now >= nextClickTime) {
            val isShiftDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                    InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)
            val inputType = if (isShiftDown) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP

            screenAcc.invokeSlotClicked(slot, slot.index, 0, inputType)
            nextClickTime = now + calculateNextInterval(now, Config.inventoryAutoClickerCps.toDouble())
        }
    }

    private fun reset() {
        wasMouseDown = false
        nextClickTime = 0L
    }

    /**
     * Generates humanized click intervals for inventory clicking at a lower rate (~5 CPS default).
     */
    private fun calculateNextInterval(now: Long, targetCps: Double): Long {
        val cps = targetCps.coerceIn(2.0, 15.0)

        // Drift base CPS slightly every ~1000ms
        if (now - lastDriftTime > 1000L) {
            val drift = (random.nextDouble() * 1.2) - 0.6 // ±0.6 CPS drift
            baseCpsDrift = (cps + drift).coerceIn(cps - 0.8, cps + 0.8)
            lastDriftTime = now
        }

        val baseDelay = (1000.0 / baseCpsDrift).toLong()

        // Gaussian jitter
        val gaussian = random.nextGaussian()
        val jitter = if (gaussian < 0) (gaussian * 10.0).toLong() else (gaussian * 22.0).toLong()

        var finalDelay = baseDelay + jitter

        // Micro-variations
        val roll = random.nextDouble()
        if (roll < 0.02) {
            finalDelay += random.nextInt(40, 90)
        } else if (roll < 0.04) {
            finalDelay -= random.nextInt(15, 25)
        }

        val minDelay = (1000.0 / (cps + 2.0)).toLong().coerceAtLeast(80L)
        val maxDelay = (1000.0 / (cps - 1.5).coerceAtLeast(1.0)).toLong()
        return finalDelay.coerceIn(minDelay, maxDelay)
    }
}
