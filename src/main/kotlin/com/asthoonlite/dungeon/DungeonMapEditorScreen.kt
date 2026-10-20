package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.overlay.MapOverlayWindow
import com.asthoonlite.render.HudMapCanvas
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/** MouseButtonEvent signatures verified against Minecraft 26.1.2 GuiEventListener. */
class DungeonMapEditorScreen : Screen(Component.literal("Dungeon Map Editor")) {
    private var dragging = false
    private var grabX = 0.0
    private var grabY = 0.0

    private var resizing = false
    private var initialScale = Config.dungeonMapScale
    private var resizeStartX = 0.0
    private var resizeStartY = 0.0

    override fun init() {
        Config.dungeonMapEditMode = true
        MapOverlayWindow.hide()
        val rect = DungeonMap.screenRect(width, height)
        Config.dungeonMapX = rect.x
        Config.dungeonMapY = rect.y
        addRenderableWidget(Button.builder(Component.literal("Done")) { onClose() }
            .bounds(width / 2 - 40, height - 26, 80, 20).build())
    }

    override fun isPauseScreen() = false

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        DungeonMap.preview(HudMapCanvas(context), width, height)
        val rect = DungeonMap.screenRect(width, height)
        // Draw resize handle in bottom-right corner
        val handleSize = 10
        val hx = rect.x + rect.size - handleSize
        val hy = rect.y + rect.size - handleSize
        context.fill(hx, hy, rect.x + rect.size, rect.y + rect.size, 0xFF55FFFF.toInt())
        context.fill(hx + 2, hy + 2, rect.x + rect.size - 2, rect.y + rect.size - 2, 0xFF0D1F35.toInt())

        context.centeredText(font, "Drag to move • Bottom-right handle or Scroll wheel to resize (Scale: ${String.format("%.2f", Config.dungeonMapScale)}x)", width / 2, 8, -1)
        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val rect = DungeonMap.screenRect(width, height)
        if (event.button() == 0) {
            val handleSize = 14
            val inHandle = event.x() >= rect.x + rect.size - handleSize && event.x() <= rect.x + rect.size + 4 &&
                event.y() >= rect.y + rect.size - handleSize && event.y() <= rect.y + rect.size + 4
            if (inHandle) {
                resizing = true
                initialScale = Config.dungeonMapScale
                resizeStartX = event.x()
                resizeStartY = event.y()
                return true
            }
            if (rect.contains(event.x(), event.y())) {
                dragging = true
                grabX = event.x() - rect.x
                grabY = event.y() - rect.y
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (resizing && event.button() == 0) {
            val delta = ((event.x() - resizeStartX) + (event.y() - resizeStartY)) / 100.0
            val newScale = (initialScale + delta.toFloat()).coerceIn(1.0f, 6.0f)
            Config.dungeonMapScale = newScale
            val rect = DungeonMap.screenRect(width, height)
            Config.dungeonMapX = rect.x
            Config.dungeonMapY = rect.y
            return true
        }
        if (!dragging || event.button() != 0) return super.mouseDragged(event, dx, dy)
        val rect = DungeonMap.clampRect((event.x() - grabX).toInt(), (event.y() - grabY).toInt(),
            DungeonMap.screenRect(width, height).size, width, height)
        Config.dungeonMapX = rect.x
        Config.dungeonMapY = rect.y
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val rect = DungeonMap.screenRect(width, height)
        if (rect.contains(mouseX, mouseY)) {
            val newScale = (Config.dungeonMapScale + (scrollY * 0.1f).toFloat()).coerceIn(1.0f, 6.0f)
            Config.dungeonMapScale = newScale
            Config.save()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (resizing && event.button() == 0) {
            resizing = false
            Config.save()
            return true
        }
        if (!dragging || event.button() != 0) return super.mouseReleased(event)
        dragging = false
        Config.save()
        return true
    }

    override fun removed() {
        Config.dungeonMapEditMode = false // Saves position even if another screen replaces the editor mid-drag.
    }

    override fun onClose() { minecraft.setScreen(null) }
}
