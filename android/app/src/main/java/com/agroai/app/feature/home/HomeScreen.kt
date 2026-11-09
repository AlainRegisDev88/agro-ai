package com.agroai.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agroai.app.AppContainer
import com.agroai.app.BuildConfig
import com.agroai.app.R
import com.agroai.app.appContainer
import com.agroai.app.data.content.Crop
import com.agroai.app.feature.history.ScanRow
import com.agroai.app.ui.components.AgroCard
import com.agroai.app.ui.components.CardTone
import com.agroai.app.ui.components.CropChip
import com.agroai.app.ui.components.EmptyState
import com.agroai.app.ui.components.FeatureCard
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.SectionHeader
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeState(
    val crops: List<Crop> = emptyList(),
    val installed: Set<String> = emptySet(),
    val updates: List<Crop> = emptyList(),
)

class HomeViewModel(private val app: AppContainer) : ViewModel() {
    val content = app.content
    val recent = app.history.recent(3).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state = combine(app.settings.settings, app.models.installed, app.models.manifest) { s, installed, _ ->
        val crops = app.content.crops.filter { it.id in s.crops }
        HomeState(
            crops = crops,
            installed = installed.keys,
            updates = crops.filter { app.models.updateAvailable(it.id) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Quietly checks for newer experts when Home opens. */
    fun checkForUpdates() = viewModelScope.launch { app.models.refreshManifest() }

    fun update(crops: List<Crop>) = viewModelScope.launch {
        val wifiOnly = app.settings.current().wifiOnly
        crops.forEach { app.models.enqueueDownload(it.id, wifiOnly) }
    }
}

@Composable
fun HomeScreen(
    onScan: (String?) -> Unit,
    onOpenScan: (Long) -> Unit,
    onSeeHistory: () -> Unit,
    onAddCrop: () -> Unit,
    onManageModels: () -> Unit,
    onOpenCrop: (String) -> Unit,
) {
    val app = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val tips = stringArrayResource(R.array.tips)
    val tip = tips[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % tips.size]
    val c = AgroTheme.colors

    LaunchedEffect(Unit) { vm.checkForUpdates() }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xl),
    ) {
        item {
            Column(Modifier.padding(top = Space.xl, bottom = Space.lg)) {
                Text(stringResource(R.string.app_name), style = AgroTheme.type.bodyMdStrong, color = c.body)
                Spacer(Modifier.height(Space.sm))
                Text(stringResource(R.string.home_title), style = AgroTheme.type.displayXxl, color = c.ink)
                Spacer(Modifier.height(Space.sm))
                Text(stringResource(R.string.home_body), style = AgroTheme.type.bodyLg, color = c.body)
                Spacer(Modifier.height(Space.xl))
                PrimaryButton(
                    stringResource(R.string.home_scan_cta),
                    onClick = { onScan(null) },
                    icon = Icons.Outlined.CameraAlt,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.installed.isEmpty()) {
            item {
                FeatureCard(
                    title = stringResource(if (BuildConfig.DEBUG) R.string.home_demo_title else R.string.home_no_models_title),
                    body = stringResource(if (BuildConfig.DEBUG) R.string.home_demo_body else R.string.home_no_models_body),
                    tone = CardTone.Sage,
                    onClick = onManageModels,
                    modifier = Modifier.padding(top = Space.sm),
                )
            }
        }

        if (state.updates.isNotEmpty()) {
            item {
                FeatureCard(
                    eyebrow = stringResource(R.string.home_update_eyebrow),
                    title = stringResource(R.string.home_update_title),
                    body = state.updates.joinToString { it.name },
                    tone = CardTone.Green,
                    modifier = Modifier.padding(top = Space.sm),
                    action = { PrimaryButton(stringResource(R.string.action_update), { vm.update(state.updates) }) },
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.home_my_crops))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                items(state.crops, key = { it.id }) { crop ->
                    CropChip(crop.name, selected = false, onClick = { onOpenCrop(crop.id) }, emoji = crop.emoji)
                }
                item { CropChip(stringResource(R.string.home_add_crop), selected = true, onClick = onAddCrop, emoji = "＋") }
            }
        }

        item {
            SectionHeader(stringResource(R.string.home_recent)) {
                if (!recent.isNullOrEmpty()) {
                    TextButton(onClick = onSeeHistory) {
                        Text(stringResource(R.string.action_see_all), style = AgroTheme.type.bodySmStrong, color = c.ink)
                    }
                }
            }
            val scans = recent
            when {
                scans == null -> Unit
                scans.isEmpty() -> EmptyState(
                    emoji = "📷",
                    title = stringResource(R.string.home_recent_empty_title),
                    body = stringResource(R.string.home_recent_empty_body),
                )
                else -> AgroCard(padding = false) {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        scans.forEachIndexed { i, scan ->
                            ScanRow(scan, vm.content, { onOpenScan(scan.id) }, divider = i < scans.lastIndex)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(Space.xl))
            FeatureCard(
                eyebrow = stringResource(R.string.home_tip_eyebrow),
                title = stringResource(R.string.home_tip_title),
                body = tip,
                tone = CardTone.Dark,
            )
        }
    }
}
