package com.hexy.launcher.ui

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.hexy.launcher.R
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
        
        setupSeekBars()
    }
    
    private fun setupSeekBars() {
        // Hex Radius (Spacing)
        val currentRadius = SettingsManager.getHexRadius(this)
        binding.seekBarHexRadius.max = ((SettingsManager.MAX_HEX_RADIUS - SettingsManager.MIN_HEX_RADIUS)).toInt()
        binding.seekBarHexRadius.progress = ((currentRadius - SettingsManager.MIN_HEX_RADIUS)).toInt()
        binding.textHexRadiusValue.text = "${currentRadius.toInt()}dp"
        
        binding.seekBarHexRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = SettingsManager.MIN_HEX_RADIUS + progress
                binding.textHexRadiusValue.text = "${value.toInt()}dp"
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = SettingsManager.MIN_HEX_RADIUS + (seekBar?.progress ?: 0)
                SettingsManager.setHexRadius(this@SettingsActivity, value)
            }
        })
        
        // Icon Size Multiplier
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
        val currentPadding = SettingsManager.getIconPadding(this)
        binding.seekBarIconPadding.max = ((SettingsManager.MAX_ICON_PADDING - SettingsManager.MIN_ICON_PADDING)).toInt()
        binding.seekBarIconPadding.progress = ((currentPadding - SettingsManager.MIN_ICON_PADDING)).toInt()
        binding.textIconPaddingValue.text = "${currentPadding.toInt()}dp"
        
        binding.seekBarIconPadding.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = SettingsManager.MIN_ICON_PADDING + progress
                binding.textIconPaddingValue.text = "${value.toInt()}dp"
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = SettingsManager.MIN_ICON_PADDING + (seekBar?.progress ?: 0)
                SettingsManager.setIconPadding(this@SettingsActivity, value)
            }
        })
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
