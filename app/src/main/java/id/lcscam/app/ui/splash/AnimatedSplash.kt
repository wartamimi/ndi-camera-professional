package id.lcscam.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.lcscam.app.R
import id.lcscam.app.ui.theme.Cine
import androidx.compose.ui.res.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Splash background, identical to the system splash colour so the hand-off is seamless. */
private val SplashBg = Color(0xFF14161B)
private val Ring = Color(0xFFFF8A1F)
private val Core = Color(0xFFF2F2F4)
private val Live = Color(0xFFE5322D)

/** Total length of the logo animation. */
const val SPLASH_DURATION_MS = 1900

/** Maps overall progress [p] into a 0..1 progress for the window [start]..[end]. */
private fun seg(p: Float, start: Float, end: Float) = ((p - start) / (end - start)).coerceIn(0f, 1f)
private fun easeOutCubic(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/**
 * Animated LCS Cam logo: the orange lens ring sweeps in while rotating into place, the white core
 * pops in, then the red live dot lights up with a pulse and the name fades in. Geometry matches
 * the launcher icon (ring r=19, gap centred at -45 degrees, live dot r=4.2 in a 108 viewport).
 */
@Composable
fun AnimatedSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SPLASH_DURATION_MS, easing = LinearEasing))
        onFinished()
    }
    val p = progress.value
    val fadeOut = 1f - seg(p, 0.9f, 1f)
    Box(Modifier.fillMaxSize().background(SplashBg).alpha(fadeOut), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(168.dp)) {
                val unit = size.minDimension / 108f * 1.22f // icon viewport unit, with the icon's 1.22 group scale
                val c = Offset(size.width / 2f, size.height / 2f)
                val ringT = easeOutCubic(seg(p, 0f, 0.4f))
                val r = 19f * unit
                // Ring: sweeps from 0 to 300 degrees while rotating 120 degrees into its final pose.
                rotate(-120f * (1f - ringT), c) {
                    drawArc(
                        color = Ring.copy(alpha = seg(p, 0f, 0.08f)),
                        startAngle = -15f, sweepAngle = 300f * ringT, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                        style = Stroke(width = 6.5f * unit, cap = StrokeCap.Round),
                    )
                }
                // White core.
                val coreT = easeOutBack(seg(p, 0.18f, 0.45f))
                if (coreT > 0f) drawCircle(Core, radius = 8f * unit * coreT, center = c)
                // Red live dot in the ring's gap, with a pulse halo.
                val a = (-45f / 180f * PI).toFloat()
                val dot = Offset(c.x + r * cos(a), c.y + r * sin(a))
                val dotT = easeOutBack(seg(p, 0.42f, 0.6f))
                val pulse = seg(p, 0.55f, 0.88f)
                if (pulse in 0.001f..0.999f) {
                    drawCircle(Live.copy(alpha = 0.45f * (1f - pulse)), radius = 4.2f * unit * (1f + 2.6f * pulse), center = dot)
                }
                if (dotT > 0f) drawCircle(Live, radius = 4.2f * unit * dotT, center = dot)
            }
            Spacer(Modifier.height(18.dp))
            val textT = easeOutCubic(seg(p, 0.5f, 0.78f))
            Text(
                stringResource(R.string.app_name), color = Cine.Text, fontSize = 30.sp, fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier.graphicsLayer { alpha = textT; translationY = (1f - textT) * 24.dp.toPx() },
            )
            Text(
                stringResource(R.string.developer), color = Cine.Dim, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = easeOutCubic(seg(p, 0.62f, 0.85f)) },
            )
        }
    }
}
