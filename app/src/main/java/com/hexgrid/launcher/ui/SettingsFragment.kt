package com.hexgrid.launcher.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.hexgrid.launcher.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
