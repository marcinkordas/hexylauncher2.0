package com.hexy.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.data.AppRepository
import com.hexy.launcher.domain.AppSorter
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = AppRepository(application)
    
    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps
    
    private val _hiddenApps = mutableSetOf<String>()
    
    private var activityContext: android.app.Activity? = null
    
    fun setActivityContext(activity: android.app.Activity) {
        activityContext = activity
    }
    
    fun loadApps() {
        viewModelScope.launch {
            val allApps = repository.loadInstalledApps()
            val visible = allApps.filter { it.packageName !in _hiddenApps }
            val sorted = AppSorter.sortApps(visible)
            _apps.value = sorted
        }
    }
    
    fun launchApp(app: AppInfo) {
        val context = activityContext ?: getApplication<Application>()
        repository.launchApp(app, context)
    }
    
    fun hideApp(app: AppInfo) {
        _hiddenApps.add(app.packageName)
        loadApps() // Refresh
    }
}
