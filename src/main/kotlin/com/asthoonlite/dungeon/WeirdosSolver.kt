package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import java.util.regex.Pattern

/**
 * Ported from NoammAddons' ThreeWeirdosSolver.kt. The original highlights
 * the *chest* next to the correct NPC using room-relative coordinates from
 * the dungeon map engine (see RoomAlerts' note on why that engine wasn't
 * ported).
 *
 * This version keeps the same chat-parsing/solution logic and announces
 * the correct NPC in chat. It intentionally skips rendering a glowing
 * outline on the NPC: real vanilla entity-glow in 26.1.2 has no public
 * setter (vanilla only exposes the read-only `Entity#isCurrentlyGlowing`
 * check) — NoammAddons implements it via a dedicated Mixin Extras
 * `@ModifyExpressionValue` injection into `Minecraft#shouldEntityAppearGlowing`
 * plus its own `IGlowingEntity` mixin interface. That's a reasonable amount
 * of extra plumbing (and an extra Mixin Extras dependency) for a single
 * cosmetic highlight, so it's left out here — see NoammAddons'
 * MixinMinecraft.java / IGlowingEntity.java if this glow effect is wanted
 * later.
 */
object WeirdosSolver {

    private val npcRegex = Pattern.compile("\\[NPC] (\\w+): (.+)")

    private val solutions = listOf(
        Regex("The reward is not in my chest!"),
        Regex("At least one of them is lying, and the reward is not in .+'s chest\\.?"),
        Regex("My chest doesn't have the reward\\. We are all telling the truth\\.?"),
        Regex("My chest has the reward and I'm telling the truth!"),
        Regex("The reward isn't in any of our chests\\.?"),
        Regex("Both of them are telling the truth\\. Also, .+ has the reward in their chest\\.?"),
    )

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay) onChat(text)
            true
        }
    }

    private fun onChat(component: Component) {
        if (!Config.weirdosSolverEnabled) return
        val raw = ChatFormatting.stripFormatting(component.string) ?: return
        val m = npcRegex.matcher(raw)
        if (!m.matches()) return

        val npcName = m.group(1)
        val statement = m.group(2)
        if (solutions.none { it.matches(statement) }) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        // Confirm the NPC is actually present before announcing it, even
        // though we don't render anything on it directly.
        level.entitiesForRendering()
            .filterIsInstance<ArmorStand>()
            .find { it.name.string.contains(npcName) } ?: return

        mc.player?.sendSystemMessage(
            Component.literal("§d[AsthoonLite] §fCorrect Weirdo: §b$npcName")
        )
    }
}
