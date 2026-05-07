package com.hexgrid.launcher.ui

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hexgrid.launcher.MainActivity
import com.hexgrid.launcher.databinding.ActivitySettingsHubBinding
import com.hexgrid.launcher.util.SettingsExporter

class SettingsHubActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENTER_EDIT_MODE = MainActivity.EXTRA_ENTER_EDIT_MODE
        private const val REQUEST_CODE_DEFAULT_LAUNCHER = 1001
    }

    private lateinit var binding: ActivitySettingsHubBinding

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportSettingsToUri(it) }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importSettingsFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }

        setupTiles()
    }

    override fun onResume() {
        super.onResume()
        updateBadges()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupTiles() {
        binding.tileHero.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_ENTER_EDIT_MODE, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        binding.tileWidgets.setOnClickListener {
            startActivity(Intent(this, WidgetManagementActivity::class.java))
        }

        binding.tileManageApps.setOnClickListener {
            startActivity(Intent(this, AppVisibilityActivity::class.java))
        }

        binding.tilePermissions.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        binding.tileDefaultLauncher.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CODE_DEFAULT_LAUNCHER)
                }
            } else {
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }

        binding.tileExport.setOnClickListener {
            exportLauncher.launch(SettingsExporter.getSuggestedFilename())
        }

        binding.tileImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    private fun updateBadges() {
        val notifGranted = isNotificationListenerEnabled()
        binding.badgePermissions.visibility = if (notifGranted) View.GONE else View.VISIBLE
        binding.tilePermissions.contentDescription =
            if (notifGranted) "Permissions" else "Permissions — action needed"

        val isDefault = isDefaultLauncher()
        binding.badgeDefaultLauncher.visibility = if (isDefault) View.GONE else View.VISIBLE
        binding.tileDefaultLauncher.contentDescription =
            if (isDefault) "Default Launcher" else "Default Launcher — action needed"
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, "com.hexgrid.launcher.service.NotificationListener")
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun exportSettingsToUri(uri: Uri) {
        try {
            val json = SettingsExporter.exportToJson(this)
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(this, "Settings exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettingsFromUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            SettingsExporter.importFromJson(this, json).fold(
                onSuccess = { count ->
                    Toast.makeText(this, "Imported $count settings", Toast.LENGTH_SHORT).show()
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
}
