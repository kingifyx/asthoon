package com.asthoonlite.dungeon

import com.asthoonlite.AsthoonLite
import com.asthoonlite.config.Config
import com.google.gson.GsonBuilder
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.io.File

/**
 * Ported from NoammAddons' QuizSolver.kt (the chat-driven half — the
 * block-highlight half needed room-relative coordinates from the dungeon
 * map engine, which wasn't in scope here, see RoomAlerts' note).
 *
 * Detects the "Trivia! [Oruo the Omniscient]" question text and prints the
 * matching answer straight to chat. Ships with a small starter answer set
 * you can extend yourself at
 * .minecraft/config/asthoonlite/quizSolutions.json (same "question snippet
 * → list of correct-answer strings" shape noamm uses), since the full
 * answer bank wasn't bundled in the source you gave me.
 */
object QuizSolver {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val solutionsFile = File(
        FabricLoader.getInstance().configDir.resolve(AsthoonLite.MOD_ID).toFile(),
        "quizSolutions.json"
    )

    // Starter set — extend via quizSolutions.json. Keys are matched with
    // String#contains against the question line.
    private val defaultSolutions = mapOf(
        "How many unique minions are there in the" to listOf("__EDIT_ME__ see quizSolutions.json"),
        "What year is it in Skyblock" to listOf("__COMPUTED__skyblock_year")
    )

    private var solutions: Map<String, List<String>> = emptyMap()
    private var triviaAnswers: List<String>? = null

    fun register() {
        loadSolutions()

        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay) onChat(text)
            true
        }
    }

    private fun loadSolutions() {
        runCatching {
            if (!solutionsFile.exists()) {
                solutionsFile.parentFile.mkdirs()
                solutionsFile.writeText(gson.toJson(defaultSolutions))
            }
            val type = object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
            solutions = gson.fromJson(solutionsFile.readText(), type) ?: defaultSolutions
        }.onFailure {
            AsthoonLite.LOGGER.warn("[AsthoonLite] Failed to load quizSolutions.json, using defaults", it)
            solutions = defaultSolutions
        }
    }

    private fun onChat(component: Component) {
        if (!Config.quizSolverEnabled) return
        val message = ChatFormatting.stripFormatting(component.string) ?: return
        val trimmed = message.trim()

        if (trimmed == "What SkyBlock year is it?") {
            val year = (((System.currentTimeMillis() / 1000) - 1560276000) / 446400).toInt() + 1
            triviaAnswers = listOf("Year $year")
            return
        }

        val newAnswers = solutions.entries.find { message.contains(it.key) }?.value
        if (newAnswers != null) {
            triviaAnswers = newAnswers
            return
        }

        if (trimmed.startsWith("ⓐ") || trimmed.startsWith("ⓑ") || trimmed.startsWith("ⓒ")) {
            val answers = triviaAnswers ?: return
            val matched = answers.firstOrNull { message.endsWith(it) } ?: return
            val mc = Minecraft.getInstance()
            mc.player?.sendSystemMessage(
                Component.literal("§d[AsthoonLite Quiz] §fCorrect answer: §b${trimmed[0]} $matched")
            )
        }
    }
}
