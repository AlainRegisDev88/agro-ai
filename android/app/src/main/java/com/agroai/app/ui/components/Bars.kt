package com.agroai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.agroai.app.R
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space

/** Sticky sage top bar with an optional back button and actions. */
@Composable
fun AgroTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    background: Color = AgroTheme.colors.background,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconCircleButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.action_back),
                onBack,
                container = Color.Transparent,
            )
        } else {
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            title,
            style = AgroTheme.type.bodyMdStrong,
            color = AgroTheme.colors.ink,
            modifier = Modifier.weight(1f).padding(horizontal = Space.sm),
        )
        actions()
    }
}

data class TabItem(val key: String, val label: String, val icon: ImageVector)

/**
 * White bottom bar. The centre slot is the lime Scan button; the other tabs show a
 * lime indicator pill when active.
 */
@Composable
fun AgroBottomBar(
    tabs: List<TabItem>,
    selected: String,
    onSelect: (String) -> Unit,
    scanLabel: String,
    onScan: () -> Unit,
    scanIcon: ImageVector,
) {
    val c = AgroTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(color = c.divider, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp)
                .padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            val half = tabs.size / 2
            tabs.take(half).forEach { TabButton(it, it.key == selected, onSelect) }
            ScanTab(scanLabel, scanIcon, onScan)
            tabs.drop(half).forEach { TabButton(it, it.key == selected, onSelect) }
        }
    }
}

@Composable
private fun RowScope.TabButton(item: TabItem, active: Boolean, onSelect: (String) -> Unit) {
    val c = AgroTheme.colors
    Column(
        Modifier
            .weight(1f)
            .clip(Radius.lg)
            .clickable(role = Role.Tab) { onSelect(item.key) }
            .semantics { selected = active }
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(Radius.pill)
                .background(if (active) c.primary else Color.Transparent)
                .padding(horizontal = Space.lg, vertical = Space.xs),
        ) {
            Icon(item.icon, null, tint = if (active) c.onPrimary else c.mute, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(item.label, style = AgroTheme.type.caption, color = if (active) c.ink else c.mute)
    }
}

@Composable
private fun RowScope.ScanTab(label: String, icon: ImageVector, onScan: () -> Unit) {
    val c = AgroTheme.colors
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(c.primary)
                .clickable(role = Role.Button, onClick = onScan)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

/** Ink snackbar, 16dp radius. */
@Composable
fun AgroSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            data,
            shape = Radius.lg,
            containerColor = AgroTheme.colors.snackbar,
            contentColor = AgroTheme.colors.onSnackbar,
            actionColor = AgroTheme.colors.primary,
            modifier = Modifier.padding(Space.lg),
        )
    }
}

/** White bottom sheet with 24dp top corners and a sage drag handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgroBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = Radius.sheetTop,
        containerColor = AgroTheme.colors.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Space.md)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(Radius.pill)
                    .background(AgroTheme.colors.surfaceSoft),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xl),
            content = content,
        )
    }
}
