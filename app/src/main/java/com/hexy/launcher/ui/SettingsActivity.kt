package com.hexy.launcher.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hexy.launcher.databinding.ActivitySettingsBinding
import com.hexy.launcher.util.SettingsExporter
import com.hexy.launcher.util.SettingsManager

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val REQUEST_CODE_SET_DEFAULT_LAUNCHER = 1001
    }
    
    private lateinit var binding: ActivitySettingsBinding
    
    // File picker for export
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportSettingsToUri(it) }
    }
    
    // File picker for import
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importSettingsFromUri(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Launcher Settings"
        
        setupSpinners()
        setupSeekBars()
        setupTransparencySliders()
        setupToggles()
        setupPermissions()
        setupBackupRestore()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ uses RoleManager for proper launcher selection
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_LAUNCHER)
                }
            } else {
                // Older Android: Open default apps settings
                try {
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    // Ultimate fallback to general settings
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    startActivity(intent)
                }
            }
        }
        
        binding.btnManageApps.setOnClickListener {
            startActivity(Intent(this, AppVisibilityActivity::class.java))
        }
    }
    
    private fun setupTransparencySliders() {
        // Tile Transparency (0-100)
        val currentTile = SettingsManager.getTileTransparency(this)
        binding.seekBarTileTransparency.progress = currentTile
        binding.textTileTransparencyValue.text = "$currentTile%"
        binding.seekBarTileTransparency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textTileTransparencyValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                SettingsManager.setTileTransparency(this@SettingsActivity, seekBar?.progress ?: 50)
            }
        })
        
        // Dock Transparency (0-100)
        val currentDock = SettingsManager.getDockTransparency(this)
        binding.seekBarDockTransparency.progress = currentDock
        binding.textDockTransparencyValue.text = "$currentDock%"
        binding.seekBarDockTransparency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textDockTransparencyValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                SettingsManager.setDockTransparency(this@SettingsActivity, seekBar?.progress ?: 90)
            }
        })
    }
    
    private fun setupBackupRestore() {
        binding.btnExportSettings.setOnClickListener {
            val filename = SettingsExporter.getSuggestedFilename()
            exportLauncher.launch(filename)
        }
        
        binding.btnImportSettings.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }
    
    private fun exportSettingsToUri(uri: Uri) {
        try {
            val json = SettingsExporter.exportToJson(this)
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            Toast.makeText(this, "Settings exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun importSettingsFromUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            val result = SettingsExporter.importFromJson(this, json)
            
            result.fold(
                onSuccess = { count ->
                    Toast.makeText(this, "Imported $count settings", Toast.LENGTH_SHORT).show()
                    // Refresh UI to reflect imported settings
                    recreate()
                },
                onFailure = { error ->
                    Toast.makeText(this, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
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
