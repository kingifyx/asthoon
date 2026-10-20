package com.asthoonlite.hud

import com.asthoonlite.AsthoonLite
import com.asthoonlite.QuietMode
import com.asthoonlite.config.Config
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

        val font = mc.font
        val scale = Config.speedBarScale.coerceIn(0.5f, 3.0f)

        val pose = context.pose()
        pose.pushMatrix()
        val posX = if (Config.speedBarX >= 0) Config.speedBarX.toFloat() else (mc.window.guiScaledWidth / 2f - 30f)
        val posY = if (Config.speedBarY >= 0) Config.speedBarY.toFloat() else (mc.window.guiScaledHeight - 48f)
        pose.translate(posX, posY)
        pose.scale(scale, scale)

        // Clean display of true speed without background box or cap
        val label = "§b✦ §f$speedVal"
        context.text(font, label, 0, 0, 0xFFFFFFFF.toInt())

        pose.popMatrix()
    }
}
