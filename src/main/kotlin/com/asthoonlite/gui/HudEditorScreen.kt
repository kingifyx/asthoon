package com.asthoonlite.gui

import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.DragonPhase
import com.asthoonlite.dungeon.DungeonTimers
import com.asthoonlite.dungeon.MaskDisplay
import com.asthoonlite.dungeon.TerminalProgressHud
import com.asthoonlite.hud.AlertHud
import com.asthoonlite.mining.PickaxeAbilityTimer
import com.asthoonlite.pet.PetHudOverlay
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Universal HUD Position & Scale Editor opened via `/asl gui`.
 * Allows dragging and mouse-wheel scaling for all HUD elements
 * EXCEPT Dungeon Map (which has its own dedicated editor with full map preview).
 */
class HudEditorScreen : Screen(Component.literal("AsthoonLite HUD Editor")) {

    private enum class HudElementTarget(val label: String) {
        PET("Pet Display"),
        ALERT("Alert HUD (Cleared/Secrets/Ability)"),
        MASK("Mask Display"),
        DRAGON("Dragon Phase HUD"),
        DUNGEON_TIMERS("Dungeon Timers"),
        PICKAXE_TIMER("Pickaxe Ability Timer"),
        TERMINAL_PROGRESS("Terminal Progress HUD")
    }

    private data class ElementBox(
        val target: HudElementTarget,
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int
    ) {
        fun contains(px: Double, py: Double): Boolean =
            px >= x && px <= x + width && py >= y && py <= y + height
    }

    private var activeDrag: ElementBox? = null
    private var dragOffsetX = 0.0
    private var dragOffsetY = 0.0

    private lateinit var btnDone: Button
    private lateinit var btnReset: Button

    override fun isPauseScreen() = false

    override fun init() {
        val cx = width / 2
        btnReset = Button.builder(Component.literal("Reset Defaults")) {
            resetAllToDefaults()
        }.bounds(cx - 110, height - 26, 100, 20).build()
        addRenderableWidget(btnReset)

        btnDone = Button.builder(Component.literal("Done")) {
            Config.save()
            onClose()
        }.bounds(cx + 10, height - 26, 100, 20).build()
        addRenderableWidget(btnDone)
    }

    private fun resetAllToDefaults() {
        Config.petDisplayX = 4
        Config.petDisplayY = 4
        Config.petDisplayScale = 1.0f

        Config.alertHudX = -1
        Config.alertHudY = -1
        Config.alertHudScale = 2.5f

        Config.maskHudX = 4
        Config.maskHudY = 240
        Config.maskHudScale = 1.0f

        Config.dragonHudX = 4
        Config.dragonHudY = 170
        Config.dragonHudScale = 1.0f

        Config.dungeonTimerX = 4
        Config.dungeonTimerY = 170
        Config.dungeonTimerScale = 1.0f

        Config.pickaxeTimerX = 4
        Config.pickaxeTimerY = 40
        Config.pickaxeTimerScale = 1.0f

        Config.termProgressHudX = -1
        Config.termProgressHudY = -1
        Config.termProgressHudScale = 1.0f

        Config.save()
    }

    private fun getBoxes(): List<ElementBox> {
        val list = mutableListOf<ElementBox>()

        // 1. Pet HUD
        val petW = (100 * Config.petDisplayScale).toInt()
        val petH = (28 * Config.petDisplayScale).toInt()
        list.add(ElementBox(HudElementTarget.PET, Config.petDisplayX, Config.petDisplayY, petW, petH))

        // 2. Alert HUD
        val alertW = (140 * (Config.alertHudScale / 2.5f)).toInt()
        val alertH = (24 * (Config.alertHudScale / 2.5f)).toInt()
        val ax = if (Config.alertHudX >= 0) Config.alertHudX else (width / 2 - alertW / 2)
        val ay = if (Config.alertHudY >= 0) Config.alertHudY else (height / 2 - (height * 0.056f).toInt() - alertH / 2)
        list.add(ElementBox(HudElementTarget.ALERT, ax, ay, alertW, alertH))

        // 3. Mask Display
        val maskW = (90 * Config.maskHudScale).toInt()
        val maskH = (32 * Config.maskHudScale).toInt()
        list.add(ElementBox(HudElementTarget.MASK, Config.maskHudX, Config.maskHudY, maskW, maskH))

        // 4. Dragon Phase HUD
        val dragonW = (130 * Config.dragonHudScale).toInt()
        val dragonH = (44 * Config.dragonHudScale).toInt()
        list.add(ElementBox(HudElementTarget.DRAGON, Config.dragonHudX, Config.dragonHudY, dragonW, dragonH))

        // 5. Dungeon Timers
        val timerW = (110 * Config.dungeonTimerScale).toInt()
        val timerH = (50 * Config.dungeonTimerScale).toInt()
        list.add(ElementBox(HudElementTarget.DUNGEON_TIMERS, Config.dungeonTimerX, Config.dungeonTimerY, timerW, timerH))

        // 6. Pickaxe Ability Timer
        val pickW = (112 * Config.pickaxeTimerScale).toInt()
        val pickH = (24 * Config.pickaxeTimerScale).toInt()
        list.add(ElementBox(HudElementTarget.PICKAXE_TIMER, Config.pickaxeTimerX, Config.pickaxeTimerY, pickW, pickH))

        // 7. Terminal Progress HUD
        val tpW = (120 * Config.termProgressHudScale).toInt()
        val tpH = (22 * Config.termProgressHudScale).toInt()
        val tpx = if (Config.termProgressHudX >= 0) Config.termProgressHudX else (width / 2 - tpW / 2)
        val tpy = if (Config.termProgressHudY >= 0) Config.termProgressHudY else (height / 10)
        list.add(ElementBox(HudElementTarget.TERMINAL_PROGRESS, tpx, tpy, tpW, tpH))

        return list
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Semi-transparent dark background
        context.fill(0, 0, width, height, 0x77000000.toInt())

        context.centeredText(font, "§bAsthoonLite HUD Editor §7— Drag any box to move • Mouse Wheel over box to scale", width / 2, 8, -1)
        context.centeredText(font, "§8(Dungeon map is excluded here; use /asl -> Map -> Edit Map Position)", width / 2, 20, -1)

        val boxes = getBoxes()
        for (box in boxes) {
            val hovered = box.contains(mouseX.toDouble(), mouseY.toDouble()) || activeDrag?.target == box.target
            val borderColor = if (hovered) 0xFF00FFAA.toInt() else 0xFF1E90FF.toInt()
            val fillColor = if (hovered) 0x6600AA77.toInt() else 0x440D1F35.toInt()

            context.fill(box.x, box.y, box.x + box.width, box.y + box.height, fillColor)
            // Border outline
            context.fill(box.x, box.y, box.x + box.width, box.y + 1, borderColor)
            context.fill(box.x, box.y + box.height - 1, box.x + box.width, box.y + box.height, borderColor)
            context.fill(box.x, box.y, box.x + 1, box.y + box.height, borderColor)
            context.fill(box.x + box.width - 1, box.y, box.x + box.width, box.y + box.height, borderColor)

            // Label
            val scaleStr = when (box.target) {
                HudElementTarget.PET -> String.format("%.1fx", Config.petDisplayScale)
                HudElementTarget.ALERT -> String.format("%.1fx", Config.alertHudScale)
                HudElementTarget.MASK -> String.format("%.1fx", Config.maskHudScale)
                HudElementTarget.DRAGON -> String.format("%.1fx", Config.dragonHudScale)
                HudElementTarget.DUNGEON_TIMERS -> String.format("%.1fx", Config.dungeonTimerScale)
                HudElementTarget.PICKAXE_TIMER -> String.format("%.1fx", Config.pickaxeTimerScale)
                HudElementTarget.TERMINAL_PROGRESS -> String.format("%.1fx", Config.termProgressHudScale)
            }
            context.text(font, "${box.target.label} ($scaleStr)", box.x + 3, box.y + 3, 0xFFFFFFFF.toInt())
        }

        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            val boxes = getBoxes()
            for (box in boxes.reversed()) {
                if (box.contains(event.x(), event.y())) {
                    activeDrag = box
                    dragOffsetX = event.x() - box.x
                    dragOffsetY = event.y() - box.y
                    return true
                }
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val drag = activeDrag
        if (drag != null && event.button() == 0) {
            val newX = (event.x() - dragOffsetX).toInt().coerceIn(0, width - drag.width)
            val newY = (event.y() - dragOffsetY).toInt().coerceIn(0, height - drag.height)

            when (drag.target) {
                HudElementTarget.PET -> { Config.petDisplayX = newX; Config.petDisplayY = newY }
                HudElementTarget.ALERT -> { Config.alertHudX = newX; Config.alertHudY = newY }
                HudElementTarget.MASK -> { Config.maskHudX = newX; Config.maskHudY = newY }
                HudElementTarget.DRAGON -> { Config.dragonHudX = newX; Config.dragonHudY = newY }
                HudElementTarget.DUNGEON_TIMERS -> { Config.dungeonTimerX = newX; Config.dungeonTimerY = newY }
                HudElementTarget.PICKAXE_TIMER -> { Config.pickaxeTimerX = newX; Config.pickaxeTimerY = newY }
                HudElementTarget.TERMINAL_PROGRESS -> { Config.termProgressHudX = newX; Config.termProgressHudY = newY }
            }
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val boxes = getBoxes()
        for (box in boxes) {
            if (box.contains(mouseX, mouseY)) {
                val step = (scrollY * 0.1).toFloat()
                when (box.target) {
                    HudElementTarget.PET -> Config.petDisplayScale = (Config.petDisplayScale + step).coerceIn(0.5f, 3.0f)
                    HudElementTarget.ALERT -> Config.alertHudScale = (Config.alertHudScale + step * 2f).coerceIn(0.5f, 5.0f)
                    HudElementTarget.MASK -> Config.maskHudScale = (Config.maskHudScale + step).coerceIn(0.5f, 3.0f)
                    HudElementTarget.DRAGON -> Config.dragonHudScale = (Config.dragonHudScale + step).coerceIn(0.5f, 3.0f)
                    HudElementTarget.DUNGEON_TIMERS -> Config.dungeonTimerScale = (Config.dungeonTimerScale + step).coerceIn(0.5f, 3.0f)
                    HudElementTarget.PICKAXE_TIMER -> Config.pickaxeTimerScale = (Config.pickaxeTimerScale + step).coerceIn(0.5f, 3.0f)
                    HudElementTarget.TERMINAL_PROGRESS -> Config.termProgressHudScale = (Config.termProgressHudScale + step).coerceIn(0.5f, 3.0f)
                }
                Config.save()
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (activeDrag != null && event.button() == 0) {
            activeDrag = null
            Config.save()
            return true
        }
        return super.mouseReleased(event)
    }

    override fun onClose() {
        Config.save()
        minecraft.setScreen(null)
    }
}
