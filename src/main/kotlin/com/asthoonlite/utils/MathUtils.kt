package com.asthoonlite.utils

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object MathUtils {

    data class LinearRegressionResult(val r: Double, val b: Double, val a: Double)

    fun linReg(x: List<Double>, y: List<Double>): LinearRegressionResult? {
        if (x.size != y.size) throw IllegalArgumentException("inputs not same size")
        if (x.size < 2) return null

        val mx = x.sum() / x.size
        val my = y.sum() / y.size

        var xStd = 0.0
        var yStd = 0.0
        var r = 0.0
        for (i in x.indices) {
            val dx = (x[i] - mx)
            val dy = (y[i] - my)
            xStd += dx * dx
            yStd += dy * dy
            r += dx * dy
        }

        xStd = sqrt(xStd)
        yStd = sqrt(yStd)
        if (xStd == 0.0) return null
        if (yStd == 0.0) return LinearRegressionResult(1.0, 0.0, my)
        r /= (xStd * yStd)

        val b = r * yStd / xStd
        val a = my - b * mx

        return LinearRegressionResult(r, b, a)
    }

    fun lerp(f: Double, o: Double, n: Double): Double = (n - o) * f.coerceIn(0.0..1.0) + o

    fun rescale(v: Double, oldMin: Double, oldMax: Double, newMin: Double, newMax: Double): Double =
        if (oldMax == oldMin) newMin else (v - oldMin) / (oldMax - oldMin) * (newMax - newMin) + newMin
}
