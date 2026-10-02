/*
 * MacroDimeApplication.kt
 * MacroDime
 *
 * Composition root: opens the database, then mirrors the curated catalogue into
 * it. The Android counterpart of MacroDimeApp.init on iOS.
 */
package com.lungelo.macrodime

import android.app.Application
import com.lungelo.macrodime.data.MacroDimeDatabase
import com.lungelo.macrodime.data.MacroDimeRepository
import com.lungelo.macrodime.data.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What the screens need, built once per process. */
class AppContainer(application: Application) {
    val database = MacroDimeDatabase.open(application)
    val photos = PhotoStore(application)
    val repository = MacroDimeRepository(database, photos)

    /** Work that must finish even if the screen that started it goes away. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class MacroDimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Idempotent and cheap after the first run: one read of 57 rows, and a
        // write only for a food whose values changed in this build. A failure
        // is retried on the next launch; nothing depends on it finishing first,
        // because every screen watches the food table and redraws when it fills.
        container.applicationScope.launch {
            runCatching { container.repository.seedCatalog() }
        }
    }
}
