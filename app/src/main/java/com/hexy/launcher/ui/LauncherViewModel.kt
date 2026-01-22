package com.hexy.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.data.AppRepository
import com.hexy.launcher.domain.AppSorter
import com.hexy.launcher.util.SettingsManager
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = AppRepository(application)
    
    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps
    
    // Cached full list to restore after filtering
    private var allInstalledApps: List<AppInfo> = emptyList()
    
    private val _hiddenApps = mutableSetOf<String>()
    
    private var activityContext: android.app.Activity? = null
    
    fun setActivityContext(activity: android.app.Activity) {
        activityContext = activity
    }
    
    fun loadApps() {
        viewModelScope.launch {
            // Load hidden apps from settings
            _hiddenApps.clear()
            _hiddenApps.addAll(SettingsManager.getHiddenApps(getApplication()))
            
            allInstalledApps = repository.loadInstalledApps()
            updateAppList(allInstalledApps)
        }
    }
    
    fun filterApps(query: String) {
        if (query.isBlank()) {
            updateAppList(allInstalledApps)
        } else {
            val filtered = allInstalledApps.filter { 
                it.label.contains(query, ignoreCase = true) 
            }
            updateAppList(filtered)
        }
    }
    
    private fun updateAppList(list: List<AppInfo>) {
        val visible = list.filter { it.packageName !in _hiddenApps }
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
        // Refresh with current filter if any, but simplified: just reload
        updateAppList(allInstalledApps)
    }
    
    fun unhideApp(packageName: String) {
        _hiddenApps.remove(packageName)
        SettingsManager.setHiddenApps(getApplication(), _hiddenApps)
        updateAppList(allInstalledApps)
    }
}
