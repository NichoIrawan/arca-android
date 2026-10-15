package org.nicho.arca

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.nicho.arca.di.ApplicationScope
import org.nicho.arca.domain.repository.CategoryStore

@HiltAndroidApp
class ArcaApplication : Application() {

    @Inject lateinit var categories: CategoryStore

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // FR-250: default categories, Fees included. Idempotent, and off the main thread so it
        // never delays the first frame (SDD-ARC-002 §12.4).
        appScope.launch { categories.seedDefaultsIfNeeded() }
    }
}
