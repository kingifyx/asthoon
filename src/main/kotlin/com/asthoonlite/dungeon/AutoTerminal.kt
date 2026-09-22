package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Advanced automatic terminal clicker with RSM-compatible preset support,
 * safe Gaussian random delay intervals, break threshold protection,
 * and Melody skip / party announce features.
 */
object AutoTerminal {
    private var lastClickAt = 0L
    private var terminalOpenedAt = 0L
    private var suppressReopenUntil = 0L
    private var lastWindowId = -1
    private var lastMelodyWindowId = -1
    private var firstClickPending = true
    private var currentClickDelayMs = 0L
    private val melodySkipQueue = ArrayDeque<Int>()

    private const val RUBIX_REPEAT_GUARD_MS = 70L

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
    }

    private fun tick() {
        if (!Config.autoTerminalEnabled || !DungeonContext.inDungeon) {
            reset()
            return
        }

        val mc = Minecraft.getInstance()
        val screen = mc.screen as? AbstractContainerScreen<*> ?: run { reset(); return }
        val title = screen.title.string
        if (suppressReopenUntil > System.currentTimeMillis() && typeFor(title) != null) {
            mc.setScreen(null)
            return
        }
        val type = typeFor(title) ?: run { reset(); return }
        if (!isTypeEnabled(type)) return

        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val now = System.currentTimeMillis()
        val windowId = screen.menu.containerId

        // New terminal window opened: engage first-click delay once from opening
        if (windowId != lastWindowId) {
            lastWindowId = windowId
            firstClickPending = true
            terminalOpenedAt = now
            lastClickAt = now
            currentClickDelayMs = nextFirstClickDelayMs()
            melodySkipQueue.clear()

            // Melody party announcement
            if (type == Type.MELODY && Config.autoTerminalAnnounceMelody && windowId != lastMelodyWindowId) {
                lastMelodyWindowId = windowId
                val msg = Config.autoTerminalMelodyMessage.trim()
                if (msg.isNotEmpty()) {
                    mc.player?.connection?.sendCommand("pc $msg")
                }
            }
        }

        // Delay timer check: first click delay applies ONLY to the first click from opening
        if (firstClickPending) {
            if (now - terminalOpenedAt < currentClickDelayMs) return
        } else {
            if (now - lastClickAt < currentClickDelayMs) return
        }

        // Process queued Melody skip clicks first if available
        if (melodySkipQueue.isNotEmpty()) {
            val nextSlot = melodySkipQueue.removeFirst()
            gameMode.handleContainerInput(windowId, nextSlot, 0, ContainerInput.PICKUP, player)
            lastClickAt = now
            lastSlot = nextSlot
            currentClickDelayMs = 40L // Rapid safe skip click
            return
        }

        val slots = screen.menu.slots
        val size = type.slotCount
        if (slots.size < size) return
        val items = slots.take(size).map { it.item }
        val click = nextClick(type, title, items) ?: return

        // Rubix repeat guard
        if (click.slot == lastSlot && type == Type.RUBIX && now - lastClickAt < RUBIX_REPEAT_GUARD_MS) return

        val button = if (click.button == 0) 2 else click.button
        val input = if (click.button == 0) ContainerInput.CLONE else ContainerInput.PICKUP
        gameMode.handleContainerInput(windowId, click.slot, button, input, player)

        lastClickAt = now
        lastSlot = click.slot
        firstClickPending = false
        currentClickDelayMs = nextClickDelayMs()
    }

    private var lastSlot = -1

    private data class Click(val slot: Int, val button: Int = 0)
    private enum class Type(val slotCount: Int) { COLORS(54), MELODY(54), NUMBERS(36), REDGREEN(45), RUBIX(45), STARTWITH(45) }

    fun isTerminalTitle(title: String): Boolean = typeFor(title) != null

    private fun isTypeEnabled(type: Type): Boolean = when (type) {
        Type.COLORS -> Config.autoTermColors
        Type.MELODY -> Config.autoTermMelody
        Type.NUMBERS -> Config.autoTermNumbers
        Type.REDGREEN -> Config.autoTermRedGreen
        Type.RUBIX -> Config.autoTermRubix
        Type.STARTWITH -> Config.autoTermStartsWith
    }

    private fun typeFor(title: String): Type? = when {
        title.startsWith("Select all the ", true) -> Type.COLORS
        title.startsWith("Click the button on time!", true) -> Type.MELODY
        title.startsWith("Click in order!", true) -> Type.NUMBERS
        title.startsWith("Correct all the panes!", true) -> Type.REDGREEN
        title.startsWith("Change all to same color!", true) -> Type.RUBIX
        title.startsWith("What starts with:", true) -> Type.STARTWITH
        else -> null
    }

    private fun nextClick(type: Type, title: String, items: List<ItemStack>): Click? = when (type) {
        Type.NUMBERS -> items.mapIndexedNotNull { i, stack ->
            if (stack.`is`(Items.RED_STAINED_GLASS_PANE)) i to stack.count else null
        }
            .sortedBy { it.second }
            .take(NUMBER_TERM_COUNT)
            .minByOrNull { it.second }
            ?.let { Click(it.first) }

        Type.REDGREEN -> items.indexOfFirst { it.`is`(Items.RED_STAINED_GLASS_PANE) }
            .takeIf { it >= 0 }?.let { Click(it) }

        Type.COLORS -> {
            val match = Regex("^Select all the (.+) items!$", RegexOption.IGNORE_CASE).matchEntire(title)
                ?: return null
            val wanted = normalizeColor(match.groupValues[1])
            items.indexOfFirst { stack ->
                if (stack.isEmpty || stack.`is`(Items.BLACK_STAINED_GLASS_PANE)) return@indexOfFirst false
                val name = ChatFormatting.stripFormatting(stack.hoverName.string)?.lowercase() ?: return@indexOfFirst false
                name.startsWith(wanted) && stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != true
            }.takeIf { it >= 0 }?.let { Click(it) }
        }

        Type.STARTWITH -> {
            val match = Regex("^What starts with: '(.+)'\\?$", RegexOption.IGNORE_CASE).matchEntire(title)
                ?: return null
            val wanted = match.groupValues[1].lowercase()
            items.indexOfFirst { stack ->
                if (stack.isEmpty) return@indexOfFirst false
                val name = ChatFormatting.stripFormatting(stack.hoverName.string)?.lowercase() ?: return@indexOfFirst false
                name.startsWith(wanted) && stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != true
            }.takeIf { it >= 0 }?.let { Click(it) }
        }

        Type.RUBIX -> rubixClick(items)
        Type.MELODY -> melodyClick(items)
    }

    private fun rubixClick(items: List<ItemStack>): Click? {
        val allowed = intArrayOf(12, 13, 14, 21, 22, 23, 30, 31, 32)
        val colors = listOf(
            Items.BLUE_STAINED_GLASS_PANE,
            Items.RED_STAINED_GLASS_PANE,
            Items.ORANGE_STAINED_GLASS_PANE,
            Items.YELLOW_STAINED_GLASS_PANE,
            Items.GREEN_STAINED_GLASS_PANE
        )
        val panes = allowed.toList().mapNotNull { slot ->
            val item = items.getOrNull(slot)?.item ?: return@mapNotNull null
            val idx = colors.indexOf(item)
            if (idx >= 0) slot to idx else null
        }
        if (panes.isEmpty()) return null

        val costs = IntArray(5)
        for (target in 0..4) for ((_, current) in panes) {
            val dist = abs(target - current)
            costs[target] += if (dist > 2) 5 - dist else dist
        }
        val target = costs.indices.minByOrNull { costs[it] } ?: return null
        val mismatch = panes.firstOrNull { (_, current) -> current != target } ?: return null
        val current = mismatch.second
        var diff = target - current
        if (diff > 2) diff -= 5
        if (diff < -2) diff += 5
        return when {
            diff > 0 -> Click(mismatch.first, 0)
            diff < 0 -> Click(mismatch.first, 1)
            else -> null
        }
    }

    private fun melodyClick(items: List<ItemStack>): Click? {
        val magenta = items.indexOfFirst { it.`is`(Items.MAGENTA_STAINED_GLASS_PANE) }
        val lime = items.indexOfFirst { it.`is`(Items.LIME_STAINED_GLASS_PANE) }
        if (magenta < 0 || lime < 0) return null
        val correct = (magenta % 9) - 1
        val current = (lime % 9) - 1
        val buttonRow = floor(lime / 9.0).toInt() - 1
        if (current != correct || buttonRow !in 0..3) return null

        val clickedSlot = buttonRow * 9 + 16

        // Melody Skip feature
        if (Config.autoTerminalMelodySkip) {
            val skipAllowed = !(buttonRow == 0 && Config.autoTerminalDontSkipFirst)
            if (skipAllowed && buttonRow < 3) {
                melodySkipQueue.clear()
                for (r in (buttonRow + 1)..3) {
                    melodySkipQueue.add(r * 9 + 16)
                }
            }
        }

        return Click(clickedSlot)
    }

    private const val NUMBER_TERM_COUNT = 10

    private fun gaussianRandom(minimum: Int, maximum: Int): Int {
        val minValue = min(minimum, maximum)
        val maxValue = max(minimum, maximum)
        if (minValue == maxValue) return minValue

        val u1 = 1.0 - Random.nextDouble()
        val u2 = 1.0 - Random.nextDouble()
        val gaussian = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)

        val mean = minValue + (maxValue - minValue) / 2.0
        val stdDev = (maxValue - minValue) / 6.0
        val result = gaussian * stdDev + mean

        return result.coerceIn(minValue.toDouble(), maxValue.toDouble()).toInt()
    }

    private fun nextFirstClickDelayMs(): Long {
        val base = Config.autoTerminalFirstClickDelayMs.coerceAtLeast(0)
        if (!Config.autoTerminalRandomDelay) return base.toLong()
        val min = (base - 30).coerceAtLeast(50)
        val max = base + 30
        return gaussianRandom(min, max).toLong()
    }

    private fun nextClickDelayMs(): Long {
        if (!Config.autoTerminalRandomDelay) {
            return Config.autoTerminalClickDelayMs.coerceAtLeast(0).toLong()
        }

        val target = Config.autoTerminalClickDelayMs.coerceAtLeast(0)
        val minDelay = Config.autoTerminalMinRandomDelayMs.coerceAtLeast(0)
        val maxDelay = Config.autoTerminalMaxRandomDelayMs.coerceAtLeast(0)

        val lower = if (minDelay > 0 && maxDelay >= minDelay) minDelay else (target - 20).coerceAtLeast(50)
        val upper = if (maxDelay > 0 && maxDelay >= lower) maxDelay else (target + 20).coerceAtLeast(lower)

        return gaussianRandom(lower, upper).toLong()
    }

    private fun normalizeColor(value: String): String = when (value.lowercase()) {
        "light gray" -> "silver"
        "wool" -> "white"
        "bone" -> "white"
        "ink" -> "black"
        "lapis" -> "blue"
        "cocoa" -> "brown"
        "dandelion" -> "yellow"
        "rose" -> "red"
        "cactus" -> "green"
        else -> value.lowercase()
    }

    fun onEscape() {
        suppressReopenUntil = System.currentTimeMillis() + 750L
        reset()
    }

    private fun reset() {
        lastClickAt = 0L
        terminalOpenedAt = 0L
        lastSlot = -1
        lastWindowId = -1
        firstClickPending = true
        currentClickDelayMs = 0L
        melodySkipQueue.clear()
    }
}
