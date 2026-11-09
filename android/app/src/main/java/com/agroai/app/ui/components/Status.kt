package com.agroai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agroai.app.R
import com.agroai.app.ml.DiagnosisStatus
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space
import kotlin.math.roundToInt

@Immutable
data class StatusColors(val hero: Color, val onHero: Color, val badge: Color, val onBadge: Color, val accent: Color)

@Composable
fun statusColors(status: DiagnosisStatus): StatusColors {
    val c = AgroTheme.colors
    return when (status) {
        DiagnosisStatus.Healthy -> StatusColors(c.heroDark, c.onHeroDark, c.positivePale, c.positiveDeep, c.positive)
        DiagnosisStatus.Disease -> StatusColors(c.negativePale, c.negativeDeep, c.negativePale, c.negativeDeep, c.negative)
        DiagnosisStatus.Uncertain -> StatusColors(c.warningPale, c.warningContent, c.warningPale, c.warningContent, c.warning)
    }
}

@Composable
fun statusLabel(status: DiagnosisStatus): String = stringResource(
    when (status) {
        DiagnosisStatus.Healthy -> R.string.status_healthy
        DiagnosisStatus.Disease -> R.string.status_disease
        DiagnosisStatus.Uncertain -> R.string.status_uncertain
    },
)

/** Small pill. */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AgroTheme.type.bodySmStrong,
        color = content,
        modifier = modifier.clip(Radius.pill).background(container).padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

@Composable
fun StatusBadge(status: DiagnosisStatus, modifier: Modifier = Modifier, text: String = statusLabel(status)) {
    val s = statusColors(status)
    Row(
        modifier.clip(Radius.pill).background(s.badge).padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(Radius.pill).background(s.accent))
        Spacer(Modifier.size(6.dp))
        Text(text, style = AgroTheme.type.bodySmStrong, color = s.onBadge)
    }
}

fun percent(score: Float): String = "${(score * 100).roundToInt()}%"

/** Label + percentage + bar. */
@Composable
fun ConfidenceBar(
    label: String,
    score: Float,
    modifier: Modifier = Modifier,
    color: Color = AgroTheme.colors.ink,
) {
    val animated by animateFloatAsState(score.coerceIn(0f, 1f), label = "confidence")
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = AgroTheme.type.bodyMd, color = AgroTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(percent(score), style = AgroTheme.type.bodyMdStrong, color = AgroTheme.colors.ink)
        }
        Spacer(Modifier.size(Space.xs))
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(Radius.pill).background(AgroTheme.colors.surfaceSoft),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(Radius.pill).background(color))
        }
    }
}
