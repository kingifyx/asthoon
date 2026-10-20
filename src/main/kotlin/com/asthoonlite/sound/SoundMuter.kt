package com.asthoonlite.sound

import com.asthoonlite.config.Config
import net.minecraft.client.resources.sounds.SoundInstance

object SoundMuter {

    fun shouldMute(instance: SoundInstance?): Boolean {
        if (!Config.soundMuterEnabled || instance == null) return false
        val loc = instance.identifier.toString()
        return loc in Config.mutedSounds
    }

    fun mute(soundId: String) {
        Config.mutedSounds.add(soundId)
        Config.save()
    }

    fun unmute(soundId: String) {
        Config.mutedSounds.remove(soundId)
        Config.save()
    }

    fun toggle(soundId: String): Boolean {
        val muted = if (Config.mutedSounds.contains(soundId)) {
            Config.mutedSounds.remove(soundId)
            false
        } else {
            Config.mutedSounds.add(soundId)
            true
        }
        Config.save()
        return muted
    }
}
