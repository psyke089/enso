package app.enso.meditation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.enso.meditation.R
import app.enso.meditation.ui.theme.Paper
import app.enso.meditation.ui.theme.WashiTint

/**
 * Decorative paper-and-ink assets. These are the real artwork supplied for Enso;
 * the composables only position and gently fade them so they never compete with the timer.
 */

/** Warm washi paper fills the screen; a soft wash keeps the texture from feeling busy. */
@Composable
fun WashiBackground(modifier: Modifier = Modifier) {
    Box(modifier.background(Paper)) {
        Image(
            painter = painterResource(R.drawable.washi_texture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.94f),
        )
        Box(Modifier.fillMaxSize().background(WashiTint.copy(alpha = 0.12f)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Paper.copy(alpha = 0.10f),
                        Color.Transparent,
                        Paper.copy(alpha = 0.18f),
                    ),
                ),
            ),
        )
    }
}

/** Rotates the supplied brush so its opening sits toward the top-left. */
private const val ENSO_ROTATION_DEGREES = 154f

/** The hand-painted Enso brush circle. The timer is laid over its empty center. */
@Composable
fun EnsoArtwork(modifier: Modifier = Modifier, paused: Boolean = false) {
    Image(
        painter = painterResource(R.drawable.enso_brush),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alpha = if (paused) 0.55f else 0.92f,
        modifier = modifier.rotate(ENSO_ROTATION_DEGREES),
    )
}

/** A faint ink-wash landscape anchored near the bottom edge. */
@Composable
fun SumiLandscape(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.sumi_landscape),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        alpha = 0.45f,
        modifier = modifier,
    )
}

/** Small vermilion seal used as the single warm accent. */
@Composable
fun SealAccent(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.enso_seal),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alpha = 0.9f,
        modifier = modifier,
    )
}
