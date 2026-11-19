package id.lcscam.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.lcscam.app.R
import id.lcscam.app.ui.theme.Cine

/** Splash background: the navy of the launcher icon, identical to the system splash colour. */
val SplashBg = Color(0xFF0C0F1A)

/** Total length of the splash animation. */
const val SPLASH_DURATION_MS = 2300

/** Maps overall progress [p] into a 0..1 progress for the window [start]..[end]. */
fun seg(p: Float, start: Float, end: Float) = ((p - start) / (end - start)).coerceIn(0f, 1f)
fun easeOutCubic(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/** Width / height of the logo artwork (the logo_part_* drawables all share this canvas). */
private const val LOGO_ASPECT = 720f / 479f

/**
 * The LCS Cam logo assembled from its six parts. [p] runs 0..1: the orange frame closes in from
 * top and bottom, "LCS" springs in, the camera tail slides in from the right, "Cam" rises, the NDI
 * badge pops and a light sweep crosses the finished logo. At p = 1 it is the static logo.
 */
@Composable
fun AnimatedLogo(p: Float, modifier: Modifier = Modifier) {
    val top = easeOutBack(seg(p, 0.00f, 0.30f))
    val bottom = easeOutBack(seg(p, 0.06f, 0.36f))
    val lcs = easeOutBack(seg(p, 0.18f, 0.48f))
    val tail = easeOutBack(seg(p, 0.36f, 0.58f))
    val cam = easeOutCubic(seg(p, 0.46f, 0.66f))
    val badge = easeOutBack(seg(p, 0.56f, 0.76f))
    val shine = seg(p, 0.70f, 0.95f)
    Box(
        modifier
            .aspectRatio(LOGO_ASPECT)
            // Offscreen so the light sweep only lights up the logo's own pixels.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (shine > 0f && shine < 1f) {
                    val w = size.width
                    val x = -0.4f * w + shine * 1.8f * w
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent,
                            start = Offset(x, 0f), end = Offset(x + 0.3f * w, size.height),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            },
    ) {
        val part: @Composable (Int, Modifier) -> Unit = { id, m ->
            Image(painterResource(id), contentDescription = null, modifier = Modifier.fillMaxSize().then(m))
        }
        part(R.drawable.logo_part_top, Modifier.graphicsLayer {
            alpha = seg(p, 0f, 0.12f); translationY = -(1f - top) * size.height * 0.35f
        })
        part(R.drawable.logo_part_bottom, Modifier.graphicsLayer {
            alpha = seg(p, 0.06f, 0.18f); translationY = (1f - bottom) * size.height * 0.35f
        })
        part(R.drawable.logo_part_lcs, Modifier.graphicsLayer {
            alpha = seg(p, 0.18f, 0.30f); scaleX = 0.55f + 0.45f * lcs; scaleY = scaleX
        })
        part(R.drawable.logo_part_tail, Modifier.graphicsLayer {
            alpha = seg(p, 0.36f, 0.46f); translationX = (1f - tail) * size.width * 0.25f
        })
        part(R.drawable.logo_part_cam, Modifier.graphicsLayer {
            alpha = cam; translationY = (1f - cam) * size.height * 0.12f
        })
        part(R.drawable.logo_part_badge, Modifier.graphicsLayer {
            alpha = seg(p, 0.56f, 0.64f)
            // Pop around the badge centre (badge sits at ~79% x, ~78% y of the artwork).
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.79f, 0.78f)
            scaleX = badge; scaleY = badge
        })
    }
}

/** In-app animated splash shown right after the system splash, then [onFinished]. */
@Composable
fun AnimatedSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SPLASH_DURATION_MS, easing = LinearEasing))
        onFinished()
    }
    val p = progress.value
    val fadeOut = 1f - seg(p, 0.92f, 1f)
    Box(Modifier.fillMaxSize().background(SplashBg).alpha(fadeOut), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // The logo gets a gentle zoom-out at the end to hand over to the app.
            AnimatedLogo(
                seg(p, 0f, 0.85f),
                Modifier.width(260.dp).graphicsLayer {
                    val s = 1f + 0.04f * seg(p, 0.88f, 1f); scaleX = s; scaleY = s
                },
            )
            Spacer(Modifier.height(28.dp))
            val textT = easeOutCubic(seg(p, 0.62f, 0.85f))
            Text(
                stringResource(R.string.developer), color = Cine.Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = textT; translationY = (1f - textT) * 12.dp.toPx() },
            )
        }
    }
}
