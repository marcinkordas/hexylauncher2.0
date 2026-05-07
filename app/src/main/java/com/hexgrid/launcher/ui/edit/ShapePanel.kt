package com.hexgrid.launcher.ui.edit

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.hexgrid.launcher.R
import com.hexgrid.launcher.databinding.PanelShapeBinding
import com.hexgrid.launcher.util.SettingsManager

/**
 * Shape panel: sliders for hex radius, icon size/padding, outline width, corner radius,
 * and a Pointy/Flat orientation chip group.
 *
 * Each control reads its initial value from SettingsManager on [attach] and writes
 * immediately on change — the SharedPreferences listener in MainActivity propagates
 * the change to the live grid view.
 */
class ShapePanel(private val context: Context) : EditPanel {

    private var _binding: PanelShapeBinding? = null
    override val view: View get() = _binding!!.root

    override fun attach(parent: ViewGroup) {
        val binding = PanelShapeBinding.inflate(LayoutInflater.from(context), parent, true)
        _binding = binding
        setup(binding)
    }

    override fun detach(parent: ViewGroup) {
        _binding?.root?.let { parent.removeView(it) }
        _binding = null
    }

    private fun setup(binding: PanelShapeBinding) {
        binding.sliderHexRadius.value = SettingsManager.getHexRadius(context)
        binding.sliderHexRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setHexRadius(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderHexRadius,
                    sliderDelegate("Hex radius: ${value.toInt()}dp"))
            }
        }
        ViewCompat.setAccessibilityDelegate(binding.sliderHexRadius,
            sliderDelegate("Hex radius: ${SettingsManager.getHexRadius(context).toInt()}dp"))

        binding.sliderIconSize.value = SettingsManager.getIconSizeMultiplier(context)
        binding.sliderIconSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setIconSizeMultiplier(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderIconSize,
                    sliderDelegate("Icon size: ${(value * 100).toInt()}%"))
            }
        }

        binding.sliderIconPadding.value = SettingsManager.getIconPadding(context)
        binding.sliderIconPadding.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setIconPadding(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderIconPadding,
                    sliderDelegate("Icon padding: ${value.toInt()}dp"))
            }
        }

        binding.sliderOutlineWidth.value = SettingsManager.getOutlineWidth(context)
        binding.sliderOutlineWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setOutlineWidth(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderOutlineWidth,
                    sliderDelegate("Outline width: ${value}dp"))
            }
        }

        binding.sliderCornerRadius.value = SettingsManager.getCornerRadius(context)
        binding.sliderCornerRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setCornerRadius(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderCornerRadius,
                    sliderDelegate("Corner radius: ${value.toInt()}dp"))
            }
        }

        when (SettingsManager.getHexOrientation(context)) {
            SettingsManager.HexOrientation.POINTY_TOP ->
                binding.toggleHexOrientation.check(R.id.btnOrientationPointy)
            SettingsManager.HexOrientation.FLAT_TOP ->
                binding.toggleHexOrientation.check(R.id.btnOrientationFlat)
        }
        binding.toggleHexOrientation.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newOrientation = when (checkedId) {
                R.id.btnOrientationPointy -> SettingsManager.HexOrientation.POINTY_TOP
                else                      -> SettingsManager.HexOrientation.FLAT_TOP
            }
            SettingsManager.setHexOrientation(context, newOrientation)
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
