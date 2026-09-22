package com.asthoonlite.dungeon

object DungeonServerTick {
    var current: Long = 0L
        private set

    fun tick() { current++ }
}
