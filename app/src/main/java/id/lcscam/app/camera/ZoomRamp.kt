package id.lcscam.app.camera

import kotlin.math.pow

/** Pure math for timed zoom moves (unit-tested). */
object ZoomRamp {
    /** Durations offered in the zoom panel, in seconds. 0 means instant. */
    val DURATIONS_SEC = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)

    /** Smoothstep easing: starts and ends gently, like a servo zoom rocker. */
    fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * Zoom ratio at progress [t] (0..1) of a move from [from] to [to]. Interpolates in log space,
     * so the image grows at a perceptually constant rate (1x->2x takes as long as 2x->4x).
     */
    fun valueAt(from: Float, to: Float, t: Float): Float {
        val e = ease(t)
        if (t >= 1f) return to
        if (from <= 0f || to <= 0f) return from + (to - from) * e
        return from * (to / from).pow(e)
    }
}
