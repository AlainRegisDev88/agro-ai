package com.agroai.app.feature.onboarding

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.agroai.app.data.models.DownloadState
import com.agroai.app.ui.components.AgroCard
import com.agroai.app.ui.components.AgroTopBar
import com.agroai.app.ui.components.CardTone
import com.agroai.app.ui.components.CropAvatar
import com.agroai.app.ui.components.CropTile
import com.agroai.app.ui.components.FeatureCard
import com.agroai.app.ui.components.ListRow
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.SecondaryButton
import com.agroai.app.ui.components.SectionHeader
import com.agroai.app.ui.components.TertiaryButton
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ─── Welcome ────────────────────────────────────────────────────────────────

private data class WelcomePage(val emoji: String, val title: Int, val body: Int)

private val welcomePages = listOf(
    WelcomePage("🌿", R.string.welcome_1_title, R.string.welcome_1_body),
    WelcomePage("📶", R.string.welcome_2_title, R.string.welcome_2_body),
    WelcomePage("🧑‍🌾", R.string.welcome_3_title, R.string.welcome_3_body),
)

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val pager = rememberPagerState { welcomePages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == welcomePages.lastIndex
    val c = AgroTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = welcomePages[index]
            Column(Modifier.fillMaxSize().padding(horizontal = Space.gutter)) {
                Spacer(Modifier.height(Space.x3l))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(Radius.xl)
                        .background(if (index == 0) c.heroDark else c.primaryPale),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(140.dp).clip(CircleShape).background(c.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(page.emoji, style = AgroTheme.type.displayMega)
                    }
                }
                Spacer(Modifier.height(Space.x2l))
                Text(stringResource(page.title), style = AgroTheme.type.displayXxl, color = c.ink)
                Spacer(Modifier.height(Space.md))
                Text(stringResource(page.body), style = AgroTheme.type.bodyLg, color = c.body)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.lg),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(welcomePages.size) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(8.dp)
                        .width(if (i == pager.currentPage) 24.dp else 8.dp)
                        .clip(Radius.pill)
                        .background(if (i == pager.currentPage) c.ink else c.secondaryFill),
                )
            }
        }

        Column(Modifier.padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            PrimaryButton(
                text = stringResource(if (last) R.string.welcome_get_started else R.string.action_next),
                onClick = { if (last) onStart() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!last) {
                SecondaryButton(stringResource(R.string.action_skip), onStart, Modifier.fillMaxWidth())
            }
        }
    }
}

// ─── Choose crops ───────────────────────────────────────────────────────────

class ChooseCropsViewModel(private val app: AppContainer) : ViewModel() {
    val crops: List<Crop> = app.content.crops
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val manifest = app.models.manifest

    init {
        viewModelScope.launch {
            selected.value = app.settings.current().crops
            app.models.refreshManifest()
        }
    }

    fun toggle(crop: String) {
        selected.value = selected.value.let { if (crop in it) it - crop else it + crop }
    }

    fun sizeOf(crop: String): Long? = app.models.downloadBytes(crop)

    /** Saves the selection. From Settings, also starts downloads for newly added crops. */
    fun save(fromSettings: Boolean, done: () -> Unit) = viewModelScope.launch {
        val before = app.settings.current()
        val chosen = selected.value
        app.settings.setCrops(chosen)
        if (fromSettings) {
            (chosen - before.crops).forEach { app.models.enqueueDownload(it, before.wifiOnly) }
        }
        done()
    }
}

@Composable
fun ChooseCropsScreen(fromSettings: Boolean, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val app = appContainer()
    val vm: ChooseCropsViewModel = viewModel { ChooseCropsViewModel(app) }
    val selected by vm.selected.collectAsStateWithLifecycle()
    vm.manifest.collectAsStateWithLifecycle() // recompose when sizes become known
    val context = LocalContext.current
    val c = AgroTheme.colors

    val popular = vm.crops.filter { it.group == "east_africa" }
    val more = vm.crops.filter { it.group != "east_africa" }

    Column(Modifier.fillMaxSize().background(c.background)) {
        if (onBack != null) {
            AgroTopBar(stringResource(R.string.choose_title_short), onBack = onBack)
        } else {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.choose_title), style = AgroTheme.type.displayXl, color = c.ink)
                    Spacer(Modifier.height(Space.sm))
                    Text(stringResource(R.string.choose_body), style = AgroTheme.type.bodyLg, color = c.body)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.choose_popular)) }
            items(popular, key = { it.id }) { crop ->
                CropTile(
                    crop, crop.id in selected, { vm.toggle(crop.id) },
                    subtitle = vm.sizeOf(crop.id)?.let { Formatter.formatShortFileSize(context, it) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.choose_more)) }
            items(more, key = { it.id }) { crop ->
                CropTile(
                    crop, crop.id in selected, { vm.toggle(crop.id) },
                    subtitle = vm.sizeOf(crop.id)?.let { Formatter.formatShortFileSize(context, it) },
                )
            }
        }
        StickyFooter {
            PrimaryButton(
                text = if (selected.isEmpty()) stringResource(R.string.choose_pick_one)
                else pluralStringResource(R.plurals.choose_continue, selected.size, selected.size),
                onClick = { vm.save(fromSettings, onDone) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun StickyFooter(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(AgroTheme.colors.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(Space.gutter),
    ) { content() }
}

// ─── Download models ────────────────────────────────────────────────────────

sealed interface ManifestStatus {
    data object Loading : ManifestStatus
    data object Ready : ManifestStatus
    data object Unreachable : ManifestStatus
}

class DownloadModelsViewModel(private val app: AppContainer) : ViewModel() {
    val manifestStatus = MutableStateFlow<ManifestStatus>(ManifestStatus.Loading)
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val installed = app.models.installed

    val crops: StateFlow<List<Crop>> = app.settings.settings
        .map { s -> app.content.crops.filter { it.id in s.crops } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        manifestStatus.value = ManifestStatus.Loading
        val ok = app.models.refreshManifest().isSuccess || app.models.manifest.value != null
        manifestStatus.value = if (ok) ManifestStatus.Ready else ManifestStatus.Unreachable
        if (ok) startAll()
    }

    private suspend fun startAll() {
        val s = app.settings.current()
        s.crops.filter { app.models.latest(it) != null && it !in installed.value }
            .forEach { app.models.enqueueDownload(it, s.wifiOnly) }
    }

    fun retry(crop: String) = viewModelScope.launch {
        app.models.cancelDownload(crop)
        app.models.enqueueDownload(crop, app.settings.current().wifiOnly)
    }

    /** Changing the network rule restarts pending downloads with the new constraint. */
    fun setWifiOnly(value: Boolean) = viewModelScope.launch {
        app.settings.setWifiOnly(value)
        app.settings.current().crops.filter { it !in installed.value && app.models.latest(it) != null }.forEach {
            app.models.cancelDownload(it)
            app.models.enqueueDownload(it, value)
        }
    }

    fun state(crop: String): Flow<DownloadState> = app.models.downloadState(crop)
    fun published(crop: String) = app.models.latest(crop) != null
    fun size(crop: String) = app.models.downloadBytes(crop)

    fun finish(done: () -> Unit) = viewModelScope.launch {
        app.settings.setOnboarded(true)
        done()
    }
}

@Composable
fun DownloadModelsScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val app = appContainer()
    val vm: DownloadModelsViewModel = viewModel { DownloadModelsViewModel(app) }
    val crops by vm.crops.collectAsStateWithLifecycle()
    val status by vm.manifestStatus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = AgroTheme.colors

    val totalBytes = crops.sumOf { vm.size(it.id) ?: 0L }
    val anyReady = crops.any { it.id in installed }

    Column(Modifier.fillMaxSize().background(c.background)) {
        AgroTopBar(stringResource(R.string.download_title_short), onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item {
                Text(stringResource(R.string.download_title), style = AgroTheme.type.displayXl, color = c.ink)
                Spacer(Modifier.height(Space.sm))
                Text(
                    if (totalBytes > 0) stringResource(R.string.download_body_size, Formatter.formatShortFileSize(context, totalBytes))
                    else stringResource(R.string.download_body),
                    style = AgroTheme.type.bodyLg,
                    color = c.body,
                )
            }
            item {
                AgroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_wifi_only), style = AgroTheme.type.bodyMdStrong, color = c.ink)
                            Text(stringResource(R.string.settings_wifi_only_body), style = AgroTheme.type.bodySm, color = c.body)
                        }
                        AgroSwitch(settings?.wifiOnly ?: true, vm::setWifiOnly)
                    }
                }
            }
            when (status) {
                ManifestStatus.Loading -> item {
                    AgroCard { LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.ink, trackColor = c.surfaceSoft) }
                }
                ManifestStatus.Unreachable -> item {
                    FeatureCard(
                        title = stringResource(R.string.download_unreachable_title),
                        body = stringResource(
                            if (BuildConfig.DEBUG) R.string.download_unreachable_body_demo else R.string.download_unreachable_body,
                        ),
                        tone = CardTone.Sage,
                        action = { TertiaryButton(stringResource(R.string.action_try_again), { vm.refresh() }) },
                    )
                }
                ManifestStatus.Ready -> Unit
            }
            item {
                AgroCard {
                    crops.forEachIndexed { i, crop ->
                        DownloadRow(
                            crop = crop,
                            installed = crop.id in installed,
                            published = status != ManifestStatus.Ready || vm.published(crop.id),
                            stateFlow = remember(crop.id) { vm.state(crop.id) },
                            wifiOnly = settings?.wifiOnly ?: true,
                            onRetry = { vm.retry(crop.id) },
                            divider = i < crops.lastIndex,
                        )
                    }
                }
            }
        }
        StickyFooter {
            PrimaryButton(
                text = stringResource(if (anyReady || !BuildConfig.DEBUG) R.string.action_continue else R.string.download_continue_demo),
                onClick = { vm.finish(onDone) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun DownloadRow(
    crop: Crop,
    installed: Boolean,
    published: Boolean,
    stateFlow: Flow<DownloadState>,
    wifiOnly: Boolean,
    onRetry: () -> Unit,
    divider: Boolean,
) {
    val state by stateFlow.collectAsState(DownloadState.Idle)
    val c = AgroTheme.colors
    val subtitle = when {
        installed -> stringResource(R.string.download_ready)
        !published -> stringResource(R.string.download_not_published)
        state is DownloadState.Running -> stringResource(R.string.download_progress, ((state as DownloadState.Running).fraction * 100).toInt())
        state is DownloadState.Queued -> stringResource(if (wifiOnly) R.string.download_waiting_wifi else R.string.download_waiting)
        state is DownloadState.Failed -> stringResource(R.string.download_failed)
        else -> stringResource(R.string.download_waiting)
    }
    Column {
        ListRow(
            title = crop.name,
            subtitle = subtitle,
            leading = { CropAvatar(crop) },
            divider = divider && state !is DownloadState.Running,
            trailing = {
                when {
                    installed -> Text("✓", style = AgroTheme.type.displaySm, color = c.positive)
                    state is DownloadState.Failed -> TertiaryButton(stringResource(R.string.action_retry), onRetry)
                    else -> Unit
                }
            },
        )
        if (!installed && state is DownloadState.Running) {
            LinearProgressIndicator(
                progress = { (state as DownloadState.Running).fraction },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.md).clip(Radius.pill),
                color = c.primary,
                trackColor = c.surfaceSoft,
            )
        }
    }
}

@Composable
fun AgroSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AgroTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            checkedBorderColor = c.primary,
            uncheckedThumbColor = c.mute,
            uncheckedTrackColor = c.surfaceSoft,
            uncheckedBorderColor = c.mute,
        ),
    )
}
