package com.agroai.app.feature.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.agroai.app.AppContainer
import com.agroai.app.R
import com.agroai.app.appContainer
import com.agroai.app.data.content.Crop
import com.agroai.app.ml.NoModelException
import com.agroai.app.ui.components.AgroBottomSheet
import com.agroai.app.ui.components.AgroSnackbarHost
import com.agroai.app.ui.components.AgroTopBar
import com.agroai.app.ui.components.CropAvatar
import com.agroai.app.ui.components.EmptyState
import com.agroai.app.ui.components.IconCircleButton
import com.agroai.app.ui.components.ListRow
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.SecondaryButton
import com.agroai.app.ui.theme.AgroTheme
import com.agroai.app.ui.theme.Palette
import com.agroai.app.ui.theme.Radius
import com.agroai.app.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

sealed interface ScanStage {
    data object Camera : ScanStage
    data class Review(val photo: Uri) : ScanStage
    data class Analyzing(val photo: Uri) : ScanStage
}

data class ScanUiState(
    val loading: Boolean = true,
    val crops: List<Crop> = emptyList(),
    val crop: Crop? = null,
    val askCrop: Boolean = false,
    val stage: ScanStage = ScanStage.Camera,
)

class ScanViewModel(private val app: AppContainer, initialCrop: String?) : ViewModel() {
    val state = MutableStateFlow(ScanUiState())
    val errors = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            val settings = app.settings.current()
            val crops = app.content.crops.filter { it.id in settings.crops && app.classifiers.isAvailable(it.id) }
            val preferred = initialCrop ?: settings.lastCrop
            val crop = crops.firstOrNull { it.id == preferred } ?: crops.singleOrNull()
            state.value = ScanUiState(
                loading = false,
                crops = crops,
                crop = crop ?: crops.firstOrNull(),
                // Ask which crop when there is a real choice and no earlier answer.
                askCrop = crop == null && crops.size > 1,
            )
        }
    }

    fun chooseCrop(crop: Crop) {
        state.value = state.value.copy(crop = crop, askCrop = false)
        viewModelScope.launch { app.settings.setLastCrop(crop.id) }
    }

    fun showCropPicker(show: Boolean) {
        state.value = state.value.copy(askCrop = show)
    }

    fun review(photo: Uri) {
        state.value = state.value.copy(stage = ScanStage.Review(photo))
    }

    fun retake() {
        state.value = state.value.copy(stage = ScanStage.Camera)
    }

    fun analyze(onResult: (Long) -> Unit) {
        val s = state.value
        val photo = (s.stage as? ScanStage.Review)?.photo ?: return
        val crop = s.crop ?: return
        state.value = s.copy(stage = ScanStage.Analyzing(photo))
        viewModelScope.launch {
            try {
                app.settings.setLastCrop(crop.id)
                onResult(app.scanner.analyze(crop.id, photo))
            } catch (e: NoModelException) {
                errors.value = R.string.scan_error_no_model
                state.value = state.value.copy(stage = ScanStage.Review(photo))
            } catch (e: Exception) {
                Log.e("ScanViewModel", "analysis failed", e)
                errors.value = R.string.scan_error_generic
                state.value = state.value.copy(stage = ScanStage.Review(photo))
            }
        }
    }
}

@Composable
fun ScanScreen(
    initialCrop: String?,
    onClose: () -> Unit,
    onResult: (Long) -> Unit,
    onManageModels: () -> Unit,
) {
    val app = appContainer()
    val vm: ScanViewModel = viewModel { ScanViewModel(app, initialCrop) }
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.errors.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    error?.let { res ->
        val message = stringResource(res)
        LaunchedEffect(res) {
            snackbar.showSnackbar(message)
            vm.errors.value = null
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::review)
    }
    val openGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        when {
            state.loading -> Unit
            state.crops.isEmpty() -> NoModelState(onClose, onManageModels)
            else -> when (val stage = state.stage) {
                ScanStage.Camera -> CameraStage(
                    crop = state.crop,
                    onClose = onClose,
                    onPickCrop = { vm.showCropPicker(true) },
                    onCaptured = vm::review,
                    onGallery = openGallery,
                    onError = { vm.errors.value = R.string.scan_error_camera },
                )
                is ScanStage.Review -> ReviewStage(stage.photo, state.crop, analyzing = false, vm::retake) { vm.analyze(onResult) }
                is ScanStage.Analyzing -> ReviewStage(stage.photo, state.crop, analyzing = true, {}, {})
            }
        }
        AgroSnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    if (state.askCrop && state.crops.size > 1) {
        AgroBottomSheet(onDismiss = { if (state.crop != null) vm.showCropPicker(false) else onClose() }) {
            Text(stringResource(R.string.scan_pick_crop), style = AgroTheme.type.displayMd, color = AgroTheme.colors.ink)
            Spacer(Modifier.height(Space.md))
            state.crops.forEachIndexed { i, crop ->
                ListRow(
                    title = crop.name,
                    leading = { CropAvatar(crop) },
                    trailing = {
                        if (crop.id == state.crop?.id) Text("✓", style = AgroTheme.type.displaySm, color = AgroTheme.colors.positive)
                    },
                    divider = i < state.crops.lastIndex,
                    onClick = { vm.chooseCrop(crop) },
                )
            }
        }
    }

    BackHandler(enabled = state.stage is ScanStage.Review) { vm.retake() }
}

@Composable
private fun NoModelState(onClose: () -> Unit, onManageModels: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AgroTheme.colors.background)) {
        AgroTopBar(stringResource(R.string.action_scan), onBack = onClose)
        EmptyState(
            emoji = "📦",
            title = stringResource(R.string.scan_no_model_title),
            body = stringResource(R.string.scan_no_model_body),
            modifier = Modifier.padding(Space.gutter),
            action = { PrimaryButton(stringResource(R.string.settings_manage_crops), onManageModels) },
        )
    }
}

// ─── Camera ─────────────────────────────────────────────────────────────────

@Composable
private fun CameraStage(
    crop: Crop?,
    onClose: () -> Unit,
    onPickCrop: () -> Unit,
    onCaptured: (Uri) -> Unit,
    onGallery: () -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var explainerDismissed by rememberSaveable { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) {
            val activity = context as? Activity
            permanentlyDenied = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
            explainerDismissed = true
        }
    }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (granted) {
            CameraPreview(imageCapture, onError)
            SquareGuide()
        } else {
            PermissionState(
                permanentlyDenied = permanentlyDenied,
                onAllow = { requestPermission.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                onGallery = onGallery,
            )
        }

        // Top controls
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(Icons.Filled.Close, stringResource(R.string.action_close), onClose, container = Color.Black.copy(alpha = 0.4f), content = Color.White)
            Spacer(Modifier.weight(1f))
            if (crop != null) {
                Row(
                    Modifier
                        .clip(Radius.pill)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(role = Role.Button, onClick = onPickCrop)
                        .padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${crop.emoji}  ${crop.name}", style = AgroTheme.type.bodyMdStrong, color = Color.White)
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.scan_change_crop), tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            IconCircleButton(
                if (flashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                stringResource(R.string.scan_flash),
                { flashOn = !flashOn },
                container = Color.Black.copy(alpha = 0.4f),
                content = if (flashOn) Palette.Primary else Color.White,
            )
        }

        // Bottom controls
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (granted) {
                Text(
                    stringResource(R.string.scan_hint),
                    style = AgroTheme.type.bodyMdStrong,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(Radius.pill)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = Space.lg, vertical = Space.sm),
                )
                Spacer(Modifier.height(Space.xl))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.x2l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconCircleButton(
                    Icons.Outlined.PhotoLibrary,
                    stringResource(R.string.scan_gallery),
                    onGallery,
                    modifier = Modifier.size(56.dp),
                    container = Color.Black.copy(alpha = 0.4f),
                    content = Color.White,
                )
                val shutterLabel = stringResource(R.string.scan_capture)
                Box(
                    Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(if (granted && !capturing) Palette.Primary else Palette.Mute)
                        .clickable(enabled = granted && !capturing, role = Role.Button) {
                            capturing = true
                            scope.launch {
                                val uri = takePhoto(context, imageCapture)
                                capturing = false
                                if (uri != null) onCaptured(uri) else onError()
                            }
                        }
                        .semantics { contentDescription = shutterLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    if (capturing) CircularProgressIndicator(Modifier.size(28.dp), color = Palette.Ink, strokeWidth = 3.dp)
                }
                Spacer(Modifier.size(56.dp))
            }
        }
    }

    // Explain why before the system prompt.
    if (!granted && !explainerDismissed) {
        AgroBottomSheet(onDismiss = { explainerDismissed = true }) {
            Text("📷", style = AgroTheme.type.displayXxl)
            Spacer(Modifier.height(Space.md))
            Text(stringResource(R.string.permission_title), style = AgroTheme.type.displayMd, color = AgroTheme.colors.ink)
            Spacer(Modifier.height(Space.sm))
            Text(stringResource(R.string.permission_body), style = AgroTheme.type.bodyMd, color = AgroTheme.colors.body)
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(stringResource(R.string.permission_allow), {
                explainerDismissed = true
                requestPermission.launch(Manifest.permission.CAMERA)
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(stringResource(R.string.permission_gallery), {
                explainerDismissed = true
                onGallery()
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CameraPreview(imageCapture: ImageCapture, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                provider = future.get().also {
                    val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
                    it.unbindAll()
                    it.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }
            } catch (e: Exception) {
                Log.e("CameraPreview", "bind failed", e)
                onError()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

/** Dims everything outside a centred square: the part of the photo the model sees. */
@Composable
private fun SquareGuide() {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val side = size.width - 32.dp.toPx()
            val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
            val radius = CornerRadius(24.dp.toPx())
            val hole = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(topLeft.x, topLeft.y, topLeft.x + side, topLeft.y + side, radius))
            }
            clipPath(hole, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.55f))
            }
            drawRoundRect(
                color = Palette.Primary,
                topLeft = topLeft,
                size = Size(side, side),
                cornerRadius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun PermissionState(
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(Space.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📷", style = AgroTheme.type.displayMega)
        Spacer(Modifier.height(Space.lg))
        Text(
            stringResource(if (permanentlyDenied) R.string.permission_denied_title else R.string.permission_title),
            style = AgroTheme.type.displayMd, color = Color.White, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(if (permanentlyDenied) R.string.permission_denied_body else R.string.permission_body),
            style = AgroTheme.type.bodyMd, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(
            stringResource(if (permanentlyDenied) R.string.permission_open_settings else R.string.permission_allow),
            if (permanentlyDenied) onOpenSettings else onAllow,
        )
        Spacer(Modifier.height(Space.sm))
        SecondaryButton(stringResource(R.string.permission_gallery), onGallery)
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private suspend fun takePhoto(context: Context, capture: ImageCapture): Uri? = suspendCancellableCoroutine { cont ->
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // keep only the latest capture
    val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
    capture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(Uri.fromFile(file))
            override fun onError(exception: ImageCaptureException) {
                Log.e("ScanScreen", "capture failed", exception)
                cont.resume(null)
            }
        },
    )
}

// ─── Review / analyzing ─────────────────────────────────────────────────────

@Composable
private fun ReviewStage(photo: Uri, crop: Crop?, analyzing: Boolean, onRetake: () -> Unit, onAnalyze: () -> Unit) {
    val c = AgroTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        AgroTopBar(
            stringResource(if (analyzing) R.string.scan_analyzing_title else R.string.scan_review_title),
            onBack = if (analyzing) null else onRetake,
        )
        Column(Modifier.weight(1f).padding(horizontal = Space.gutter)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(Radius.xl).background(c.surfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                // Shows exactly the centre square the model will see.
                AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (analyzing) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Palette.Primary, strokeWidth = 4.dp)
                            Spacer(Modifier.height(Space.md))
                            Text(stringResource(R.string.scan_analyzing_body), style = AgroTheme.type.bodyMdStrong, color = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.lg))
            if (crop != null) {
                Text(
                    stringResource(R.string.scan_review_body, crop.name),
                    style = AgroTheme.type.bodyMd,
                    color = c.body,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            SecondaryButton(stringResource(R.string.scan_retake), onRetake, Modifier.weight(1f), enabled = !analyzing)
            PrimaryButton(stringResource(R.string.scan_analyze), onAnalyze, Modifier.weight(1f), loading = analyzing)
        }
    }
}
