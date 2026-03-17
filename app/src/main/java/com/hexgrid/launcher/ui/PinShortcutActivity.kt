package com.hexgrid.launcher.ui

import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Handles pin shortcut requests (e.g. "Add to Home Screen" from Chrome PWA).
 * Receives ACTION_CONFIRM_PIN_SHORTCUT intent, shows confirmation dialog.
 *
 * Only receives intents when HexGrid is the default launcher.
 */
class PinShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val request = extractPinRequest()

        if (request == null || !request.isValid) {
            finish()
            return
        }

        val shortcutInfo = request.shortcutInfo
        val label = shortcutInfo?.shortLabel?.toString()
            ?: shortcutInfo?.longLabel?.toString()
            ?: "Shortcut"

        val icon: Drawable? = shortcutInfo?.let { info ->
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            runCatching {
                launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
            }.getOrNull()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add to HexGrid?")
            .setMessage(label)
            .apply { if (icon != null) setIcon(icon) }
            .setPositiveButton("Add") { _, _ ->
                request.accept()
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun extractPinRequest(): LauncherApps.PinItemRequest? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(
                LauncherApps.EXTRA_PIN_ITEM_REQUEST,
                LauncherApps.PinItemRequest::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST)
        }
    }
}
