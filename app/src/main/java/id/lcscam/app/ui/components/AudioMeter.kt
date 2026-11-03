package id.lcscam.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import id.lcscam.app.domain.model.AudioLevels
import id.lcscam.app.ui.theme.Cine

/** Vertical PPM-style meter, -60..0 dBFS, with peak marks and clip indicator. */
@Composable
fun AudioMeter(levels: AudioLevels, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ch = levels.peakDb.size.coerceAtLeast(1)
        val gap = 3f
        val barW = (size.width - gap * (ch - 1)) / ch
        fun frac(db: Float) = ((db + 60f) / 60f).coerceIn(0f, 1f)
        for (i in 0 until ch) {
            val x = i * (barW + gap)
            drawRect(Cine.PanelRaised, Offset(x, 0f), Size(barW, size.height))
            val rmsH = frac(levels.rmsDb.getOrElse(i) { -120f }) * size.height
            val color = when {
                levels.rmsDb.getOrElse(i) { -120f } > -6f -> Cine.Rec
                levels.rmsDb.getOrElse(i) { -120f } > -18f -> Cine.Warn
                else -> Cine.Ok
            }
            drawRect(color, Offset(x, size.height - rmsH), Size(barW, rmsH))
            val pk = size.height - frac(levels.peakDb.getOrElse(i) { -120f }) * size.height
            drawRect(Color.White, Offset(x, pk), Size(barW, 2f))
        }
        // -18 dBFS reference line
        val ref = size.height - frac(-18f) * size.height
        drawLine(Cine.Dim, Offset(0f, ref), Offset(size.width, ref), 1f)
        if (levels.clipped) drawRect(Cine.Rec, Offset(0f, 0f), Size(size.width, 4f))
    }
}
