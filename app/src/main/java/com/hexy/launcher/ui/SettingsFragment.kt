package com.hexy.launcher.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.hexy.launcher.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
