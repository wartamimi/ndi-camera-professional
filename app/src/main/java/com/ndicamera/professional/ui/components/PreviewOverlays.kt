package com.ndicamera.professional.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.ndicamera.professional.camera.OverlayFrame
import com.ndicamera.professional.settings.OverlaySettings
import com.ndicamera.professional.ui.theme.Cine

/** Zebra / focus peaking mask, rotated from sensor orientation to the displayed preview. */
@Composable
fun AnalysisMask(frame: OverlayFrame?, rotation: Int, mirrored: Boolean, modifier: Modifier = Modifier) {
    val bmp = frame?.mask ?: return
    val swap = rotation == 90 || rotation == 270
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
        modifier = modifier
            .fillMaxSize()
            // Mirror in display space (outer), then rotate sensor->display (inner).
            .scale(if (mirrored) -1f else 1f, 1f)
            .graphicsLayer {
                rotationZ = rotation.toFloat()
                if (swap) {
                    // After a 90 degree rotation the bitmap must be stretched to the swapped box.
                    scaleX = size.height / size.width
                    scaleY = size.width / size.height
                }
            },
    )
}

/** Thirds, crosshair, safe areas and the tap focus indicator. */
@Composable
fun GuideOverlay(settings: OverlaySettings, focusPoint: Offset?, focusColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val line = Color.White.copy(alpha = 0.35f)
        if (settings.thirds) {
            for (i in 1..2) {
                drawLine(line, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), 1f)
                drawLine(line, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), 1f)
            }
        }
        if (settings.crosshair) {
            val c = Offset(w / 2, h / 2)
            val l = minOf(w, h) * 0.04f
            drawLine(Color.White, Offset(c.x - l, c.y), Offset(c.x + l, c.y), 2f)
            drawLine(Color.White, Offset(c.x, c.y - l), Offset(c.x, c.y + l), 2f)
        }
        if (settings.safeAreas) {
            // Action safe 93%, title safe 90% (SMPTE ST 2046-1 for 16:9).
            listOf(0.93f to Cine.Warn.copy(alpha = 0.7f), 0.90f to Color.White.copy(alpha = 0.6f)).forEach { (f, col) ->
                val sw = w * f
                val sh = h * f
                drawRect(col, Offset((w - sw) / 2, (h - sh) / 2), Size(sw, sh), style = Stroke(1.5f))
            }
        }
        focusPoint?.let { p ->
            val s = minOf(w, h) * 0.12f
            drawRect(focusColor, Offset(p.x - s / 2, p.y - s / 2), Size(s, s), style = Stroke(2f))
        }
    }
}

/** Luma histogram (log-scaled counts) with clipping markers. */
@Composable
fun Histogram(frame: OverlayFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color.Black.copy(alpha = 0.55f))
        if (frame == null) return@Canvas
        val hist = frame.histogram
        var max = 1.0
        for (v in hist) if (v > 0) max = maxOf(max, kotlin.math.ln(1.0 + v))
        val path = Path()
        path.moveTo(0f, size.height)
        for (i in 0 until 256) {
            val x = i / 255f * size.width
            val y = size.height - (kotlin.math.ln(1.0 + hist[i]) / max).toFloat() * size.height
            path.lineTo(x, y)
        }
        path.lineTo(size.width, size.height)
        path.close()
        drawPath(path, Color.White.copy(alpha = 0.7f))
        if (frame.clippedFraction > 0.01f) drawRect(Cine.Rec, Offset(size.width - 4f, 0f), Size(4f, size.height))
        if (frame.crushedFraction > 0.01f) drawRect(Cine.Preview, Offset(0f, 0f), Size(4f, size.height))
    }
}
