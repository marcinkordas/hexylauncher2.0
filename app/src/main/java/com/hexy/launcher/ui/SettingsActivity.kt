package com.hexy.launcher.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.hexy.launcher.MainActivity
import com.hexy.launcher.databinding.ActivitySettingsBinding
import com.hexy.launcher.util.SettingsManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Launcher Settings"
        
        setupSpinners()
        setupSeekBars()
        setupToggles()
        setupPermissions()
    }
    
    private fun setupSpinners() {
        // Sort Order
        val sortOptions = SettingsManager.SortOrder.values().map { it.name.replace('_', ' ') }
        binding.spinnerSortOrder.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSortOrder.setSelection(SettingsManager.getSortOrder(this).ordinal)
        binding.spinnerSortOrder.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.setSortOrder(this@SettingsActivity, SettingsManager.SortOrder.values()[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Hex Orientation
        val orientOptions = SettingsManager.HexOrientation.values().map { it.name.replace('_', ' ') }
        binding.spinnerHexOrientation.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, orientOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerHexOrientation.setSelection(SettingsManager.getHexOrientation(this).ordinal)
        binding.spinnerHexOrientation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.setHexOrientation(this@SettingsActivity, SettingsManager.HexOrientation.values()[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Search Position
        val searchOptions = SettingsManager.SearchPosition.values().map { it.name }
        binding.spinnerSearchPosition.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, searchOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSearchPosition.setSelection(SettingsManager.getSearchPosition(this).ordinal)
        binding.spinnerSearchPosition.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.setSearchPosition(this@SettingsActivity, SettingsManager.SearchPosition.values()[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupToggles() {
        binding.switchShowOutline.isChecked = SettingsManager.getShowOutline(this)
        binding.switchShowOutline.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setShowOutline(this, isChecked)
        }
        
        binding.switchShowLabels.isChecked = SettingsManager.getShowLabels(this)
        binding.switchShowLabels.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setShowLabels(this, isChecked)
        }
        
        binding.switchShowNotificationGlow.isChecked = SettingsManager.getShowNotificationGlow(this)
        binding.switchShowNotificationGlow.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setShowNotificationGlow(this, isChecked)
        }
        
        binding.switchSearchWithMic.isChecked = SettingsManager.getSearchWithMic(this)
        binding.switchSearchWithMic.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setSearchWithMic(this, isChecked)
        }
        
        binding.switchDimStatusBar.isChecked = SettingsManager.getDimStatusBar(this)
        binding.switchDimStatusBar.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setDimStatusBar(this, isChecked)
        }
        
        binding.switchDarkTheme.isChecked = SettingsManager.getDarkTheme(this)
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setDarkTheme(this, isChecked)
        }
        
        binding.switchUnifiedBucketColors.isChecked = SettingsManager.getUnifiedBucketColors(this)
        binding.switchUnifiedBucketColors.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setUnifiedBucketColors(this, isChecked)
        }
    }
    
    private fun setupPermissions() {
        binding.btnUsageAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        
        binding.btnNotificationAccess.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        
        binding.btnSetDefaultLauncher.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
        
        binding.btnManageApps.setOnClickListener {
            startActivity(Intent(this, AppVisibilityActivity::class.java))
        }
    }
    
    private fun setupSeekBars() {
        // Hex Radius
        setupSeekBar(
            binding.seekBarHexRadius,
            binding.textHexRadiusValue,
            SettingsManager.getHexRadius(this),
            SettingsManager.MIN_HEX_RADIUS,
            SettingsManager.MAX_HEX_RADIUS,
            "dp"
        ) { SettingsManager.setHexRadius(this, it) }
        
        // Icon Size
        val currentMultiplier = SettingsManager.getIconSizeMultiplier(this)
        binding.seekBarIconSize.max = ((SettingsManager.MAX_ICON_SIZE_MULTIPLIER - SettingsManager.MIN_ICON_SIZE_MULTIPLIER) * 100).toInt()
        binding.seekBarIconSize.progress = ((currentMultiplier - SettingsManager.MIN_ICON_SIZE_MULTIPLIER) * 100).toInt()
        binding.textIconSizeValue.text = "${(currentMultiplier * 100).toInt()}%"
        binding.seekBarIconSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = SettingsManager.MIN_ICON_SIZE_MULTIPLIER + progress / 100f
                binding.textIconSizeValue.text = "${(value * 100).toInt()}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = SettingsManager.MIN_ICON_SIZE_MULTIPLIER + (seekBar?.progress ?: 0) / 100f
                SettingsManager.setIconSizeMultiplier(this@SettingsActivity, value)
            }
        })
        
        // Icon Padding
        setupSeekBar(
            binding.seekBarIconPadding,
            binding.textIconPaddingValue,
            SettingsManager.getIconPadding(this),
            SettingsManager.MIN_ICON_PADDING,
            SettingsManager.MAX_ICON_PADDING,
            "dp"
        ) { SettingsManager.setIconPadding(this, it) }
        
        // Outline Width
        setupSeekBar(
            binding.seekBarOutlineWidth,
            binding.textOutlineWidthValue,
            SettingsManager.getOutlineWidth(this),
            SettingsManager.MIN_OUTLINE_WIDTH,
            SettingsManager.MAX_OUTLINE_WIDTH,
            "dp"
        ) { SettingsManager.setOutlineWidth(this, it) }
        
        // Corner Radius
        setupSeekBar(
            binding.seekBarCornerRadius,
            binding.textCornerRadiusValue,
            SettingsManager.getCornerRadius(this),
            SettingsManager.MIN_CORNER_RADIUS,
            SettingsManager.MAX_CORNER_RADIUS,
            "dp"
        ) { SettingsManager.setCornerRadius(this, it) }
    }
    
    private fun setupSeekBar(
        seekBar: SeekBar,
        textView: android.widget.TextView,
        current: Float,
        min: Float,
        max: Float,
        suffix: String,
        onSave: (Float) -> Unit
    ) {
        seekBar.max = ((max - min) * 10).toInt()
        seekBar.progress = ((current - min) * 10).toInt()
        textView.text = String.format("%.1f%s", current, suffix)
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = min + progress / 10f
                textView.text = String.format("%.1f%s", value, suffix)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = min + (seekBar?.progress ?: 0) / 10f
                onSave(value)
            }
        })
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
