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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space

enum class CardTone { White, Sage, Green, Dark }

@Composable
private fun toneColors(tone: CardTone): Pair<Color, Color> {
    val c = AgroTheme.colors
    return when (tone) {
        CardTone.White -> c.surface to c.ink
        CardTone.Sage -> c.secondaryFill to c.ink
        CardTone.Green -> c.primaryPale to c.ink
        CardTone.Dark -> c.heroDark to c.onHeroDark
    }
}

/** 24dp rounded card. White on the sage canvas gives elevation without shadows. */
@Composable
fun AgroCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.White,
    onClick: (() -> Unit)? = null,
    padding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (bg, _) = toneColors(tone)
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.xl)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (padding) Modifier.padding(Space.lg) else Modifier),
        content = content,
    )
}

/** Feature card with a headline and body, used for tips, banners and promos. */
@Composable
fun FeatureCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Sage,
    eyebrow: String? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val (_, fg) = toneColors(tone)
    val bodyColor = if (tone == CardTone.Dark) fg.copy(alpha = 0.85f) else AgroTheme.colors.body
    AgroCard(modifier, tone, onClick) {
        if (eyebrow != null) {
            Text(eyebrow.uppercase(), style = AgroTheme.type.caption, color = bodyColor)
            Spacer(Modifier.size(Space.xs))
        }
        Text(title, style = AgroTheme.type.displayMd, color = fg)
        Spacer(Modifier.size(Space.sm))
        Text(body, style = AgroTheme.type.bodyMd, color = bodyColor)
        if (action != null) {
            Spacer(Modifier.size(Space.lg))
            action()
        }
    }
}

/** Full-bleed hero area at the top of a screen. */
@Composable
fun HeroBand(
    modifier: Modifier = Modifier,
    background: Color = AgroTheme.colors.background,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            .padding(start = Space.gutter, end = Space.gutter, top = Space.xl, bottom = Space.x2l),
        content = content,
    )
}

/** Big Wise-style screen title. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null, inset: Boolean = true) {
    Column(modifier.padding(horizontal = if (inset) Space.gutter else 0.dp, vertical = Space.lg)) {
        Text(text, style = AgroTheme.type.displayXl, color = AgroTheme.colors.ink)
        if (subtitle != null) {
            Spacer(Modifier.size(Space.sm))
            Text(subtitle, style = AgroTheme.type.bodyLg, color = AgroTheme.colors.body)
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = AgroTheme.type.displaySm, color = AgroTheme.colors.ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Workhorse row for settings, history and lists. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showChevron: Boolean = false,
    divider: Boolean = true,
    titleColor: Color = AgroTheme.colors.ink,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Space.md))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = AgroTheme.type.bodyMdStrong, color = titleColor)
                if (subtitle != null) Text(subtitle, style = AgroTheme.type.bodySm, color = AgroTheme.colors.body)
            }
            if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            if (showChevron) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AgroTheme.colors.mute)
            }
        }
        if (divider) HorizontalDivider(color = AgroTheme.colors.divider, thickness = 1.dp)
    }
}

/** Round icon tile used as a list row's leading element. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, container: Color = AgroTheme.colors.surfaceSoft) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = AgroTheme.colors.ink, modifier = Modifier.size(20.dp))
    }
}

/** Empty-state frame for lists and screens with nothing to show yet. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.xl)
            .background(AgroTheme.colors.secondaryFill)
            .padding(horizontal = Space.lg, vertical = Space.x2l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(emoji, style = AgroTheme.type.displayXxl)
        Text(title, style = AgroTheme.type.displaySm, color = AgroTheme.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = AgroTheme.type.bodyMd, color = AgroTheme.colors.body, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(Space.sm))
            action()
        }
    }
}

/** Small bulleted list used in disease guidance. */
@Composable
fun BulletList(items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        items.forEach { item ->
            Row {
                Box(
                    Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(AgroTheme.colors.ink),
                )
                Spacer(Modifier.width(Space.md))
                Text(item, style = AgroTheme.type.bodyMd, color = AgroTheme.colors.body)
            }
        }
    }
}
