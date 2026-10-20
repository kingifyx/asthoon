package com.asthoonlite.render

import com.asthoonlite.config.Config

object ItemModelCustomizer {

    fun isEnabled(): Boolean = Config.customItemModelEnabled

    fun getSwingDuration(): Int = Config.swingDuration.coerceIn(1, 40)

    fun getMainScale(): Float = Config.mainHandScale.coerceIn(0.1f, 3.0f)

    fun getOffScale(): Float = Config.offHandScale.coerceIn(0.1f, 3.0f)

    fun getMainX(): Float = Config.mainHandX

    fun getMainY(): Float = Config.mainHandY

    fun getMainZ(): Float = Config.mainHandZ
}
