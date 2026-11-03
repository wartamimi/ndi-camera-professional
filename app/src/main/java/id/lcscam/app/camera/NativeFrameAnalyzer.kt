package id.lcscam.app.camera

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/** Result of one analysis pass; the mask is in sensor orientation. */
class OverlayFrame(
    val mask: Bitmap?,
    val histogram: IntArray,
    val meanLuma: Float,
    val clippedFraction: Float,
    val crushedFraction: Float,
    val sequence: Long,
)

data class AnalyzerSettings(
    val zebra: Boolean = false,
    val zebraThreshold: Int = 235,
    val peaking: Boolean = false,
    val peakingThreshold: Int = 48,
    val peakingColor: Int = 0xFFFF3B30.toInt(),
)

/**
 * Focus peaking, zebra and histogram computed natively (libndicam.so) from the luma plane.
 * Masks rotate through three bitmaps so the UI never draws a bitmap that is being written.
 */
class NativeFrameAnalyzer {
    @Volatile var settings = AnalyzerSettings()
    private val _output = MutableStateFlow<OverlayFrame?>(null)
    val output: StateFlow<OverlayFrame?> = _output.asStateFlow()

    private var bitmaps: Array<Bitmap>? = null
    private var index = 0
    private var sequence = 0L
    private var phase = 0

    /** Called on the ndi-video thread. */
    fun analyze(y: ByteBuffer, rowStride: Int, width: Int, height: Int) {
        val s = settings
        val needMask = s.zebra || s.peaking
        val bmp = if (needMask) nextBitmap(width, height) else null
        val hist = IntArray(256)
        val stats = FloatArray(3)
        phase = (phase + 1) and 7
        val rc = nativeAnalyze(y, rowStride, width, height, bmp, s.zebra, s.zebraThreshold, s.peaking,
            s.peakingThreshold, s.peakingColor, phase, hist, stats)
        if (rc != 0) return
        _output.value = OverlayFrame(bmp, hist, stats[0], stats[1], stats[2], ++sequence)
    }

    private fun nextBitmap(width: Int, height: Int): Bitmap {
        val mw = 384
        val mh = (mw.toLong() * height / width).toInt().coerceAtLeast(1)
        val current = bitmaps
        val set = if (current == null || current[0].width != mw || current[0].height != mh) {
            Array(3) { Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888) }.also { bitmaps = it }
        } else current
        index = (index + 1) % 3
        return set[index]
    }

    companion object {
        init { System.loadLibrary("ndicam") }

        @JvmStatic external fun nativeAnalyze(
            y: ByteBuffer, rowStride: Int, width: Int, height: Int, bitmap: Bitmap?,
            zebra: Boolean, zebraThreshold: Int, peaking: Boolean, peakingThreshold: Int, peakingColor: Int,
            stripePhase: Int, histogramOut: IntArray, statsOut: FloatArray,
        ): Int
    }
}
