package com.hexgrid.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.data.AppRepository
import com.hexgrid.launcher.domain.AppSorter
import com.hexgrid.launcher.util.SettingsManager
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _allApps = MutableLiveData<List<AppInfo>>()
    val allApps: LiveData<List<AppInfo>> = _allApps

    private var allInstalledApps: List<AppInfo> = emptyList()

    private val _hiddenApps = mutableSetOf<String>()
    private val _unavailableApps = mutableSetOf<String>()

    // Tracks active search query so reloads don't clobber filter state
    private var _currentQuery: String = ""
    val currentQuery: String get() = _currentQuery

    private var activityContext: android.app.Activity? = null

    fun setActivityContext(activity: android.app.Activity) {
        activityContext = activity
    }

    fun loadApps() {
        viewModelScope.launch {
            _hiddenApps.clear()
            _hiddenApps.addAll(SettingsManager.getHiddenApps(getApplication()))
            allInstalledApps = repository.loadInstalledApps()
            _allApps.value = allInstalledApps
            updateAppList(allInstalledApps)
        }
    }

    /**
     * Invalidates cache and reloads — used by reactive listeners (BroadcastReceiver,
     * LauncherApps.Callback). Preserves active search query.
     */
    fun reloadApps() {
        repository.invalidateCache()
        loadApps()
    }

    fun filterApps(query: String) {
        _currentQuery = query
        updateAppList(allInstalledApps)
    }

    /**
     * Called by LauncherApps.Callback.onPackagesUnavailable().
     * Hides apps restricted by Samsung Modes or work profile.
     */
    fun markUnavailable(packages: Array<String>) {
        _unavailableApps.addAll(packages)
        updateAppList(allInstalledApps)
    }

    /**
     * Called by LauncherApps.Callback.onPackagesAvailable().
     * Restores apps when Samsung Mode or work profile restriction lifts.
     */
    fun markAvailable(packages: Array<String>) {
        _unavailableApps.removeAll(packages.toSet())
        updateAppList(allInstalledApps)
    }

    private fun updateAppList(baseList: List<AppInfo>) {
        // Apply query filter first
        val queryFiltered = if (_currentQuery.isBlank()) baseList
        else baseList.filter { it.label.contains(_currentQuery, ignoreCase = true) }

        // Then apply hidden + unavailable filters
        val visible = queryFiltered.filter {
            it.packageName !in _hiddenApps && it.packageName !in _unavailableApps
        }
        val sorted = AppSorter.sortApps(visible, getApplication())
        _apps.value = sorted
    }

    fun launchApp(app: AppInfo) {
        val context = activityContext ?: getApplication<Application>()
        repository.launchApp(app, context)
    }

    fun hideApp(app: AppInfo) {
        _hiddenApps.add(app.packageName)
        SettingsManager.setHiddenApps(getApplication(), _hiddenApps)
        updateAppList(allInstalledApps)
    }

    fun unhideApp(packageName: String) {
        _hiddenApps.remove(packageName)
        SettingsManager.setHiddenApps(getApplication(), _hiddenApps)
        updateAppList(allInstalledApps)
    }
}
