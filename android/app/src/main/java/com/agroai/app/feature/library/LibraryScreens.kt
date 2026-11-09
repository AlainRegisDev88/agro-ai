package com.agroai.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agroai.app.AppContainer
import com.agroai.app.BuildConfig
import com.agroai.app.R
import com.agroai.app.appContainer
import com.agroai.app.data.content.Crop
import com.agroai.app.data.content.DiseaseInfo
import com.agroai.app.data.models.DownloadState
import com.agroai.app.ml.DiagnosisStatus
import com.agroai.app.ui.components.AgroCard
import com.agroai.app.ui.components.AgroTopBar
import com.agroai.app.ui.components.BulletList
import com.agroai.app.ui.components.CardTone
import com.agroai.app.ui.components.CropAvatar
import com.agroai.app.ui.components.EmptyState
import com.agroai.app.ui.components.FeatureCard
import com.agroai.app.ui.components.ListRow
import com.agroai.app.ui.components.Pill
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.ScreenTitle
import com.agroai.app.ui.components.SectionHeader
import com.agroai.app.ui.components.StatusBadge
import com.agroai.app.ui.components.statusColors
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val app: AppContainer) : ViewModel() {
    val content = app.content

    /** (my crops, the rest). Crops the farmer grows come first. */
    val sections = combine(app.settings.settings, app.models.installed) { s, installed ->
        val mine = app.content.crops.filter { it.id in s.crops || it.id in installed }
        mine to (app.content.crops - mine.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Crop>() to app.content.crops)

    val installed = app.models.installed
    val manifest = app.models.manifest

    fun downloadState(crop: String) = app.models.downloadState(crop)

    fun install(crop: String) = viewModelScope.launch {
        app.settings.addCrop(crop)
        app.models.refreshManifest()
        app.models.enqueueDownload(crop, app.settings.current().wifiOnly)
    }
}

@Composable
fun LibraryScreen(onOpenCrop: (String) -> Unit) {
    val app = appContainer()
    val vm: LibraryViewModel = viewModel { LibraryViewModel(app) }
    val (mine, rest) = vm.sections.collectAsStateWithLifecycle().value
    val c = AgroTheme.colors

    LazyColumn(
        Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(bottom = Space.xl),
    ) {
        item { ScreenTitle(stringResource(R.string.library_title), subtitle = stringResource(R.string.library_body)) }
        if (mine.isNotEmpty()) {
            item { CropList(stringResource(R.string.library_your_crops), mine, onOpenCrop) }
        }
        item { CropList(stringResource(if (mine.isEmpty()) R.string.library_all_crops else R.string.library_other_crops), rest, onOpenCrop) }
    }
}

@Composable
private fun CropList(title: String, crops: List<Crop>, onOpen: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Space.gutter)) {
        SectionHeader(title)
        AgroCard(padding = false) {
            Column(Modifier.padding(horizontal = Space.lg)) {
                crops.forEachIndexed { i, crop ->
                    val problems = crop.labels.count { !it.endsWith("__healthy") }
                    ListRow(
                        title = crop.name,
                        subtitle = pluralStringResource(R.plurals.library_conditions, problems, problems),
                        leading = { CropAvatar(crop) },
                        showChevron = true,
                        divider = i < crops.lastIndex,
                        onClick = { onOpen(crop.id) },
                    )
                }
            }
        }
    }
}

// ─── Crop detail ────────────────────────────────────────────────────────────

@Composable
fun CropDetailScreen(
    cropId: String,
    onBack: () -> Unit,
    onOpenDisease: (String) -> Unit,
    onScan: (String) -> Unit,
) {
    val app = appContainer()
    val vm: LibraryViewModel = viewModel { LibraryViewModel(app) }
    val installed by vm.installed.collectAsStateWithLifecycle()
    val downloadFlow = remember(cropId) { vm.downloadState(cropId) }
    val download by downloadFlow.collectAsStateWithLifecycle(DownloadState.Idle)
    val crop = vm.content.crop(cropId) ?: return
    val model = installed[cropId]
    val c = AgroTheme.colors

    val diseases = crop.labels.filterNot { it.endsWith("__healthy") }
    val healthy = crop.labels.filter { it.endsWith("__healthy") }

    Column(Modifier.fillMaxSize().background(c.background)) {
        AgroTopBar(crop.name, onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xl),
        ) {
            item {
                AgroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CropAvatar(crop, size = 64.dp)
                        Spacer(Modifier.width(Space.lg))
                        Column(Modifier.weight(1f)) {
                            Text(crop.name, style = AgroTheme.type.displayMd, color = c.ink)
                            Spacer(Modifier.height(Space.xs))
                            when {
                                model != null -> Pill(stringResource(R.string.model_installed, model.versionLabel), c.positivePale, c.positiveDeep)
                                download is DownloadState.Running || download is DownloadState.Queued ->
                                    Pill(stringResource(R.string.model_downloading), c.warningPale, c.warningContent)
                                else -> Pill(stringResource(R.string.model_not_installed), c.surfaceSoft, c.body)
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.lg))
                    when {
                        model != null || BuildConfig.DEBUG -> PrimaryButton(
                            stringResource(R.string.crop_scan, crop.name), { onScan(cropId) },
                            Modifier.fillMaxWidth(), icon = Icons.Outlined.CameraAlt,
                        )
                        download is DownloadState.Running || download is DownloadState.Queued -> Unit
                        else -> PrimaryButton(
                            stringResource(R.string.crop_install), { vm.install(cropId) },
                            Modifier.fillMaxWidth(), icon = Icons.Outlined.Download,
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.crop_problems)) }
            item { ConditionList(diseases, vm, onOpenDisease) }
            if (healthy.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.crop_healthy_section)) }
                item { ConditionList(healthy, vm, onOpenDisease) }
            }
        }
    }
}

@Composable
private fun ConditionList(labels: List<String>, vm: LibraryViewModel, onOpen: (String) -> Unit) {
    AgroCard(padding = false) {
        Column(Modifier.padding(horizontal = Space.lg)) {
            labels.forEachIndexed { i, label ->
                val info = vm.content.disease(label)
                ListRow(
                    title = info.name,
                    subtitle = info.summary.takeIf { it.isNotBlank() },
                    showChevron = true,
                    divider = i < labels.lastIndex,
                    onClick = { onOpen(label) },
                )
            }
        }
    }
}

// ─── Disease detail ─────────────────────────────────────────────────────────

@Composable
fun DiseaseDetailScreen(label: String, onBack: () -> Unit) {
    val app = appContainer()
    val info = app.content.disease(label)
    val status = if (info.healthy) DiagnosisStatus.Healthy else DiagnosisStatus.Disease
    val colors = statusColors(status)
    val c = AgroTheme.colors
    val hasContent = info.symptoms.isNotEmpty() || !info.treatment.isEmpty || info.prevention.isNotEmpty()

    Column(Modifier.fillMaxSize().background(c.background)) {
        AgroTopBar(app.content.cropName(info.crop), onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(Radius.xl).background(colors.hero).padding(Space.lg)) {
                    StatusBadge(status)
                    Spacer(Modifier.height(Space.md))
                    Text(info.name, style = AgroTheme.type.displayXl, color = colors.onHero)
                    if (info.summary.isNotBlank()) {
                        Spacer(Modifier.height(Space.sm))
                        Text(info.summary, style = AgroTheme.type.bodyLg, color = colors.onHero.copy(alpha = 0.9f))
                    }
                }
            }
            if (!hasContent) {
                item {
                    EmptyState(
                        emoji = "📝",
                        title = stringResource(R.string.disease_coming_soon_title),
                        body = stringResource(R.string.disease_coming_soon_body),
                    )
                }
            }
            section(R.string.disease_symptoms, info.symptoms)
            if (info.cause.isNotBlank()) {
                item {
                    AgroCard {
                        Text(stringResource(R.string.disease_cause), style = AgroTheme.type.displaySm, color = c.ink)
                        Spacer(Modifier.height(Space.sm))
                        Text(info.cause, style = AgroTheme.type.bodyMd, color = c.body)
                    }
                }
            }
            if (!info.treatment.isEmpty) {
                item { TreatmentCard(info) }
            }
            section(if (info.healthy) R.string.disease_care else R.string.disease_prevention, info.prevention)
            if (info.getHelp.isNotBlank()) {
                item {
                    FeatureCard(
                        title = stringResource(R.string.disease_get_help),
                        body = info.getHelp,
                        tone = CardTone.Dark,
                    )
                }
            }
            item {
                Text(
                    stringResource(if (info.reviewed) R.string.disclaimer else R.string.disclaimer_unreviewed),
                    style = AgroTheme.type.caption,
                    color = c.mute,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: Int, items: List<String>) {
    if (items.isEmpty()) return
    item {
        AgroCard {
            Text(stringResource(title), style = AgroTheme.type.displaySm, color = AgroTheme.colors.ink)
            Spacer(Modifier.height(Space.md))
            BulletList(items)
        }
    }
}

@Composable
private fun TreatmentCard(info: DiseaseInfo) {
    val c = AgroTheme.colors
    AgroCard {
        Text(stringResource(R.string.disease_treatment), style = AgroTheme.type.displaySm, color = c.ink)
        listOf(
            R.string.disease_treatment_cultural to info.treatment.cultural,
            R.string.disease_treatment_organic to info.treatment.organic,
            R.string.disease_treatment_chemical to info.treatment.chemical,
        ).filter { it.second.isNotEmpty() }.forEach { (title, steps) ->
            Spacer(Modifier.height(Space.lg))
            Text(stringResource(title), style = AgroTheme.type.bodyMdStrong, color = c.ink)
            Spacer(Modifier.height(Space.sm))
            BulletList(steps)
        }
    }
}
