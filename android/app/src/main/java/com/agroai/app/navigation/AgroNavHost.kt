package com.agroai.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.agroai.app.BuildConfig
import com.agroai.app.R
import com.agroai.app.feature.history.HistoryScreen
import com.agroai.app.feature.home.HomeScreen
import com.agroai.app.feature.library.CropDetailScreen
import com.agroai.app.feature.library.DiseaseDetailScreen
import com.agroai.app.feature.library.LibraryScreen
import com.agroai.app.feature.onboarding.ChooseCropsScreen
import com.agroai.app.feature.onboarding.DownloadModelsScreen
import com.agroai.app.feature.onboarding.WelcomeScreen
import com.agroai.app.feature.result.ResultScreen
import com.agroai.app.feature.scan.ScanScreen
import com.agroai.app.feature.settings.AboutScreen
import com.agroai.app.feature.settings.GalleryScreen
import com.agroai.app.feature.settings.ManageCropsScreen
import com.agroai.app.feature.settings.SettingsScreen
import com.agroai.app.ui.components.AgroBottomBar
import com.agroai.app.ui.components.TabItem
import com.agroai.app.ui.theme.AgroTheme
import kotlinx.serialization.Serializable

@Serializable object Welcome
@Serializable data class ChooseCrops(val fromSettings: Boolean = false)
@Serializable object DownloadModels
@Serializable object Home
@Serializable object History
@Serializable object Library
@Serializable object Settings
@Serializable data class ScanRoute(val crop: String? = null)
@Serializable data class ResultRoute(val scanId: Long)
@Serializable data class CropDetail(val crop: String)
@Serializable data class DiseaseDetail(val label: String)
@Serializable object ManageCrops
@Serializable object About
@Serializable object Gallery

private fun NavDestination?.tabKey(): String? = when {
    this == null -> null
    hasRoute(Home::class) -> "home"
    hasRoute(History::class) -> "history"
    hasRoute(Library::class) -> "library"
    hasRoute(Settings::class) -> "settings"
    else -> null
}

private fun routeFor(key: String): Any = when (key) {
    "history" -> History
    "library" -> Library
    "settings" -> Settings
    else -> Home
}

/** Home is always the base of the stack once onboarding is done. */
fun NavHostController.selectTab(key: String) = navigate(routeFor(key)) {
    popUpTo<Home> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Leaves onboarding for good: Home becomes the only entry on the stack. */
fun NavHostController.finishOnboarding() = navigate(Home) {
    popUpTo(graph.id) { inclusive = true }
}

@Composable
fun AgroNavHost(startDestination: Any) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tab = entry?.destination.tabKey()

    Column(Modifier.fillMaxSize().background(AgroTheme.colors.background)) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = startDestination) {
                composable<Welcome> {
                    WelcomeScreen(onStart = { nav.navigate(ChooseCrops()) })
                }
                composable<ChooseCrops> {
                    val route = it.toRoute<ChooseCrops>()
                    ChooseCropsScreen(
                        fromSettings = route.fromSettings,
                        onBack = if (route.fromSettings) ({ nav.popBackStack() }) else null,
                        onDone = { if (route.fromSettings) nav.popBackStack() else nav.navigate(DownloadModels) },
                    )
                }
                composable<DownloadModels> {
                    DownloadModelsScreen(onBack = { nav.popBackStack() }, onDone = { nav.finishOnboarding() })
                }
                composable<Home> {
                    HomeScreen(
                        onScan = { crop -> nav.navigate(ScanRoute(crop)) },
                        onOpenScan = { nav.navigate(ResultRoute(it)) },
                        onSeeHistory = { nav.selectTab("history") },
                        onAddCrop = { nav.navigate(ChooseCrops(fromSettings = true)) },
                        onManageModels = { nav.navigate(ManageCrops) },
                        onOpenCrop = { nav.navigate(CropDetail(it)) },
                    )
                }
                composable<History> {
                    HistoryScreen(onOpen = { nav.navigate(ResultRoute(it)) }, onScan = { nav.navigate(ScanRoute()) })
                }
                composable<Library> {
                    LibraryScreen(onOpenCrop = { nav.navigate(CropDetail(it)) })
                }
                composable<Settings> {
                    SettingsScreen(
                        onManageCrops = { nav.navigate(ManageCrops) },
                        onAbout = { nav.navigate(About) },
                        onGallery = { nav.navigate(Gallery) },
                    )
                }
                composable<ScanRoute> {
                    ScanScreen(
                        initialCrop = it.toRoute<ScanRoute>().crop,
                        onClose = { nav.popBackStack() },
                        onResult = { id ->
                            nav.navigate(ResultRoute(id)) { popUpTo<ScanRoute> { inclusive = true } }
                        },
                        onManageModels = { nav.navigate(ManageCrops) },
                    )
                }
                composable<ResultRoute> {
                    ResultScreen(
                        scanId = it.toRoute<ResultRoute>().scanId,
                        onBack = { nav.popBackStack() },
                        onScanAgain = { crop ->
                            nav.navigate(ScanRoute(crop)) { popUpTo<ResultRoute> { inclusive = true } }
                        },
                        onLearnMore = { label -> nav.navigate(DiseaseDetail(label)) },
                    )
                }
                composable<CropDetail> {
                    CropDetailScreen(
                        cropId = it.toRoute<CropDetail>().crop,
                        onBack = { nav.popBackStack() },
                        onOpenDisease = { label -> nav.navigate(DiseaseDetail(label)) },
                        onScan = { crop -> nav.navigate(ScanRoute(crop)) },
                    )
                }
                composable<DiseaseDetail> {
                    DiseaseDetailScreen(label = it.toRoute<DiseaseDetail>().label, onBack = { nav.popBackStack() })
                }
                composable<ManageCrops> {
                    ManageCropsScreen(
                        onBack = { nav.popBackStack() },
                        onAddCrop = { nav.navigate(ChooseCrops(fromSettings = true)) },
                    )
                }
                composable<About> { AboutScreen(onBack = { nav.popBackStack() }) }
                if (BuildConfig.DEBUG) {
                    composable<Gallery> { GalleryScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }

        if (tab != null) {
            AgroBottomBar(
                tabs = listOf(
                    TabItem("home", stringResource(R.string.tab_home), Icons.Outlined.Home),
                    TabItem("history", stringResource(R.string.tab_history), Icons.Outlined.History),
                    TabItem("library", stringResource(R.string.tab_library), Icons.AutoMirrored.Outlined.MenuBook),
                    TabItem("settings", stringResource(R.string.tab_settings), Icons.Outlined.Settings),
                ),
                selected = tab,
                onSelect = { nav.selectTab(it) },
                scanLabel = stringResource(R.string.action_scan),
                onScan = { nav.navigate(ScanRoute()) },
                scanIcon = Icons.Outlined.CameraAlt,
            )
        }
    }
}
