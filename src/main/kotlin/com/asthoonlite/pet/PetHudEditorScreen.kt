package com.asthoonlite.pet

import com.asthoonlite.config.Config
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Drag-to-reposition and scale editor for [PetHudOverlay].
 *
 * Confirmed against real 26.1.2 Mojang-mapped sources via javap:
 * mouse input goes through GuiEventListener's MouseButtonEvent-based methods
 * (not the guessed `Click` record, and not the old raw double/int signature).
 */
class PetHudEditorScreen : Screen(Component.literal("Pet HUD Editor")) {

    private var dragging = false
    private var posX     = Config.petDisplayX
    private var posY     = Config.petDisplayY
    private var scale    = Config.petDisplayScale

    companion object {
        private const val DEFAULT_X     = 4
        private const val DEFAULT_Y     = 4
        private const val DEFAULT_SCALE = 1.0f
    }

    private lateinit var scaleSlider : ScaleSlider
    private lateinit var btnReset    : Button
    private lateinit var btnDone     : Button

    override fun init() {
        val cx = width / 2

        scaleSlider = ScaleSlider(cx - 100, height - 50, 200, 20, scale)
        addRenderableWidget(scaleSlider)

        btnReset = Button.builder(Component.literal("Reset")) {
            posX  = DEFAULT_X
            posY  = DEFAULT_Y
            scale = DEFAULT_SCALE
            scaleSlider.setScale(scale)
        }.bounds(cx - 120, height - 26, 70, 20).build()
        addRenderableWidget(btnReset)

        btnDone = Button.builder(Component.literal("Done")) { save() }
            .bounds(cx + 50, height - 26, 70, 20).build()
        addRenderableWidget(btnDone)
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0x88000000.toInt())
        context.centeredText(
            font,
            "§aDrag the panel to move it. Use the slider to scale.",
            width / 2, 8, 0xFFFFFFFF.toInt()
        )

        val mockPet = PetTracker.current ?: PetTracker.ActivePet(
            name        = "PetName",
            level       = 100,
            rarityColor = 0xFFFFAA00.toInt(),
            display     = "[Lvl 100] PetName"
        )

        val (panelW, panelH) = PetHudOverlay.panelSize(mockPet)
        val scaledW = (panelW * scale).toInt()
        val scaledH = (panelH * scale).toInt()

        val hovering = mouseX in posX..(posX + scaledW) && mouseY in posY..(posY + scaledH)
        if (hovering || dragging) {
            context.fill(posX - 2, posY - 2, posX + scaledW + 2, posY + scaledH + 2, 0x661E90FF.toInt())
        }

        // Temporarily override config to render at our editor position
        val savedX = Config.petDisplayX;  Config.petDisplayX = posX
        val savedY = Config.petDisplayY;  Config.petDisplayY = posY
        val savedS = Config.petDisplayScale; Config.petDisplayScale = scale
        val savedE = Config.petDisplayEnabled; Config.petDisplayEnabled = true
        PetHudOverlay.render(context)
        Config.petDisplayX       = savedX
        Config.petDisplayY       = savedY
        Config.petDisplayScale   = savedS
        Config.petDisplayEnabled = savedE

        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() == 0) {
            val mx = event.x().toInt()
            val my = event.y().toInt()
            val (panelW, panelH) = PetHudOverlay.panelSize()
            val scaledW = (panelW * scale).toInt()
            val scaledH = (panelH * scale).toInt()
            if (mx in posX..(posX + scaledW) && my in posY..(posY + scaledH)) {
                dragging = true
                return true
            }
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (dragging && event.button() == 0) {
            posX = (posX + dx.toInt()).coerceIn(0, width  - 20)
            posY = (posY + dy.toInt()).coerceIn(0, height - 20)
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0) dragging = false
        return super.mouseReleased(event)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun isPauseScreen() = false

    override fun onClose() { save() }

    private fun save() {
        Config.petDisplayX     = posX
        Config.petDisplayY     = posY
        Config.petDisplayScale = scale
        Config.save()
        minecraft?.setScreen(null)
    }

    // ── Scale slider ──────────────────────────────────────────────────────────

    private inner class ScaleSlider(x: Int, y: Int, w: Int, h: Int, initial: Float)
        : AbstractSliderButton(x, y, w, h, Component.empty(), ((initial - 0.5f) / 2.5f).toDouble()) {

        init { updateMessage() }

        fun setScale(v: Float) {
            value = ((v - 0.5f) / 2.5f).toDouble()
            updateMessage()
        }

        override fun updateMessage() {
            val s = 0.5f + (value * 2.5f).toFloat()
            message = Component.literal("Scale: ${"%.1f".format(s)}×")
        }

        override fun applyValue() {
            scale = 0.5f + (value * 2.5f).toFloat()
        }
    }
}
