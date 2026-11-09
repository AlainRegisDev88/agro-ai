package com.agroai.app.feature.result

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.agroai.app.AppContainer
import com.agroai.app.R
import com.agroai.app.appContainer
import com.agroai.app.data.history.Scan
import com.agroai.app.data.history.isDemo
import com.agroai.app.data.history.status
import com.agroai.app.data.history.top
import com.agroai.app.ml.DiagnosisStatus
import com.agroai.app.ui.components.AgroBottomSheet
import com.agroai.app.ui.components.AgroCard
import com.agroai.app.ui.components.AgroTopBar
import com.agroai.app.ui.components.BulletList
import com.agroai.app.ui.components.CardTone
import com.agroai.app.ui.components.ConfidenceBar
import com.agroai.app.ui.components.IconCircleButton
import com.agroai.app.ui.components.Pill
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.SecondaryButton
import com.agroai.app.ui.components.SectionHeader
import com.agroai.app.ui.components.StatusBadge
import com.agroai.app.ui.components.TertiaryButton
import com.agroai.app.ui.components.percent
import com.agroai.app.ui.components.statusColors
import com.agroai.app.feature.onboarding.StickyFooter
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ResultViewModel(private val app: AppContainer, private val scanId: Long) : ViewModel() {
    val content = app.content
    val scan = app.history.observe(scanId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun feedback(correct: Boolean) = viewModelScope.launch { app.history.setFeedback(scanId, correct) }

    fun delete(done: () -> Unit) = viewModelScope.launch {
        val s = app.history.get(scanId)
        app.history.delete(scanId)
        s?.let(app.history::purgePhoto)
        done()
    }
}

@Composable
fun ResultScreen(
    scanId: Long,
    onBack: () -> Unit,
    onScanAgain: (String) -> Unit,
    onLearnMore: (String) -> Unit,
) {
    val app = appContainer()
    val vm: ResultViewModel = viewModel(key = "result-$scanId") { ResultViewModel(app, scanId) }
    val scan by vm.scan.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val c = AgroTheme.colors

    Column(Modifier.fillMaxSize().background(c.background)) {
        AgroTopBar(stringResource(R.string.result_title), onBack = onBack) {
            val s = scan
            if (s != null) {
                val summary = shareText(context, s, vm.content.cropName(s.crop), vm.content.name(s.label))
                IconCircleButton(Icons.Outlined.Share, stringResource(R.string.action_share), { share(context, s, summary) }, container = Color.Transparent)
                IconCircleButton(Icons.Outlined.Delete, stringResource(R.string.action_delete), { confirmDelete = true }, container = Color.Transparent)
            }
        }
        val s = scan ?: return@Column
        val status = s.status
        val info = vm.content.disease(s.label)
        val colors = statusColors(status)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(Radius.xl).background(c.surfaceSoft)) {
                    AsyncImage(File(s.imagePath), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    StatusBadge(status, Modifier.align(Alignment.BottomStart).padding(Space.md))
                }
            }

            // Hero: what it is.
            item {
                Column(
                    Modifier.fillMaxWidth().clip(Radius.xl).background(colors.hero).padding(Space.lg),
                ) {
                    Text(
                        vm.content.cropName(s.crop).uppercase(),
                        style = AgroTheme.type.caption,
                        color = colors.onHero.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (status == DiagnosisStatus.Uncertain) stringResource(R.string.result_uncertain_title) else info.name,
                        style = AgroTheme.type.displayXl,
                        color = colors.onHero,
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        when (status) {
                            DiagnosisStatus.Uncertain -> stringResource(R.string.result_uncertain_body, info.name, percent(s.confidence))
                            else -> info.summary.ifBlank { stringResource(R.string.result_confidence, percent(s.confidence)) }
                        },
                        style = AgroTheme.type.bodyMd,
                        color = colors.onHero.copy(alpha = 0.9f),
                    )
                    if (status != DiagnosisStatus.Uncertain) {
                        Spacer(Modifier.height(Space.md))
                        Pill(stringResource(R.string.result_confidence, percent(s.confidence)), colors.badge, colors.onBadge)
                    }
                }
            }

            if (s.isDemo) {
                item {
                    AgroCard(tone = CardTone.Sage) {
                        Text(stringResource(R.string.result_demo_title), style = AgroTheme.type.bodyMdStrong, color = c.ink)
                        Text(stringResource(R.string.result_demo_body), style = AgroTheme.type.bodySm, color = c.body)
                    }
                }
            }

            if (status == DiagnosisStatus.Uncertain) {
                item {
                    AgroCard {
                        Text(stringResource(R.string.result_retake_title), style = AgroTheme.type.displayMd, color = c.ink)
                        Spacer(Modifier.height(Space.md))
                        BulletList(stringArrayResource(R.array.retake_tips).toList())
                        Spacer(Modifier.height(Space.lg))
                        PrimaryButton(stringResource(R.string.result_try_again), { onScanAgain(s.crop) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.CameraAlt)
                    }
                }
            } else {
                item {
                    AgroCard {
                        val healthy = status == DiagnosisStatus.Healthy
                        Text(
                            stringResource(if (healthy) R.string.result_keep_healthy else R.string.result_what_to_do),
                            style = AgroTheme.type.displayMd, color = c.ink,
                        )
                        Spacer(Modifier.height(Space.md))
                        val steps = if (healthy) info.prevention.take(3) else info.treatment.highlights()
                        if (steps.isNotEmpty()) {
                            BulletList(steps)
                        } else {
                            Text(stringResource(R.string.result_no_guidance), style = AgroTheme.type.bodyMd, color = c.body)
                        }
                        Spacer(Modifier.height(Space.lg))
                        TertiaryButton(stringResource(R.string.result_learn_more, info.name), { onLearnMore(s.label) })
                    }
                }
            }

            item {
                AgroCard {
                    Text(stringResource(R.string.result_possibilities), style = AgroTheme.type.displaySm, color = c.ink)
                    Spacer(Modifier.height(Space.md))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        s.top.forEachIndexed { i, score ->
                            ConfidenceBar(
                                vm.content.name(score.label),
                                score.score,
                                color = if (i == 0) colors.accent else c.mute,
                            )
                        }
                    }
                }
            }

            item {
                AgroCard(tone = CardTone.Green) {
                    if (s.feedback == null) {
                        Text(stringResource(R.string.result_feedback_title), style = AgroTheme.type.bodyMdStrong, color = c.ink)
                        Spacer(Modifier.height(Space.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            SecondaryButton(stringResource(R.string.result_feedback_yes), { vm.feedback(true) }, Modifier.weight(1f))
                            SecondaryButton(stringResource(R.string.result_feedback_no), { vm.feedback(false) }, Modifier.weight(1f))
                        }
                    } else {
                        Text(stringResource(R.string.result_feedback_thanks), style = AgroTheme.type.bodyMdStrong, color = c.ink)
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.disclaimer),
                    style = AgroTheme.type.caption,
                    color = c.mute,
                    modifier = Modifier.padding(bottom = Space.lg),
                )
            }
        }
        StickyFooter {
            PrimaryButton(
                stringResource(R.string.result_scan_another),
                { onScanAgain(s.crop) },
                Modifier.fillMaxWidth(),
                icon = Icons.Outlined.CameraAlt,
            )
        }
    }

    if (confirmDelete) {
        AgroBottomSheet(onDismiss = { confirmDelete = false }) {
            Text(stringResource(R.string.result_delete_title), style = AgroTheme.type.displayMd, color = c.ink)
            Spacer(Modifier.height(Space.sm))
            Text(stringResource(R.string.result_delete_body), style = AgroTheme.type.bodyMd, color = c.body)
            Spacer(Modifier.height(Space.xl))
            TertiaryButton(
                stringResource(R.string.action_delete),
                { confirmDelete = false; vm.delete(onBack) },
                Modifier.fillMaxWidth(),
                contentColor = c.negative,
            )
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(stringResource(R.string.action_cancel), { confirmDelete = false }, Modifier.fillMaxWidth())
        }
    }
}

private fun shareText(context: Context, scan: Scan, crop: String, name: String): String =
    context.getString(R.string.share_text, crop, name, percent(scan.confidence))

private fun share(context: Context, scan: Scan, text: String) {
    val file = File(scan.imagePath)
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (file.exists()) {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(intent, null))
}
