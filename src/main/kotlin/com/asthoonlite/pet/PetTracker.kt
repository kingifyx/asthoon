package com.asthoonlite.pet

import com.asthoonlite.AsthoonLite
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.Slot
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import java.util.regex.Pattern

/**
 * Tracks the currently active Hypixel Skyblock pet.
 *
 * Detection sources (in priority order):
 * 1. Chat: "You summoned your X!" / "You despawned your X!" — the real
 *    source of truth. Confirmed against devonian's PetDisplay.kt
 *    (`equippedPetRegex` / `despawnedPetRegex`): these fire the instant a
 *    pet is (de)summoned regardless of whether any menu is open, which is
 *    exactly the "instant, works everywhere" behavior menu-scanning can't
 *    give — a pet dying/despawning in combat with the Pets menu closed
 *    previously never updated `current` until the menu was reopened.
 * 2. Auto-pet chat messages ("Autopet equipped your [Lvl X] PetName!")
 * 3. Clicking a pet in the Pets GUI (equip / despawn) — instant optimistic
 *    update while the click's actual server round-trip is in flight.
 * 4. Opening the Pets GUI and finding the pet with "Click to despawn!"
 *    lore — corrective fallback only (catches anything the above missed).
 */
object PetTracker {

    // ── Rarity colours (§x prefixes used in pet item names) ──────────────────
    private val RARITY_PREFIXES = mapOf(
        "§f" to "Common",
        "§a" to "Uncommon",
        "§9" to "Rare",
        "§5" to "Epic",
        "§6" to "Legendary",
        "§d" to "Mythic"
    )

    // Matches "[Lvl 100] Bee" or "[Lvl 200] Ender Dragon ✦"
    private val PET_NAME_PATTERN: Pattern =
        Pattern.compile("^\\[Lvl (\\d+)] (.+?)(?:\\s*✦)?$")

    // Matches "Autopet equipped your [Lvl 100] Bee ✦! VIEW RULE" (chat)
    private val AUTOPET_PATTERN: Pattern =
        Pattern.compile("^Autopet equipped your \\[Lvl (\\d+)] (.+?)(?:\\s*✦)?! VIEW RULE$")

    // Matches "You summoned your Bee!" / "You summoned your Bee ✦!" (chat).
    // Ported from devonian's PetDisplay.equippedPetRegex. No level is given
    // in this message, so `current` keeps the previous level if the name
    // matches (see onChatMessage), and falls back to level -1 otherwise.
    private val SUMMON_PATTERN: Pattern =
        Pattern.compile("^You summoned your (.+?)(?:\\s*✦)?!$")

    // Matches "You despawned your Bee!" (chat).
    // Ported from devonian's PetDisplay.despawnedPetRegex.
    private val DESPAWN_PATTERN: Pattern =
        Pattern.compile("^You despawned your (.+?)(?:\\s*✦)?!$")

    // ── Active pet state ──────────────────────────────────────────────────────
    data class ActivePet(
        val name: String,       // e.g. "Bee"
        val level: Int,         // e.g. 100
        val rarityColor: Int,   // ARGB  (0 = unknown)
        val display: String     // e.g. "[Lvl 100] Bee"
    )

    @Volatile var current: ActivePet? = null
        private set

    // Prevent the screen-tick scan from immediately stomping the optimistic
    // client-side update `handleSlotClick` just made, while the server
    // round-trips the actual equip/despawn.
    //
    // This USED to be a plain `Boolean` that stayed `true` until the Pets
    // screen was reopened (BEFORE_INIT resets it). That's the exact bug
    // reported: click a different pet to switch → the optimistic update in
    // handleSlotClick sometimes doesn't match what the server actually did
    // (different click type, filtered/paginated slot layout, pet on a
    // cooldown, etc.) → `current` goes stale → because suppressScan was
    // still `true`, the per-tick scan that would've caught the real active
    // pet never ran again until you closed and reopened the Pets menu.
    //
    // Fixed by making the suppression a short timeout instead of a sticky
    // flag: the optimistic click-update still applies instantly (no visual
    // delay), but scanning always resumes on its own ~0.5s later regardless
    // of whether that optimistic update turned out to be right.
    private const val SUPPRESS_TICKS = 10
    private var suppressScanTicks = 0

    // ── Registration ─────────────────────────────────────────────────────────

    fun register() {
        // ① Chat: autopet messages
        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay) onChatMessage(text)
            true
        }

        // ② GUI: scan pet menu each tick + intercept clicks
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (screen is AbstractContainerScreen<*> && screen.title.string.startsWith("Pets")) {
                suppressScanTicks = 0

                // Scan each tick for the currently-active pet (despawn lore).
                // Always ticks down/resumes on its own — see the doc comment
                // on suppressScanTicks for why this can't be a sticky flag.
                ScreenEvents.afterTick(screen).register {
                    if (suppressScanTicks > 0) {
                        suppressScanTicks--
                    } else {
                        scanForActivePet(screen)
                    }
                }
            }
        }
    }

    /** Called by the slot-click mixin so we can detect equip / despawn. */
    fun handleSlotClick(screen: AbstractContainerScreen<*>, slot: Slot, slotId: Int) {
        if (slotId !in 0..53) return
        val stack = slot.item
        if (stack.isEmpty) return

        if (!isPetItem(stack)) return

        val lore = getLore(stack)
        val isDespawn = lore.any { it.contains("Click to despawn!") }
        val isEquip   = lore.any { it.contains("Click to equip!") }

        if (isDespawn) {
            // Player is clicking the active pet → despawn
            current = null
            suppressScanTicks = SUPPRESS_TICKS
            AsthoonLite.LOGGER.info("[AsthoonLite] Pet despawned")
        } else if (isEquip) {
            parsePetStack(stack)?.let { pet ->
                current = pet
                suppressScanTicks = SUPPRESS_TICKS
                AsthoonLite.LOGGER.info("[AsthoonLite] Pet equipped via click: ${pet.display}")
            }
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun onChatMessage(text: Component) {
        val raw = ChatFormatting.stripFormatting(text.string) ?: return

        // ① "You summoned your X!" — the primary, instant, always-on signal.
        val summonMatch = SUMMON_PATTERN.matcher(raw)
        if (summonMatch.matches()) {
            val name = summonMatch.group(1).trim()
            // The summon message doesn't carry a level, so keep the level
            // from `current` if it's the same pet name (e.g. re-summoning
            // after a menu-scan already learned the level), otherwise -1.
            val level = current?.takeIf { it.name == name }?.level ?: -1
            val color = extractRarityColor(text, name)
            current = ActivePet(
                name        = name,
                level       = level,
                rarityColor = color,
                display     = if (level >= 0) "[Lvl $level] $name" else name
            )
            suppressScanTicks = SUPPRESS_TICKS
            AsthoonLite.LOGGER.info("[AsthoonLite] Pet summoned (chat): ${current?.display}")
            return
        }

        // ② "You despawned your X!" — primary despawn signal, fires even
        // when the Pets menu is closed (e.g. pet died in combat).
        val despawnMatch = DESPAWN_PATTERN.matcher(raw)
        if (despawnMatch.matches()) {
            current = null
            suppressScanTicks = SUPPRESS_TICKS
            AsthoonLite.LOGGER.info("[AsthoonLite] Pet despawned (chat)")
            return
        }

        // ③ Autopet rule messages carry an explicit level, so prefer these
        // over the level-less summon message when both could match.
        val autopetMatch = AUTOPET_PATTERN.matcher(raw)
        if (!autopetMatch.matches()) return

        val level = autopetMatch.group(1).toIntOrNull() ?: return
        val name  = autopetMatch.group(2).trim()

        // Try to get rarity colour from the styled text
        val color = extractRarityColor(text, name)

        current = ActivePet(
            name        = name,
            level       = level,
            rarityColor = color,
            display     = "[Lvl $level] $name"
        )
        suppressScanTicks = SUPPRESS_TICKS
        AsthoonLite.LOGGER.info("[AsthoonLite] Autopet detected: ${current?.display}")
    }

    // NOTE: `screen.menu` is a best-effort guess for the Yarn `screenHandler`
    // field (Mojang's AbstractContainerScreen field is historically `menu`).
    private fun scanForActivePet(screen: AbstractContainerScreen<*>) {
        for (slot in screen.menu.slots) {
            val stack = slot.item
            if (stack.isEmpty || !isPetItem(stack)) continue
            val lore = getLore(stack)
            if (lore.any { it.contains("Click to despawn!") }) {
                parsePetStack(stack)?.let { pet ->
                    if (current?.display != pet.display) {
                        current = pet
                        AsthoonLite.LOGGER.info("[AsthoonLite] Active pet found in menu: ${pet.display}")
                    }
                }
                return
            }
        }
    }

    /** Returns true if this ItemStack looks like a Skyblock pet item. */
    fun isPetItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val name = stack.hoverName?.string ?: return false
        return PET_NAME_PATTERN.matcher(ChatFormatting.stripFormatting(name) ?: "").matches()
    }

    /** Returns true if this slot holds the currently-active pet. */
    fun isActivePet(stack: ItemStack): Boolean {
        val pet = current ?: return false
        if (!isPetItem(stack)) return false
        val lore = getLore(stack)
        // The active pet has "Click to despawn!" in its lore
        return lore.any { it.contains("Click to despawn!") } ||
               (ChatFormatting.stripFormatting(stack.hoverName?.string ?: "")?.let {
                   PET_NAME_PATTERN.matcher(it).let { m -> m.matches() && m.group(2).trim() == pet.name && m.group(1).toIntOrNull() == pet.level }
               } == true && lore.any { it.contains("Click to despawn!") })
    }

    /** Checks lore-based active status (most reliable). */
    fun hasDesawnLore(stack: ItemStack): Boolean =
        getLore(stack).any { it.contains("Click to despawn!") }

    private fun parsePetStack(stack: ItemStack): ActivePet? {
        val rawName = ChatFormatting.stripFormatting(stack.hoverName?.string ?: "") ?: return null
        val m = PET_NAME_PATTERN.matcher(rawName)
        if (!m.matches()) return null
        val level = m.group(1).toIntOrNull() ?: return null
        val name  = m.group(2).trim()
        val color = extractRarityColorFromName(stack.hoverName?.string ?: "")
        return ActivePet(name = name, level = level, rarityColor = color, display = "[Lvl $level] $name")
    }

    // NOTE: DataComponents.LORE is the best-effort Mojang name for Yarn's
    // DataComponentTypes.LORE; ItemLore#lines() returns List<Component>.
    private fun getLore(stack: ItemStack): List<String> {
        val loreComp = stack.get(net.minecraft.core.component.DataComponents.LORE) ?: return emptyList()
        return loreComp.lines().map { ChatFormatting.stripFormatting(it.string) ?: "" }
    }

    /** Reads the §x prefix from the item name string to determine rarity colour. */
    private fun extractRarityColorFromName(styledName: String): Int {
        for ((prefix, _) in RARITY_PREFIXES) {
            if (styledName.contains(prefix)) {
                return ChatFormatting.getByCode(prefix[1])?.color ?: 0
            }
        }
        return 0xFFFFAA00.toInt() // default gold
    }

    // NOTE: iterating a Component's styled runs changed shape a few times
    // across Minecraft versions (Yarn's `asOrderedText().accept { ... }`).
    // This is a best-effort port using Component#visit and may need
    // adjustment against the real 26.1.2 Component/FormattedText API.
    private fun extractRarityColor(text: Component, petName: String): Int {
        val str = text.string
        val nameIdx = str.indexOf(petName)
        if (nameIdx < 0) return 0xFFFFAA00.toInt()

        var foundColor = 0xFFFFAA00.toInt()
        var codePoint  = 0
        text.visit({ style, content ->
            if (codePoint == nameIdx) {
                foundColor = style.color?.value?.or(0xFF000000.toInt()) ?: 0xFFFFAA00.toInt()
                net.minecraft.network.chat.FormattedText.STOP_ITERATION
            } else {
                codePoint += content.length
                java.util.Optional.empty()
            }
        }, net.minecraft.network.chat.Style.EMPTY)
        return foundColor
    }
}
