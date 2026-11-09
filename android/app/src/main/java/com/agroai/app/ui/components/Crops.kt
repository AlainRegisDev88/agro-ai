package com.agroai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agroai.app.data.content.Crop
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space

@Composable
fun CropAvatar(crop: Crop, modifier: Modifier = Modifier, size: Dp = 40.dp, container: Color = AgroTheme.colors.surfaceSoft) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Text(crop.emoji, style = if (size >= 48.dp) AgroTheme.type.displayMd else AgroTheme.type.bodyLg)
    }
}

/** Selectable grid tile used when choosing crops. */
@Composable
fun CropTile(
    crop: Crop,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = AgroTheme.colors
    Box(
        modifier
            .clip(Radius.xl)
            .background(if (selected) c.primaryPale else c.surface)
            .border(BorderStroke(2.dp, if (selected) c.primary else Color.Transparent), Radius.xl)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(Space.lg),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CropAvatar(crop, size = 56.dp, container = if (selected) c.surface else c.surfaceSoft)
            Spacer(Modifier.size(Space.sm))
            Text(crop.name, style = AgroTheme.type.bodyMdStrong, color = c.ink, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = AgroTheme.type.caption, color = c.body, textAlign = TextAlign.Center)
            }
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Pill chip for crop filters and the "my crops" row. */
@Composable
fun CropChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
) {
    val c = AgroTheme.colors
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(Radius.pill)
            .background(if (selected) c.ink else c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) {
            Text(emoji, style = AgroTheme.type.bodyMd)
            Spacer(Modifier.width(Space.xs + 2.dp))
        }
        Text(label, style = AgroTheme.type.bodySmStrong, color = if (selected) c.surface else c.ink)
    }
}
