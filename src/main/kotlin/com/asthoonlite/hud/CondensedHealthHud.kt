package com.asthoonlite.hud

import com.asthoonlite.AsthoonLite
import com.asthoonlite.QuietMode
import com.asthoonlite.config.Config
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.ChatFormatting
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.resources.Identifier
import java.util.regex.Pattern

object CondensedHealthHud : HudElement {

    private val hpPattern = Pattern.compile("([0-9,]+)/([0-9,]+)❤")
    private var sbCurrentHp = -1
    private var sbMaxHp = -1
    private var lastHpUpdate = 0L

    fun register() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(AsthoonLite.MOD_ID, "condensed_health"), this
        )
    }

    fun onActionBarPacket(packet: ClientboundSetActionBarTextPacket) {
        val clean = ChatFormatting.stripFormatting(packet.text().string) ?: return
        val matcher = hpPattern.matcher(clean)
        if (matcher.find()) {
            val cur = matcher.group(1).replace(",", "").toIntOrNull()
            val max = matcher.group(2).replace(",", "").toIntOrNull()
            if (cur != null && max != null) {
                sbCurrentHp = cur
                sbMaxHp = max
                lastHpUpdate = System.currentTimeMillis()
            }
        }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (QuietMode.suppressing() || !Config.condensedHealthBar) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        val now = System.currentTimeMillis()
        val hasRecentSbHp = (now - lastHpUpdate < 3000L) && sbCurrentHp >= 0 && sbMaxHp > 0

        val currentHp = if (hasRecentSbHp) sbCurrentHp else player.health.toInt()
        val maxHp = if (hasRecentSbHp) sbMaxHp else player.maxHealth.toInt()
        val ratio = if (maxHp > 0) (currentHp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f) else 1f
        val percent = (ratio * 100f).toInt()

        val window = mc.window
        val width = 120
        val height = 12

        val baseX = if (Config.condensedHealthX >= 0) Config.condensedHealthX else (window.guiScaledWidth / 2 - width / 2)
        val baseY = if (Config.condensedHealthY >= 0) Config.condensedHealthY else (window.guiScaledHeight - 48)

        val scale = Config.condensedHealthScale.coerceIn(0.5f, 3.0f)
        val pose = context.pose()
        pose.pushMatrix()
        pose.translate(baseX.toFloat(), baseY.toFloat())
        pose.scale(scale, scale)

        // Background
        fillRoundedRect(context, 0, 0, width, height, 0xEE090B10.toInt(), 3)
        outlineRoundedRect(context, 0, 0, width, height, 0xFF1E90FF.toInt(), 3)

        // Health fill
        val fillWidth = (width * ratio).toInt()
        if (fillWidth > 2) {
            val fillColor = if (percent > 50) 0xFF43A047.toInt() else if (percent > 25) 0xFFFFA000.toInt() else 0xFFE53935.toInt()
            fillRoundedRect(context, 1, 1, fillWidth - 1, height - 1, fillColor, 2)
        }

        // Text
        val font = mc.font
        val text = "§f$currentHp/$maxHp §c❤ §7($percent%)"
        val textW = font.width(text)
        val textX = (width - textW) / 2
        val textY = (height - font.lineHeight) / 2 + 1
        context.text(font, text, textX, textY, 0xFFFFFFFF.toInt())

        pose.popMatrix()
    }
}
