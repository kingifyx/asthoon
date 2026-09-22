package com.asthoonlite.mixin

import com.asthoonlite.dungeon.DragonPhase
import com.asthoonlite.dungeon.DungeonTimers
import com.asthoonlite.dungeon.map.DungeonMapScanner
import com.asthoonlite.dungeon.solvers.CampHelper
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPacketListener::class)
abstract class MixinClientPacketListener {
    @Inject(method = ["handleSetTime"], at = [At("TAIL")])
    private fun asthoonlite_onSetTime(packet: ClientboundSetTimePacket, ci: CallbackInfo) {
        DungeonTimers.onServerTime(packet)
    }

    @Inject(method = ["handleAddEntity"], at = [At("TAIL")])
    private fun asthoonlite_onAddEntity(packet: ClientboundAddEntityPacket, ci: CallbackInfo) {
        DragonPhase.onDragonPacket(packet)
    }

    @Inject(method = ["handleSoundEvent"], at = [At("TAIL")])
    private fun asthoonlite_onSound(packet: ClientboundSoundPacket, ci: CallbackInfo) {
        if (packet.sound.value() == net.minecraft.sounds.SoundEvents.ARROW_HIT_PLAYER) {
            DragonPhase.onArrowHit()
        }
        if (packet.sound.value() == net.minecraft.sounds.SoundEvents.BAT_DEATH) {
            com.asthoonlite.dungeon.SecretSounds.onBatDeath(packet.x, packet.y, packet.z)
        }
    }

    @Inject(method = ["handleSetEquipment"], at = [At("TAIL")])
    private fun asthoonlite_onEquipment(packet: ClientboundSetEquipmentPacket, ci: CallbackInfo) {
        if (packet.slots.any { it.second.item == net.minecraft.world.item.Items.PACKED_ICE }) {
            DragonPhase.onIceSpray(packet.entity)
        }
        CampHelper.onEquipmentPacket(packet)
    }

    @Inject(method = ["handleMoveEntity"], at = [At("TAIL")])
    private fun asthoonlite_onMoveEntity(packet: ClientboundMoveEntityPacket, ci: CallbackInfo) {
        val entityId = (packet as? ClientboundMoveEntityPacketAccessor)?.entityId ?: return
        CampHelper.onMovePacket(packet, entityId)
    }

    @Inject(method = ["handleMapItemData"], at = [At("TAIL")])
    private fun asthoonlite_onMapItemData(packet: ClientboundMapItemDataPacket, ci: CallbackInfo) {
        DungeonMapScanner.onMapPacket(packet)
    }

    @Inject(method = ["handleOpenScreen"], at = [At("HEAD")], cancellable = true)
    private fun asthoonlite_onOpenScreen(packet: net.minecraft.network.protocol.game.ClientboundOpenScreenPacket, ci: CallbackInfo) {
        if (!com.asthoonlite.config.Config.autoCloseSecretChest || !com.asthoonlite.dungeon.DungeonContext.inDungeon || com.asthoonlite.dungeon.DungeonContext.inBoss) return
        val type = packet.type
        if (type != net.minecraft.world.inventory.MenuType.GENERIC_9x3 && type != net.minecraft.world.inventory.MenuType.GENERIC_9x6) return
        val title = net.minecraft.ChatFormatting.stripFormatting(packet.title.string) ?: ""
        if (title == "Chest" || title == "Large Chest") {
            val mc = net.minecraft.client.Minecraft.getInstance()
            mc.connection?.send(net.minecraft.network.protocol.game.ServerboundContainerClosePacket(packet.containerId))
            ci.cancel()
        }
    }
}

