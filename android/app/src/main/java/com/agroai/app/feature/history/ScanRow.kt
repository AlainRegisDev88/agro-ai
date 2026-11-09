package com.agroai.app.feature.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.agroai.app.data.content.ContentRepository
import com.agroai.app.data.history.Scan
import com.agroai.app.data.history.status
import com.agroai.app.ui.components.ListRow
import com.agroai.app.ui.components.StatusBadge
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Radius
import java.io.File

/** One scan in a list: photo, condition, crop and time, status badge. */
@Composable
fun ScanRow(scan: Scan, content: ContentRepository, onClick: () -> Unit, divider: Boolean = true) {
    val time = DateUtils.getRelativeTimeSpanString(
        scan.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    )
    ListRow(
        title = content.name(scan.label),
        subtitle = "${content.cropName(scan.crop)} · $time",
        leading = { ScanThumb(scan.imagePath) },
        trailing = { StatusBadge(scan.status) },
        divider = divider,
        onClick = onClick,
    )
}

@Composable
fun ScanThumb(path: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(48.dp).clip(Radius.md).background(AgroTheme.colors.surfaceSoft),
    )
}
