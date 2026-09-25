package com.asthoonlite.funny

import com.asthoonlite.config.Config
import com.asthoonlite.mixin.MinecraftAccessor
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.Random
import kotlin.jvm.optionals.getOrNull

/**
 * Simulates humanized left-clicks at ~7 CPS when holding down left-click while
 * wielding a qualifying weapon (Terminator, Claymore, Hyperion, or Slayer weapons).
 */
object WeaponAutoClicker {

    private val random = Random()
    private var baseCpsDrift = 7.0
    private var lastDriftTime = 0L
    private var nextClickTime = 0L
    private var wasMouseDown = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            tick()
        }
    }

    private fun tick() {
        if (!Config.weaponAutoClickerEnabled) {
            reset()
            return
        }

        val mc = Minecraft.getInstance()
        val player = mc.player
        val window = mc.window

        // Only active in-game with no open screen
        if (player == null || mc.screen != null) {
            reset()
            return
        }

        // Check if left mouse button is pressed
        val isLeftMouseDown = GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS

        if (!isLeftMouseDown) {
            reset()
            return
        }

        val heldItem = player.mainHandItem
        if (!isQualifyingWeapon(heldItem)) {
            reset()
            return
        }

        val now = System.currentTimeMillis()

        // The very first click is performed by the physical mouse down.
        if (!wasMouseDown) {
            wasMouseDown = true
            nextClickTime = now + calculateNextInterval(now, Config.weaponAutoClickerCps.toDouble())
            return
        }

        if (now >= nextClickTime) {
            performAttack(mc)
            nextClickTime = now + calculateNextInterval(now, Config.weaponAutoClickerCps.toDouble())
        }
    }

    private fun performAttack(mc: Minecraft) {
        val mcAcc = mc as? MinecraftAccessor ?: return
        // Clear missTime so air swings never lock out attacks
        mcAcc.missTime = 0
        mcAcc.invokeStartAttack()
        mcAcc.missTime = 0
    }

    private fun reset() {
        wasMouseDown = false
        nextClickTime = 0L
    }

    /**
     * Generates a natural, human-like click interval centered around target CPS (~7 CPS).
     * Includes speed drift over time, asymmetric Gaussian distribution, and occasional micro-stutters.
     */
    private fun calculateNextInterval(now: Long, targetCps: Double): Long {
        val cps = targetCps.coerceIn(4.0, 15.0)

        // Drift base CPS slightly every ~1000ms
        if (now - lastDriftTime > 1000L) {
            val drift = (random.nextDouble() * 1.6) - 0.8 // ±0.8 CPS drift
            baseCpsDrift = (cps + drift).coerceIn(cps - 1.0, cps + 1.0)
            lastDriftTime = now
        }

        val baseDelay = (1000.0 / baseCpsDrift).toLong()

        // Asymmetric Gaussian variance
        val gaussian = random.nextGaussian()
        val jitter = if (gaussian < 0) (gaussian * 8.0).toLong() else (gaussian * 18.0).toLong()

        var finalDelay = baseDelay + jitter

        // Micro-variations
        val roll = random.nextDouble()
        if (roll < 0.02) {
            finalDelay += random.nextInt(40, 80)
        } else if (roll < 0.04) {
            finalDelay -= random.nextInt(10, 20)
        }

        // Clamp to realistic bounds (never faster than 100ms for ~7 CPS)
        val minDelay = (1000.0 / (cps + 2.5)).toLong().coerceAtLeast(65L)
        val maxDelay = (1000.0 / (cps - 2.0).coerceAtLeast(2.0)).toLong()
        return finalDelay.coerceIn(minDelay, maxDelay)
    }

    /**
     * Checks if the item is a Terminator, Dark Claymore, Hyperion/variants, Slayer weapon, or any sword.
     */
    fun isQualifyingWeapon(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false

        val name = stack.hoverName.string
        val cleanName = ChatFormatting.stripFormatting(name) ?: name

        // 1. Terminator (bow in vanilla)
        if (cleanName.contains("Terminator", ignoreCase = true)) return true

        // 2. Any Minecraft sword item (e.g. item.minecraft.diamond_sword)
        if (stack.item.descriptionId.contains("sword", ignoreCase = true)) return true

        // 3. Specific named slayer/dungeon weapons that might not be SwordItem (or custom items)
        if (cleanName.contains("Claymore", ignoreCase = true) ||
            cleanName.contains("Hyperion", ignoreCase = true) ||
            cleanName.contains("Valkyrie", ignoreCase = true) ||
            cleanName.contains("Scylla", ignoreCase = true) ||
            cleanName.contains("Astraea", ignoreCase = true) ||
            cleanName.contains("Katana", ignoreCase = true) ||
            cleanName.contains("Falchion", ignoreCase = true) ||
            cleanName.contains("Axe of the Shredded", ignoreCase = true) ||
            cleanName.contains("Scorpion Foil", ignoreCase = true) ||
            cleanName.contains("Recluse Fang", ignoreCase = true) ||
            cleanName.contains("Pooch Sword", ignoreCase = true) ||
            cleanName.contains("Shaman Sword", ignoreCase = true) ||
            cleanName.contains("Dagger", ignoreCase = true)) return true

        // 4. Skyblock NBT id check if present in CustomData
        val customData = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        if (customData != null) {
            val extra = customData.getCompound("ExtraAttributes").getOrNull() ?: customData
            val id = extra.getString("id").getOrNull()?.uppercase() ?: ""
            if (id == "TERMINATOR" ||
                id.contains("CLAYMORE") ||
                id in listOf("HYPERION", "VALKYRIE", "SCYLLA", "ASTRAEA", "NECRON_BLADE") ||
                id.contains("KATANA") ||
                id.contains("FALCHION") ||
                id.contains("SHREDDED") ||
                id.contains("DAGGER") ||
                id.contains("FOIL") ||
                id.contains("SWORD")) {
                return true
            }
        }

        return false
    }
}
