package com.agroai.app.feature.settings

import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
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
import com.agroai.app.data.models.InstalledModel
import com.agroai.app.feature.onboarding.AgroSwitch
import com.agroai.app.feature.onboarding.StickyFooter
import com.agroai.app.ml.DiagnosisStatus
import com.agroai.app.ui.components.AgroBottomSheet
import com.agroai.app.ui.components.AgroCard
import com.agroai.app.ui.components.AgroSnackbarHost
import com.agroai.app.ui.components.AgroTopBar
import com.agroai.app.ui.components.BulletList
import com.agroai.app.ui.components.CardTone
import com.agroai.app.ui.components.ConfidenceBar
import com.agroai.app.ui.components.CropAvatar
import com.agroai.app.ui.components.CropChip
import com.agroai.app.ui.components.CropTile
import com.agroai.app.ui.components.EmptyState
import com.agroai.app.ui.components.FeatureCard
import com.agroai.app.ui.components.IconCircleButton
import com.agroai.app.ui.components.IconTile
import com.agroai.app.ui.components.ListRow
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.ScreenTitle
import com.agroai.app.ui.components.SecondaryButton
import com.agroai.app.ui.components.SectionHeader
import com.agroai.app.ui.components.StatusBadge
import com.agroai.app.ui.components.TertiaryButton
import com.agroai.app.ui.components.percent
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    val content = app.content
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val installed = app.models.installed
    val manifest = app.models.manifest
    val storage = MutableStateFlow(0L to 0L)
    val messages = MutableStateFlow<Int?>(null)

    /** Crops the farmer grows plus anything installed, in catalogue order. */
    val myCrops = combine(app.settings.settings, app.models.installed) { s, installed ->
        app.content.crops.filter { it.id in s.crops || it.id in installed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshStorage() {
        storage.value = app.models.bytesUsed() to app.history.bytesUsed()
    }

    fun refreshManifest() = viewModelScope.launch { app.models.refreshManifest() }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch { app.settings.setWifiOnly(value) }

    fun clearHistory() = viewModelScope.launch {
        app.history.clear()
        refreshStorage()
        messages.value = R.string.settings_history_cleared
    }

    fun download(crop: String) = viewModelScope.launch {
        app.models.enqueueDownload(crop, app.settings.current().wifiOnly)
    }

    fun remove(crop: String) = viewModelScope.launch {
        app.models.remove(crop)
        app.settings.removeCrop(crop)
        refreshStorage()
    }

    fun downloadState(crop: String) = app.models.downloadState(crop)
    fun published(crop: String) = app.models.latest(crop) != null
    fun updateAvailable(crop: String) = app.models.updateAvailable(crop)
    fun size(crop: String) = app.models.downloadBytes(crop)

    fun importLocal(crop: String, model: Uri, labels: Uri) = viewModelScope.launch {
        messages.value = try {
            app.models.importLocal(crop, model, labels)
            app.settings.addCrop(crop)
            R.string.dev_import_done
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "import failed", e)
            R.string.dev_import_failed
        }
    }
}

@Composable
private fun MessageSnackbar(vm: SettingsViewModel, host: SnackbarHostState) {
    val message by vm.messages.collectAsStateWithLifecycle()
    message?.let { res ->
        val text = stringResource(res)
        LaunchedEffect(res) {
            host.showSnackbar(text)
            vm.messages.value = null
        }
    }
}

// ─── Settings ───────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(onManageCrops: () -> Unit, onAbout: () -> Unit, onGallery: () -> Unit) {
    val app = appContainer()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(app) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<String?>(null) }
    val languageSoon = stringResource(R.string.settings_language_soon)
    val c = AgroTheme.colors

    LaunchedEffect(Unit) { vm.refreshStorage() }
    MessageSnackbar(vm, snackbar)

    // Debug: crop -> .tflite -> labels.txt
    var importCrop by remember { mutableStateOf<String?>(null) }
    var importModel by remember { mutableStateOf<Uri?>(null) }
    val pickLabels = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val crop = importCrop
        val model = importModel
        if (uri != null && crop != null && model != null) vm.importLocal(crop, model, uri)
        importCrop = null
        importModel = null
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            importCrop = null
        } else {
            importModel = uri
            pickLabels.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xl),
        ) {
            item { ScreenTitle(stringResource(R.string.settings_title), inset = false) }

            item {
                AgroCard(padding = false) {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        ListRow(
                            stringResource(R.string.settings_manage_crops),
                            subtitle = stringResource(R.string.settings_manage_crops_body),
                            leading = { IconTile(Icons.Outlined.Eco) },
                            showChevron = true,
                            onClick = onManageCrops,
                        )
                        ListRow(
                            stringResource(R.string.settings_wifi_only),
                            subtitle = stringResource(R.string.settings_wifi_only_body),
                            leading = { IconTile(Icons.Outlined.Wifi) },
                            trailing = { AgroSwitch(settings?.wifiOnly ?: true, vm::setWifiOnly) },
                        )
                        ListRow(
                            stringResource(R.string.settings_language),
                            subtitle = stringResource(R.string.settings_language_value),
                            leading = { IconTile(Icons.Outlined.Language) },
                            showChevron = true,
                            divider = false,
                            onClick = { scope.launch { snackbar.showSnackbar(languageSoon) } },
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_storage)) }
            item {
                AgroCard(padding = false) {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        ListRow(
                            stringResource(R.string.settings_storage_models),
                            trailing = { Text(Formatter.formatShortFileSize(context, storage.first), style = AgroTheme.type.bodyMd, color = c.body) },
                        )
                        ListRow(
                            stringResource(R.string.settings_storage_photos),
                            trailing = { Text(Formatter.formatShortFileSize(context, storage.second), style = AgroTheme.type.bodyMd, color = c.body) },
                        )
                        ListRow(
                            stringResource(R.string.settings_clear_history),
                            leading = { IconTile(Icons.Outlined.Delete) },
                            titleColor = c.negative,
                            divider = false,
                            onClick = { sheet = "clear" },
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_about_section)) }
            item {
                AgroCard(padding = false) {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        ListRow(
                            stringResource(R.string.settings_about),
                            subtitle = stringResource(R.string.settings_about_body),
                            leading = { IconTile(Icons.Outlined.Info) },
                            showChevron = true,
                            onClick = onAbout,
                        )
                        ListRow(
                            stringResource(R.string.settings_privacy),
                            subtitle = stringResource(R.string.settings_privacy_body),
                            leading = { IconTile(Icons.Outlined.Lock) },
                            showChevron = true,
                            divider = false,
                            onClick = { sheet = "privacy" },
                        )
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                item { SectionHeader(stringResource(R.string.dev_section)) }
                item {
                    AgroCard(padding = false) {
                        Column(Modifier.padding(horizontal = Space.lg)) {
                            ListRow(
                                stringResource(R.string.dev_import),
                                subtitle = stringResource(R.string.dev_import_body),
                                leading = { IconTile(Icons.Outlined.FileOpen) },
                                showChevron = true,
                                onClick = { sheet = "import" },
                            )
                            ListRow(
                                stringResource(R.string.dev_gallery),
                                leading = { IconTile(Icons.Outlined.Palette) },
                                showChevron = true,
                                divider = false,
                                onClick = onGallery,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = AgroTheme.type.caption,
                    color = c.mute,
                    modifier = Modifier.padding(top = Space.xl),
                )
            }
        }
        AgroSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (sheet) {
        "clear" -> AgroBottomSheet(onDismiss = { sheet = null }) {
            Text(stringResource(R.string.settings_clear_title), style = AgroTheme.type.displayMd, color = c.ink)
            Spacer(Modifier.height(Space.sm))
            Text(stringResource(R.string.settings_clear_body), style = AgroTheme.type.bodyMd, color = c.body)
            Spacer(Modifier.height(Space.xl))
            TertiaryButton(stringResource(R.string.settings_clear_confirm), { sheet = null; vm.clearHistory() }, Modifier.fillMaxWidth(), contentColor = c.negative)
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(stringResource(R.string.action_cancel), { sheet = null }, Modifier.fillMaxWidth())
        }
        "privacy" -> AgroBottomSheet(onDismiss = { sheet = null }) {
            Text(stringResource(R.string.settings_privacy), style = AgroTheme.type.displayMd, color = c.ink)
            Spacer(Modifier.height(Space.md))
            BulletList(stringArrayResource(R.array.privacy_points).toList())
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(stringResource(R.string.action_got_it), { sheet = null }, Modifier.fillMaxWidth())
        }
        "import" -> AgroBottomSheet(onDismiss = { sheet = null }) {
            Text(stringResource(R.string.dev_import_pick_crop), style = AgroTheme.type.displayMd, color = c.ink)
            Spacer(Modifier.height(Space.md))
            vm.content.crops.forEachIndexed { i, crop ->
                ListRow(
                    crop.name,
                    leading = { CropAvatar(crop) },
                    divider = i < vm.content.crops.lastIndex,
                    onClick = {
                        sheet = null
                        importCrop = crop.id
                        pickModel.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                )
            }
        }
    }
}

// ─── Manage crops & models ──────────────────────────────────────────────────

@Composable
fun ManageCropsScreen(onBack: () -> Unit, onAddCrop: () -> Unit) {
    val app = appContainer()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(app) }
    val crops by vm.myCrops.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    vm.manifest.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Crop?>(null) }
    val c = AgroTheme.colors

    LaunchedEffect(Unit) { vm.refreshManifest() }

    Column(Modifier.fillMaxSize().background(c.background)) {
        AgroTopBar(stringResource(R.string.settings_manage_crops), onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item {
                Text(stringResource(R.string.manage_body), style = AgroTheme.type.bodyLg, color = c.body)
            }
            if (crops.isEmpty()) {
                item {
                    EmptyState("🌱", stringResource(R.string.manage_empty_title), stringResource(R.string.manage_empty_body))
                }
            }
            crops.forEach { crop ->
                item(key = crop.id) {
                    ModelCard(crop, installed[crop.id], vm, onRemove = { removing = crop })
                }
            }
        }
        StickyFooter {
            PrimaryButton(stringResource(R.string.manage_add), onAddCrop, Modifier.fillMaxWidth())
        }
    }

    removing?.let { crop ->
        AgroBottomSheet(onDismiss = { removing = null }) {
            Text(stringResource(R.string.manage_remove_title, crop.name), style = AgroTheme.type.displayMd, color = c.ink)
            Spacer(Modifier.height(Space.sm))
            Text(stringResource(R.string.manage_remove_body), style = AgroTheme.type.bodyMd, color = c.body)
            Spacer(Modifier.height(Space.xl))
            TertiaryButton(stringResource(R.string.action_remove), { vm.remove(crop.id); removing = null }, Modifier.fillMaxWidth(), contentColor = c.negative)
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(stringResource(R.string.action_cancel), { removing = null }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ModelCard(crop: Crop, model: InstalledModel?, vm: SettingsViewModel, onRemove: () -> Unit) {
    val context = LocalContext.current
    val stateFlow = remember(crop.id) { vm.downloadState(crop.id) }
    val state by stateFlow.collectAsStateWithLifecycle(DownloadState.Idle)
    val c = AgroTheme.colors
    val busy = state is DownloadState.Running || state is DownloadState.Queued

    val subtitle = when {
        state is DownloadState.Running -> stringResource(R.string.download_progress, ((state as DownloadState.Running).fraction * 100).toInt())
        state is DownloadState.Queued -> stringResource(R.string.download_waiting)
        model != null -> buildString {
            append(model.versionLabel)
            append(" · ")
            append(Formatter.formatShortFileSize(context, model.bytes))
            model.accuracy?.let { append(" · ").append(context.getString(R.string.manage_accuracy, percent(it.toFloat()))) }
        }
        state is DownloadState.Failed -> stringResource(R.string.download_failed)
        !vm.published(crop.id) -> stringResource(R.string.download_not_published)
        else -> vm.size(crop.id)?.let { stringResource(R.string.manage_not_downloaded_size, Formatter.formatShortFileSize(context, it)) }
            ?: stringResource(R.string.model_not_installed)
    }

    AgroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CropAvatar(crop, size = 48.dp)
            Spacer(Modifier.padding(start = Space.md))
            Column(Modifier.weight(1f)) {
                Text(crop.name, style = AgroTheme.type.bodyMdStrong, color = c.ink)
                Text(subtitle, style = AgroTheme.type.bodySm, color = c.body)
            }
            IconCircleButton(Icons.Outlined.Delete, stringResource(R.string.action_remove), onRemove, container = c.surfaceSoft)
        }
        if (busy) {
            Spacer(Modifier.height(Space.md))
            val fraction = (state as? DownloadState.Running)?.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().clip(Radius.pill), color = c.primary, trackColor = c.surfaceSoft)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(Radius.pill), color = c.primary, trackColor = c.surfaceSoft)
            }
        } else if (vm.published(crop.id) && (model == null || vm.updateAvailable(crop.id))) {
            Spacer(Modifier.height(Space.md))
            PrimaryButton(
                stringResource(if (model == null) R.string.action_download else R.string.action_update),
                { vm.download(crop.id) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── About ──────────────────────────────────────────────────────────────────

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val app = appContainer()
    val installed by app.models.installed.collectAsStateWithLifecycle()
    val c = AgroTheme.colors

    Column(Modifier.fillMaxSize().background(c.background)) {
        AgroTopBar(stringResource(R.string.settings_about), onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item {
                FeatureCard(
                    title = stringResource(R.string.about_title),
                    body = stringResource(R.string.about_body),
                    tone = CardTone.Dark,
                )
            }
            item {
                AgroCard {
                    Text(stringResource(R.string.about_accuracy), style = AgroTheme.type.displaySm, color = c.ink)
                    Spacer(Modifier.height(Space.sm))
                    Text(stringResource(R.string.about_accuracy_body), style = AgroTheme.type.bodySm, color = c.body)
                    Spacer(Modifier.height(Space.md))
                    val measured = installed.values.filter { it.accuracy != null }
                    if (measured.isEmpty()) {
                        Text(stringResource(R.string.about_no_models), style = AgroTheme.type.bodyMd, color = c.body)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                            measured.forEach { ConfidenceBar(app.content.cropName(it.crop), it.accuracy!!.toFloat(), color = c.positive) }
                        }
                    }
                }
            }
            item {
                AgroCard {
                    Text(stringResource(R.string.about_sources), style = AgroTheme.type.displaySm, color = c.ink)
                    Spacer(Modifier.height(Space.md))
                    BulletList(stringArrayResource(R.array.data_sources).toList())
                }
            }
            item {
                Text(stringResource(R.string.disclaimer), style = AgroTheme.type.caption, color = c.mute)
            }
        }
    }
}

// ─── Component gallery (debug only) ─────────────────────────────────────────

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val app = appContainer()
    val crop = app.content.crops.first()
    var selected by remember { mutableStateOf(true) }
    val c = AgroTheme.colors

    Column(Modifier.fillMaxSize().background(c.background)) {
        AgroTopBar(stringResource(R.string.dev_gallery), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text("Display XXL", style = AgroTheme.type.displayXxl, color = c.ink)
            Text("Display XL", style = AgroTheme.type.displayXl, color = c.ink)
            Text("Display MD", style = AgroTheme.type.displayMd, color = c.ink)
            Text("Section heading", style = AgroTheme.type.displaySm, color = c.ink)
            Text("Body text for lists and paragraphs.", style = AgroTheme.type.bodyMd, color = c.body)
            PrimaryButton("Primary", {}, Modifier.fillMaxWidth(), icon = Icons.Outlined.CameraAlt)
            SecondaryButton("Secondary", {}, Modifier.fillMaxWidth())
            TertiaryButton("Tertiary", {}, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                StatusBadge(DiagnosisStatus.Healthy)
                StatusBadge(DiagnosisStatus.Disease)
                StatusBadge(DiagnosisStatus.Uncertain)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CropChip(crop.name, selected, { selected = !selected }, emoji = crop.emoji)
                CropChip("Other", !selected, { selected = !selected })
            }
            CropTile(crop, selected, { selected = !selected }, Modifier.fillMaxWidth(0.5f), subtitle = "4.1 MB")
            AgroCard {
                ConfidenceBar("Mosaic disease", 0.87f, color = c.negative)
                Spacer(Modifier.height(Space.md))
                ConfidenceBar("Healthy", 0.09f, color = c.mute)
            }
            FeatureCard("Sage card", "Tips and banners.", tone = CardTone.Sage)
            FeatureCard("Green card", "Positive moments.", tone = CardTone.Green)
            FeatureCard("Dark card", "High-emphasis promos.", tone = CardTone.Dark, eyebrow = "Eyebrow")
            EmptyState("🌱", "Empty state", "Shown when a list has nothing yet.")
            IconCircleButton(Icons.Outlined.BugReport, null, {})
        }
    }
}
