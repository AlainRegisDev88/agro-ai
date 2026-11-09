package com.agroai.app.feature.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agroai.app.AppContainer
import com.agroai.app.R
import com.agroai.app.appContainer
import com.agroai.app.data.history.Scan
import com.agroai.app.ui.components.AgroCard
import com.agroai.app.ui.components.AgroSnackbarHost
import com.agroai.app.ui.components.CropChip
import com.agroai.app.ui.components.EmptyState
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.ScreenTitle
import com.agroai.app.ui.components.SectionHeader
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HistoryGroup(val dayStart: Long, val scans: List<Scan>)

data class HistoryState(
    val loading: Boolean = true,
    val total: Int = 0,
    val crops: List<String> = emptyList(),
    val filter: String? = null,
    val groups: List<HistoryGroup> = emptyList(),
)

class HistoryViewModel(private val app: AppContainer) : ViewModel() {
    val content = app.content
    private val filter = MutableStateFlow<String?>(null)

    val state = combine(app.history.all(), filter) { scans, f ->
        val shown = if (f == null) scans else scans.filter { it.crop == f }
        HistoryState(
            loading = false,
            total = scans.size,
            crops = scans.map { it.crop }.distinct(),
            filter = f,
            groups = shown.groupBy { startOfDay(it.createdAt) }.map { (day, list) -> HistoryGroup(day, list) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun setFilter(crop: String?) {
        filter.value = crop
    }

    fun delete(scan: Scan) = viewModelScope.launch { app.history.delete(scan.id) }
    fun restore(scan: Scan) = viewModelScope.launch { app.history.add(scan) }
    fun purge(scan: Scan) = app.history.purgePhoto(scan)

    private fun startOfDay(time: Long): Long = Calendar.getInstance().run {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, onScan: () -> Unit) {
    val app = appContainer()
    val vm: HistoryViewModel = viewModel { HistoryViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val c = AgroTheme.colors

    val onDelete: (Scan) -> Unit = { scan ->
        vm.delete(scan)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.restore(scan) else vm.purge(scan)
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = Space.xl),
        ) {
            item { ScreenTitle(stringResource(R.string.history_title)) }

            if (state.crops.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        item { CropChip(stringResource(R.string.history_all), state.filter == null, { vm.setFilter(null) }) }
                        items(state.crops) { crop ->
                            val info = vm.content.crop(crop)
                            CropChip(vm.content.cropName(crop), state.filter == crop, { vm.setFilter(crop) }, emoji = info?.emoji)
                        }
                    }
                }
            }

            if (!state.loading && state.total == 0) {
                item {
                    EmptyState(
                        emoji = "🌱",
                        title = stringResource(R.string.history_empty_title),
                        body = stringResource(R.string.history_empty_body),
                        modifier = Modifier.padding(Space.gutter),
                        action = { PrimaryButton(stringResource(R.string.home_scan_cta), onScan) },
                    )
                }
            }

            state.groups.forEach { group ->
                item(key = group.dayStart) {
                    Column(Modifier.padding(horizontal = Space.gutter)) {
                        SectionHeader(dayLabel(group.dayStart))
                        AgroCard(padding = false) {
                            group.scans.forEachIndexed { i, scan ->
                                // Keyed so a deleted row's swipe state doesn't carry over to the next scan.
                                key(scan.id) {
                                    SwipeToDelete(onDelete = { onDelete(scan) }) {
                                        Box(Modifier.background(c.surface).padding(horizontal = Space.lg)) {
                                            ScanRow(scan, vm.content, { onOpen(scan.id) }, divider = i < group.scans.lastIndex)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AgroSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = AgroTheme.colors
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
            it == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(c.negative).padding(horizontal = Space.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete), tint = c.surface)
            }
        },
    ) { content() }
}

@Composable
private fun dayLabel(dayStart: Long): String = when {
    DateUtils.isToday(dayStart) -> stringResource(R.string.history_today)
    DateUtils.isToday(dayStart + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.history_yesterday)
    else -> DateUtils.formatDateTime(
        LocalContext.current, dayStart,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_MONTH,
    )
}
