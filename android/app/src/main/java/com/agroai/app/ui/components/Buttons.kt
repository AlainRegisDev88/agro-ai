package com.agroai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    pressedContainer: Color,
    content: Color,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    icon: ImageVector?,
    border: BorderStroke? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Space.touch),
        enabled = enabled && !loading,
        shape = Radius.pill,
        border = border,
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) pressedContainer else container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.5f),
        ),
        elevation = null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.sm))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.sm))
            }
            Text(text, style = AgroTheme.type.button)
        }
    }
}

/** The one main action per screen. Lime pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = AgroTheme.colors
    PillButton(text, onClick, c.primary, c.primaryActive, c.onPrimary, modifier, enabled, loading, icon)
}

/** Cancel / Skip / Retake. Sage pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = AgroTheme.colors
    PillButton(text, onClick, c.secondaryFill, c.surfaceSoft, c.ink, modifier, enabled, false, icon)
}

/** Outlined ink pill for low-emphasis actions on busy surfaces. */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = AgroTheme.colors.ink,
) {
    PillButton(
        text, onClick, Color.Transparent, contentColor.copy(alpha = 0.08f), contentColor,
        modifier, enabled, false, icon, BorderStroke(1.5.dp, contentColor),
    )
}

/** 48dp circular icon button. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = AgroTheme.colors.surface,
    content: Color = AgroTheme.colors.ink,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Space.touch),
        colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = content),
    ) {
        Icon(icon, contentDescription)
    }
}
