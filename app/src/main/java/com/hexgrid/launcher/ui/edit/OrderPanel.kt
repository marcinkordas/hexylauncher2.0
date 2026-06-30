package com.hexgrid.launcher.ui.edit

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hexgrid.launcher.R
import com.hexgrid.launcher.databinding.PanelOrderBinding
import com.hexgrid.launcher.util.SettingsManager

/**
 * Order panel: sort order chip group, search position chip group, voice search switch.
 */
class OrderPanel(private val context: Context) : EditPanel {

    private var _binding: PanelOrderBinding? = null
    override val view: View get() = _binding!!.root

    override fun attach(parent: ViewGroup) {
        val binding = PanelOrderBinding.inflate(LayoutInflater.from(context), parent, true)
        _binding = binding
        setup(binding)
    }

    override fun detach(parent: ViewGroup) {
        _binding?.root?.let { parent.removeView(it) }
        _binding = null
    }

    private fun setup(binding: PanelOrderBinding) {
        when (SettingsManager.getSortOrder(context)) {
            SettingsManager.SortOrder.NAME              ->
                binding.toggleSortOrder.check(R.id.btnSortName)
            SettingsManager.SortOrder.USAGE_FREQUENCY   ->
                binding.toggleSortOrder.check(R.id.btnSortFrequency)
            SettingsManager.SortOrder.USAGE_TIME        ->
                binding.toggleSortOrder.check(R.id.btnSortTime)
            SettingsManager.SortOrder.NOTIFICATION_COUNT ->
                binding.toggleSortOrder.check(R.id.btnSortNotifications)
        }
        // ChipGroup (singleSelection + selectionRequired) yields 0 or 1 entries in the
        // checkedIds list — empty would only fire if selectionRequired were false.
        binding.toggleSortOrder.setOnCheckedStateChangeListener { _, checkedIds ->
            val order = when (checkedIds.firstOrNull()) {
                R.id.btnSortName          -> SettingsManager.SortOrder.NAME
                R.id.btnSortFrequency     -> SettingsManager.SortOrder.USAGE_FREQUENCY
                R.id.btnSortTime          -> SettingsManager.SortOrder.USAGE_TIME
                R.id.btnSortNotifications -> SettingsManager.SortOrder.NOTIFICATION_COUNT
                else                      -> return@setOnCheckedStateChangeListener
            }
            SettingsManager.setSortOrder(context, order)
        }

        when (SettingsManager.getSearchPosition(context)) {
            SettingsManager.SearchPosition.NONE   ->
                binding.toggleSearchPosition.check(R.id.btnSearchNone)
            SettingsManager.SearchPosition.TOP    ->
                binding.toggleSearchPosition.check(R.id.btnSearchTop)
            SettingsManager.SearchPosition.BOTTOM ->
                binding.toggleSearchPosition.check(R.id.btnSearchBottom)
        }
        binding.toggleSearchPosition.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val pos = when (checkedId) {
                R.id.btnSearchTop    -> SettingsManager.SearchPosition.TOP
                R.id.btnSearchBottom -> SettingsManager.SearchPosition.BOTTOM
                else                 -> SettingsManager.SearchPosition.NONE
            }
            SettingsManager.setSearchPosition(context, pos)
        }

        binding.switchVoiceSearch.isChecked = SettingsManager.getSearchWithMic(context)
        binding.switchVoiceSearch.setOnCheckedChangeListener { _, v ->
            SettingsManager.setSearchWithMic(context, v)
        }
    }
}
