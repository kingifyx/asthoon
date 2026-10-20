package com.asthoonlite.hud

import com.asthoonlite.AsthoonLite
import com.asthoonlite.QuietMode
import com.asthoonlite.config.Config
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ai.attributes.Attributes

object SpeedBarHud : HudElement {

    fun register() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(AsthoonLite.MOD_ID, "speed_bar_hud"),
            this
        )
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (QuietMode.suppressing() || !Config.speedBarEnabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        // In Hypixel Skyblock, speed = movementSpeed * 1000
        val speedVal = try {
            (player.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1000.0 + 0.5).toInt()
        } catch (_: Throwable) { 100 }

        val maxSpeed = if (speedVal > 400) 500 else 400
        val pct = (speedVal.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)

        val font = mc.font
        val scale = Config.speedBarScale.coerceIn(0.5f, 3.0f)
        val barW = 120
        val barH = 12

        val pose = context.pose()
        pose.pushMatrix()
        pose.translate(Config.speedBarX.toFloat(), Config.speedBarY.toFloat())
        pose.scale(scale, scale)

        // Dark background
        fillRoundedRect(context, 0, 0, barW, barH, 0xCC090B10.toInt(), 3)
        outlineRoundedRect(context, 0, 0, barW, barH, 0xFF1E90FF.toInt(), 3)

        // Filled progress bar
        val fillW = (barW * pct).toInt()
        if (fillW > 0) {
            val fillColor = if (speedVal >= maxSpeed) 0xFF00E676.toInt() else 0xFF00B0FF.toInt()
            fillRoundedRect(context, 1, 1, fillW, barH - 1, fillColor, 2)
        }

        // Text centered on bar
        val label = "$speedVal / $maxSpeed ✦"
        val textX = (barW - font.width(label)) / 2
        val textY = (barH - font.lineHeight) / 2 + 1
        context.text(font, label, textX, textY, 0xFFFFFFFF.toInt())

        pose.popMatrix()
    }
}
