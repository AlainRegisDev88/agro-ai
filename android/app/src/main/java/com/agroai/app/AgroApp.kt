package com.agroai.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.agroai.app.data.content.ContentRepository
import com.agroai.app.data.history.AgroDatabase
import com.agroai.app.data.history.HistoryRepository
import com.agroai.app.data.models.ModelRepository
import com.agroai.app.data.prefs.SettingsStore
import com.agroai.app.ml.ClassifierProvider
import com.agroai.app.ml.ScanService
import java.io.File

/** Manual DI: one instance of each repository for the whole app. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val content = ContentRepository(context)
    val models = ModelRepository(context)
    val history = HistoryRepository(
        AgroDatabase.create(context).scans(),
        File(context.filesDir, "scans").apply { mkdirs() },
    )
    val classifiers = ClassifierProvider(models, content)
    val scanner = ScanService(context, classifiers, history)
}

class AgroApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as AgroApp).container
