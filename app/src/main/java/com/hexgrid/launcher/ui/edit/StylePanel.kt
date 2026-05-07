package com.hexgrid.launcher.ui.edit

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.hexgrid.launcher.R
import com.hexgrid.launcher.databinding.PanelStyleBinding
import com.hexgrid.launcher.util.SettingsManager

/**
 * Style panel: appearance toggles (outline, labels, glow, dark theme),
 * transparency sliders, unified colors switch, icon shape chips, dim status bar.
 *
 * Dark theme writes to SharedPreferences immediately. The prefs listener in
 * MainActivity defers recreate() until Edit Mode exits to avoid destroying the overlay.
 * The "(applied on exit)" label in the layout informs the user of this behaviour.
 */
class StylePanel(private val context: Context) : EditPanel {

    private var _binding: PanelStyleBinding? = null
    override val view: View get() = _binding!!.root

    override fun attach(parent: ViewGroup) {
        val binding = PanelStyleBinding.inflate(LayoutInflater.from(context), parent, true)
        _binding = binding
        setup(binding)
    }

    override fun detach(parent: ViewGroup) {
        _binding?.root?.let { parent.removeView(it) }
        _binding = null
    }

    private fun setup(binding: PanelStyleBinding) {
        binding.switchShowOutline.isChecked = SettingsManager.getShowOutline(context)
        binding.switchShowOutline.setOnCheckedChangeListener { _, v ->
            SettingsManager.setShowOutline(context, v)
        }

        binding.switchShowLabels.isChecked = SettingsManager.getShowLabels(context)
        binding.switchShowLabels.setOnCheckedChangeListener { _, v ->
            SettingsManager.setShowLabels(context, v)
        }

        binding.switchNotificationGlow.isChecked = SettingsManager.getShowNotificationGlow(context)
        binding.switchNotificationGlow.setOnCheckedChangeListener { _, v ->
            SettingsManager.setShowNotificationGlow(context, v)
        }

        binding.switchDarkTheme.isChecked = SettingsManager.getDarkTheme(context)
        binding.switchDarkTheme.setOnCheckedChangeListener { _, v ->
            SettingsManager.setDarkTheme(context, v)
        }

        binding.sliderTileTransparency.value = SettingsManager.getTileTransparency(context).toFloat()
        binding.sliderTileTransparency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setTileTransparency(context, value.toInt())
                ViewCompat.setAccessibilityDelegate(binding.sliderTileTransparency,
                    sliderDelegate("Tile transparency: ${value.toInt()}%"))
            }
        }

        binding.sliderDockTransparency.value = SettingsManager.getDockTransparency(context).toFloat()
        binding.sliderDockTransparency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setDockTransparency(context, value.toInt())
                ViewCompat.setAccessibilityDelegate(binding.sliderDockTransparency,
                    sliderDelegate("Dock transparency: ${value.toInt()}%"))
            }
        }

        binding.switchUnifiedBucketColors.isChecked = SettingsManager.getUnifiedBucketColors(context)
        binding.switchUnifiedBucketColors.setOnCheckedChangeListener { _, v ->
            SettingsManager.setUnifiedBucketColors(context, v)
        }

        when (SettingsManager.getShortcutIconShape(context)) {
            SettingsManager.ShortcutIconShape.SQUARE    ->
                binding.toggleShortcutIconShape.check(R.id.btnShapeSquare)
            SettingsManager.ShortcutIconShape.SQUIRCLE  ->
                binding.toggleShortcutIconShape.check(R.id.btnShapeSquircle)
            SettingsManager.ShortcutIconShape.CIRCLE    ->
                binding.toggleShortcutIconShape.check(R.id.btnShapeCircle)
        }
        binding.toggleShortcutIconShape.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val shape = when (checkedId) {
                R.id.btnShapeSquare   -> SettingsManager.ShortcutIconShape.SQUARE
                R.id.btnShapeCircle   -> SettingsManager.ShortcutIconShape.CIRCLE
                else                  -> SettingsManager.ShortcutIconShape.SQUIRCLE
            }
            if (shape != SettingsManager.getShortcutIconShape(context)) {
                SettingsManager.setShortcutIconShape(context, shape)
                SettingsManager.setIconCacheDirty(context, true)
            }
        }

        binding.switchDimStatusBar.isChecked = SettingsManager.getDimStatusBar(context)
        binding.switchDimStatusBar.setOnCheckedChangeListener { _, v ->
            SettingsManager.setDimStatusBar(context, v)
        }
    }

    private fun sliderDelegate(description: String) =
        object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = description
            }
        }
}
