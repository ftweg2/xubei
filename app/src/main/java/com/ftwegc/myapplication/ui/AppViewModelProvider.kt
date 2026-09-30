package com.ftwegc.myapplication.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.ftwegc.myapplication.AppContainer
import com.ftwegc.myapplication.VluerApp
import com.ftwegc.myapplication.ui.home.HomeViewModel
import com.ftwegc.myapplication.ui.komari.KomariImportViewModel
import com.ftwegc.myapplication.ui.komari.KomariSettingsViewModel
import com.ftwegc.myapplication.ui.navigation.SubscriptionDetailRoute
import com.ftwegc.myapplication.ui.settings.SettingsViewModel
import com.ftwegc.myapplication.ui.stats.StatsViewModel
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionDetailViewModel
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionListViewModel

/** ViewModel factory that pulls dependencies from the [AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val c = container()
            HomeViewModel(c.subscriptionRepository, c.settingsStore, c.trafficRepository, c.nodeLiveRepository)
        }
        initializer {
            val c = container()
            AppChromeViewModel(c.subscriptionRepository, c.settingsStore)
        }
        initializer {
            val c = container()
            StatsViewModel(c.subscriptionRepository, c.settingsStore)
        }
        initializer {
            val c = container()
            SubscriptionListViewModel(c.subscriptionRepository, c.settingsStore, createSavedStateHandle(), c.nodeLiveRepository)
        }
        initializer {
            val c = container()
            val route = createSavedStateHandle().toRoute<SubscriptionDetailRoute>()
            SubscriptionDetailViewModel(route.id, c.subscriptionRepository, c.settingsStore, c.trafficRepository, c.nodeLiveRepository)
        }
        initializer {
            val app = vluerApp()
            val version = runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName
            }.getOrNull().orEmpty()
            SettingsViewModel(
                settingsStore = app.container.settingsStore,
                versionName = version,
                komariRepository = app.container.komariRepository,
                trafficRepository = app.container.trafficRepository,
                backupRepository = app.container.backupRepository,
                workScheduler = app.container.workScheduler,
            )
        }
        initializer {
            val c = container()
            KomariSettingsViewModel(c.komariRepository, c.trafficRepository, c.settingsStore, c.secretStore)
        }
        initializer {
            val app = vluerApp()
            KomariImportViewModel(app.container.komariRepository, app.container.trafficRepository, app.applicationScope)
        }
    }

    private fun CreationExtras.vluerApp(): VluerApp = this[APPLICATION_KEY] as VluerApp

    private fun CreationExtras.container(): AppContainer = vluerApp().container
}
