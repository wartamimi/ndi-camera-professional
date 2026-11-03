package id.lcscam.app.camera

import kotlin.math.ln
import kotlin.math.pow

/**
 * Approximate conversion between correlated colour temperature and RGGB white-balance gains.
 * Uses the Tanner Helland black-body approximation; the result is normalised so that green = 1.
 * The device's own colour transform from the last auto-WB result is kept, so these gains only
 * shift the white point. The UI labels Kelvin as approximate for that reason.
 */
object WhiteBalanceMath {

    data class Rgb(val r: Float, val g: Float, val b: Float)
    data class Gains(val r: Float, val gEven: Float, val gOdd: Float, val b: Float)

    fun kelvinToRgb(kelvin: Int): Rgb {
        val t = kelvin.coerceIn(1000, 40000) / 100.0
        val r = if (t <= 66) 255.0 else (329.698727446 * (t - 60).pow(-0.1332047592)).coerceIn(0.0, 255.0)
        val g = if (t <= 66) (99.4708025861 * ln(t) - 161.1195681661).coerceIn(0.0, 255.0)
        else (288.1221695283 * (t - 60).pow(-0.0755148492)).coerceIn(0.0, 255.0)
        val b = when {
            t >= 66 -> 255.0
            t <= 19 -> 0.0
            else -> (138.5177312231 * ln(t - 10) - 305.0447927307).coerceIn(0.0, 255.0)
        }
        return Rgb((r / 255).toFloat(), (g / 255).toFloat(), (b / 255).toFloat())
    }

    /**
     * Gains that neutralise a light source of [kelvin]. [tint] in -1..1 shifts green/magenta.
     * Gains are clamped to a range Camera2 HALs accept (>= 1 after normalisation to min).
     */
    fun gainsFor(kelvin: Int, tint: Float = 0f): Gains {
        val c = kelvinToRgb(kelvin)
        val r = 1f / c.r.coerceAtLeast(0.01f)
        val g = 1f / c.g.coerceAtLeast(0.01f)
        val b = 1f / c.b.coerceAtLeast(0.01f)
        val tintFactor = 1f + 0.25f * tint.coerceIn(-1f, 1f)
        // Normalise to green = 1 (with tint), then make sure no gain is below 1.
        var gr = r / g
        var gg = 1f * tintFactor
        var gb = b / g
        val min = minOf(gr, gg, gb)
        gr /= min; gg /= min; gb /= min
        return Gains(gr, gg, gg, gb)
    }

    /** Inverse lookup: estimates the colour temperature that would produce [rOverB] = R/B gain ratio. */
    fun estimateKelvin(redGain: Float, blueGain: Float): Int {
        if (redGain <= 0f || blueGain <= 0f) return 0
        val target = redGain / blueGain
        var best = 5600
        var bestErr = Float.MAX_VALUE
        var k = 2000
        while (k <= 12000) {
            val g = gainsFor(k)
            val err = kotlin.math.abs(g.r / g.b - target)
            if (err < bestErr) { bestErr = err; best = k }
            k += 50
        }
        return best
    }
}
