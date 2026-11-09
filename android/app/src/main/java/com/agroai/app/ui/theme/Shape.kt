package com.agroai.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Radius {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
    val sheetTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/** 4dp base unit. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val x2l = 32.dp
    val x3l = 48.dp

    /** Horizontal screen gutter. */
    val gutter = lg

    /** Minimum hit area for anything tappable. */
    val touch = 48.dp
}
